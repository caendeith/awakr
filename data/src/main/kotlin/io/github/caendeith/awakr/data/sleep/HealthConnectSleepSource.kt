package io.github.caendeith.awakr.data.sleep

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/**
 * Read-only sleep source backed by Health Connect.
 *
 * Health Connect ships as a Google app on most phones but as an APEX plus a controller app on AOSP
 * builds (GrapheneOS), where the default provider package is not there. [PROVIDER_PACKAGES] is
 * therefore probed in order and the first one that reports a bindable SDK service wins; when none
 * does, [availability] stays [SleepAvailability.UNAVAILABLE] and the UI explains what to install.
 */
class HealthConnectSleepSource(context: Context) : SleepSource {

    private val appContext: Context = context.applicationContext

    override fun availability(): SleepAvailability {
        val statuses = PROVIDER_PACKAGES.map { packageName ->
            runCatching { HealthConnectClient.getSdkStatus(appContext, packageName) }.getOrNull()
        }
        return when {
            statuses.any { it == HealthConnectClient.SDK_AVAILABLE } -> SleepAvailability.AVAILABLE
            statuses.any { it == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED } ->
                SleepAvailability.UPDATE_REQUIRED
            else -> SleepAvailability.UNAVAILABLE
        }
    }

    override fun requiredPermissions(): Set<String> =
        setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

    override fun permissions(): Set<String> = linkedSetOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
    )

    override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    override suspend fun grantedPermissions(): Set<String> =
        clientOrNull()?.permissionController?.getGrantedPermissions() ?: emptySet()

    override fun manageDataIntent(): Intent? =
        runCatching { HealthConnectClient.getHealthConnectManageDataIntent(appContext) }.getOrNull()

    override suspend fun readSessions(start: Long, end: Long): List<SleepSession> {
        val client = clientOrNull() ?: return emptyList()
        val granted = client.permissionController.getGrantedPermissions()
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) !in granted) return emptyList()

        val filter = TimeRangeFilter.between(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end))
        val rawSessions = readAll<SleepSessionRecord>(client, filter)
        if (rawSessions.isEmpty()) return emptyList()

        val heartRates = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            readAll<HeartRateRecord>(client, filter).flatMap { record ->
                record.samples.map { sample ->
                    HeartRateSample(sample.time.toEpochMilli(), sample.beatsPerMinute.toInt())
                }
            }
        } else {
            emptyList()
        }

        val spo2 = if (HealthPermission.getReadPermission(OxygenSaturationRecord::class) in granted) {
            readAll<OxygenSaturationRecord>(client, filter).map { record ->
                Spo2Sample(record.time.toEpochMilli(), record.percentage.value)
            }
        } else {
            emptyList()
        }

        return SleepSessionMapper.mapAll(rawSessions.map { it.toRaw() }, heartRates, spo2)
    }

    private fun resolveProviderPackage(): String? = PROVIDER_PACKAGES.firstOrNull { packageName ->
        runCatching {
            HealthConnectClient.getSdkStatus(appContext, packageName) == HealthConnectClient.SDK_AVAILABLE
        }.getOrDefault(false)
    }

    private fun clientOrNull(): HealthConnectClient? {
        val packageName = resolveProviderPackage() ?: return null
        return runCatching { HealthConnectClient.getOrCreate(appContext, packageName) }.getOrNull()
    }

    private suspend inline fun <reified T : Record> readAll(
        client: HealthConnectClient,
        filter: TimeRangeFilter,
    ): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(
                    recordType = T::class,
                    timeRangeFilter = filter,
                    ascendingOrder = true,
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                ),
            )
            records += page.records
            pageToken = page.pageToken
        } while (pageToken != null)
        return records
    }

    private fun SleepSessionRecord.toRaw(): RawSleepSession = RawSleepSession(
        start = startTime.toEpochMilli(),
        end = endTime.toEpochMilli(),
        stages = stages.map { stage ->
            SleepStage(
                type = stage.stage.toSleepStageType(),
                start = stage.startTime.toEpochMilli(),
                end = stage.endTime.toEpochMilli(),
            )
        },
    )

    private fun Int.toSleepStageType(): SleepStageType = when (this) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        -> SleepStageType.AWAKE

        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
        else -> SleepStageType.UNKNOWN
    }

    private companion object {
        const val PAGE_SIZE = 1_000

        /** Google's Health Connect first, then the AOSP/GrapheneOS controller package. */
        val PROVIDER_PACKAGES = listOf(
            "com.google.android.apps.healthdata",
            "com.android.healthconnect.controller",
        )
    }
}
