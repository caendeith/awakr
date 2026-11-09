package io.github.caendeith.awakr.data.mapper

import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.alarm.AlarmLog
import io.github.caendeith.awakr.core.alarm.AlarmOutcome
import io.github.caendeith.awakr.data.db.entity.AlarmEntity
import io.github.caendeith.awakr.data.db.entity.AlarmLogEntity

fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    daysMask = daysMask,
    enabled = enabled,
    label = label,
    soundUri = soundUri,
    soundPackId = soundPackId,
    soundId = soundId,
    backgroundId = backgroundId,
    rampSeconds = rampSeconds,
    volumeStart = volumeStart,
    vibrate = vibrate,
    snoozeEnabled = snoozeEnabled,
    snoozeMinutes = snoozeMinutes,
    maxSnoozes = maxSnoozes,
    missionPluginId = missionPluginId,
    missionConfig = missionConfig,
    missionChain = missionChain,
    sortOrder = sortOrder,
    skipNextAt = skipNextAt,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    daysMask = daysMask,
    enabled = enabled,
    label = label,
    soundUri = soundUri,
    soundPackId = soundPackId,
    soundId = soundId,
    backgroundId = backgroundId,
    rampSeconds = rampSeconds,
    volumeStart = volumeStart,
    vibrate = vibrate,
    snoozeEnabled = snoozeEnabled,
    snoozeMinutes = snoozeMinutes,
    maxSnoozes = maxSnoozes,
    missionPluginId = missionPluginId,
    missionConfig = missionConfig,
    missionChain = missionChain,
    sortOrder = sortOrder,
    skipNextAt = skipNextAt,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AlarmLogEntity.toDomain(): AlarmLog = AlarmLog(
    id = id,
    alarmId = alarmId,
    scheduledAt = scheduledAt,
    firedAt = firedAt,
    dismissedAt = dismissedAt,
    missionPluginId = missionPluginId,
    missionPassed = missionPassed,
    snoozeCount = snoozeCount,
    outcome = AlarmOutcome.entries.firstOrNull { it.name == outcome } ?: AlarmOutcome.ERROR,
)

fun AlarmLog.toEntity(): AlarmLogEntity = AlarmLogEntity(
    id = id,
    alarmId = alarmId,
    scheduledAt = scheduledAt,
    firedAt = firedAt,
    dismissedAt = dismissedAt,
    missionPluginId = missionPluginId,
    missionPassed = missionPassed,
    snoozeCount = snoozeCount,
    outcome = outcome.name,
)
