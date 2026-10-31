package io.github.caendeith.awakr.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.ui.component.TimeText
import io.github.caendeith.awakr.core.ui.theme.PillShape
import io.github.caendeith.awakr.data.settings.SwipeAction
import io.github.caendeith.awakr.ui.format.AlarmSchedule
import io.github.caendeith.awakr.ui.format.countdownText
import io.github.caendeith.awakr.ui.format.dateLabel
import io.github.caendeith.awakr.ui.format.formatTime
import io.github.caendeith.awakr.ui.format.rememberNow
import io.github.caendeith.awakr.ui.format.repeatSummary
import io.github.caendeith.awakr.ui.format.schedulesOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SORT_ROW_HEIGHT = 76.dp

/**
 * The alarm list.
 *
 * Three interactions live on one card, so each one has a visible entry point as well:
 * a tap edits, the "more" button opens the full menu, a long press starts selection,
 * and a horizontal swipe runs the action chosen in settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    alarms: List<Alarm>,
    missionLabel: (String) -> String,
    onToggle: (Alarm, Boolean) -> Unit,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    missedLabels: List<String> = emptyList(),
    swipeAction: SwipeAction = SwipeAction.DELETE,
    onDelete: (List<Long>) -> Unit = {},
    onRestore: (List<Long>) -> Unit = {},
    onDuplicate: (Alarm) -> Unit = {},
    onSkipNext: (Alarm) -> Unit = {},
    onClearSkipNext: (Alarm) -> Unit = {},
    onSetEnabledBulk: (List<Long>, Boolean) -> Unit = { _, _ -> },
    onReorder: (List<Long>) -> Unit = {},
) {
    val now = rememberNow()
    val schedules = remember(alarms, now) { schedulesOf(alarms, now) }
    val next = schedules.firstOrNull { it.next != null }

    var selectMode by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var sortMode by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val undoLabel = stringResource(R.string.action_undo)
    val deletedMessage = stringResource(R.string.home_deleted)

    // An alarm that disappears (deleted elsewhere, purged) must not stay selected forever.
    LaunchedEffect(alarms) {
        if (selectMode) {
            val alive = alarms.map { it.id }.toSet()
            selection = selection.intersect(alive)
        }
    }

    fun toggleSelected(id: Long) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun deleteWithUndo(ids: List<Long>) {
        if (ids.isEmpty()) return
        onDelete(ids)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onRestore(ids)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectMode) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.home_selected, selection.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectMode = false
                            selection = emptySet()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            selection = if (selection.size == alarms.size) emptySet() else alarms.map { it.id }.toSet()
                        }) {
                            Text(
                                stringResource(
                                    if (selection.size == alarms.size) R.string.home_select_none
                                    else R.string.home_select_all,
                                ),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        if (alarms.size > 1) {
                            TextButton(onClick = {
                                sortMode = true
                                selectMode = false
                                selection = emptySet()
                            }) {
                                Text(stringResource(R.string.home_reorder))
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
        bottomBar = {
            if (selectMode) {
                SelectionBar(
                    canEdit = selection.size == 1,
                    onEnable = {
                        onSetEnabledBulk(selection.toList(), true)
                        selectMode = false
                        selection = emptySet()
                    },
                    onDisable = {
                        onSetEnabledBulk(selection.toList(), false)
                        selectMode = false
                        selection = emptySet()
                    },
                    onDuplicate = {
                        selection.mapNotNull { id -> alarms.firstOrNull { it.id == id } }
                            .forEach(onDuplicate)
                        selectMode = false
                        selection = emptySet()
                    },
                    onDelete = {
                        val ids = selection.toList()
                        selectMode = false
                        selection = emptySet()
                        deleteWithUndo(ids)
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selectMode && !sortMode) {
                ExtendedFloatingActionButton(
                    onClick = onAddAlarm,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    text = { Text(stringResource(R.string.home_add_alarm)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }
        },
    ) { padding ->
        if (sortMode) {
            SortList(
                alarms = alarms,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onDone = { ordered ->
                    sortMode = false
                    onReorder(ordered)
                },
                onCancel = { sortMode = false },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "hero") {
                    NextAlarmCard(
                        schedule = next,
                        missionLabel = missionLabel,
                        now = now,
                    )
                }
                if (missedLabels.isNotEmpty()) {
                    item(key = "missed") { MissedCard(missedLabels) }
                }
                item(key = "section") {
                    SectionLabel(
                        if (alarms.isEmpty()) stringResource(R.string.home_no_alarms)
                        else stringResource(R.string.home_all_alarms),
                    )
                }
                items(items = schedules, key = { it.alarm.id }) { schedule ->
                    val alarm = schedule.alarm
                    Box(modifier = Modifier.animateItem()) {
                        AlarmCard(
                            schedule = schedule,
                            now = now,
                            selectMode = selectMode,
                            selected = alarm.id in selection,
                            swipeAction = swipeAction,
                            haptics = haptics,
                            onToggle = { onToggle(alarm, it) },
                            onToggleSelection = { toggleSelected(alarm.id) },
                            onEnterSelection = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectMode = true
                                selection = setOf(alarm.id)
                            },
                            onEdit = { onEditAlarm(alarm.id) },
                            onDuplicate = { onDuplicate(alarm) },
                            onSkipNext = { if (alarm.skipNextAt != null) onClearSkipNext(alarm) else onSkipNext(alarm) },
                            onDelete = { deleteWithUndo(listOf(alarm.id)) },
                            missionLabel = missionLabel,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NextAlarmCard(
    schedule: AlarmSchedule?,
    missionLabel: (String) -> String,
    now: java.time.ZonedDateTime,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.home_next_alarm),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val alarm = schedule?.alarm
            if (alarm == null) {
                Text(
                    text = stringResource(R.string.home_no_next),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_active_none_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TimeText(text = formatTime(alarm.hour, alarm.minute), fontSize = 64.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = schedule.next?.let { dateLabel(now, it) }.orEmpty()
                    Pill(text = listOf(label, missionLabel(alarm.missionPluginId))
                        .filter { it.isNotBlank() }
                        .joinToString(" · "))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = countdownText(now, schedule.next),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = PillShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MissedCard(labels: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.home_missed_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            labels.forEach { label ->
                Text(
                    text = stringResource(R.string.home_missed_row, label),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(
    schedule: AlarmSchedule,
    now: java.time.ZonedDateTime,
    selectMode: Boolean,
    selected: Boolean,
    swipeAction: SwipeAction,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onToggle: (Boolean) -> Unit,
    onToggleSelection: () -> Unit,
    onEnterSelection: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onSkipNext: () -> Unit,
    onDelete: () -> Unit,
    missionLabel: (String) -> String,
) {
    val alarm = schedule.alarm
    val content = @Composable {
        AlarmRow(
            schedule = schedule,
            now = now,
            selectMode = selectMode,
            selected = selected,
            onToggle = onToggle,
            onEdit = onEdit,
            onDuplicate = onDuplicate,
            onSkipNext = onSkipNext,
            onDelete = onDelete,
            onToggleSelection = onToggleSelection,
            onEnterSelection = onEnterSelection,
            missionLabel = missionLabel,
        )
    }

    if (selectMode || swipeAction == SwipeAction.NONE) {
        content()
    } else {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    when (swipeAction) {
                        SwipeAction.DELETE -> {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete()
                            // Never settle in the dismissed position: the row disappears from the
                            // list instead. Settling would leave the red backdrop on screen when the
                            // user brings the alarm back with Undo.
                            false
                        }
                        SwipeAction.TOGGLE -> {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle(!alarm.enabled)
                            false
                        }
                        SwipeAction.NONE -> false
                    }
                } else {
                    true
                }
            },
        )
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            if (swipeAction == SwipeAction.DELETE) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = stringResource(
                            if (swipeAction == SwipeAction.DELETE) R.string.home_action_delete
                            else if (alarm.enabled) R.string.home_action_disable else R.string.home_action_enable,
                        ),
                        color = if (swipeAction == SwipeAction.DELETE) MaterialTheme.colorScheme.onError
                        else MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
            enableDismissFromStartToEnd = false,
            content = { content() },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmRow(
    schedule: AlarmSchedule,
    now: java.time.ZonedDateTime,
    selectMode: Boolean,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onSkipNext: () -> Unit,
    onDelete: () -> Unit,
    onToggleSelection: () -> Unit,
    onEnterSelection: () -> Unit,
    missionLabel: (String) -> String,
) {
    val alarm = schedule.alarm
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val container = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val subtitle = listOfNotNull(
        alarm.label.ifBlank { null },
        repeatSummary(alarm.daysMask),
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .combinedClickable(
                onClick = {
                    if (selectMode) onToggleSelection() else onEdit()
                },
                onLongClick = {
                    if (selectMode) {
                        onToggleSelection()
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEnterSelection()
                    }
                },
            )
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TimeText(
                text = formatTime(alarm.hour, alarm.minute),
                fontSize = 32.sp,
                color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val status = when {
                !alarm.enabled -> stringResource(R.string.home_disabled)
                alarm.skipNextAt != null -> stringResource(R.string.home_skip_marked)
                schedule.next == null -> stringResource(R.string.home_no_next)
                else -> listOf(dateLabel(now, schedule.next), countdownText(now, schedule.next))
                    .joinToString(" · ")
            }
            Text(
                text = listOf(status, missionLabel(alarm.missionPluginId)).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = if (alarm.enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (selectMode) {
            SelectionMark(selected = selected)
        } else {
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.home_more),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_edit)) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_duplicate)) },
                        onClick = { menuOpen = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (alarm.skipNextAt != null) R.string.home_menu_skip_cancel
                                    else R.string.home_menu_skip_next,
                                ),
                            )
                        },
                        onClick = { menuOpen = false; onSkipNext() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (alarm.enabled) R.string.home_action_disable
                                    else R.string.home_action_enable,
                                ),
                            )
                        },
                        onClick = { menuOpen = false; onToggle(!alarm.enabled) },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.home_action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SelectionBar(
    canEdit: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onEnable, enabled = canEdit || true) {
                Text(stringResource(R.string.home_action_enable))
            }
            TextButton(onClick = onDisable) {
                Text(stringResource(R.string.home_action_disable))
            }
            TextButton(onClick = onDuplicate) {
                Text(stringResource(R.string.home_action_duplicate))
            }
            TextButton(onClick = onDelete) {
                Text(
                    stringResource(R.string.home_action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SortList(
    alarms: List<Alarm>,
    modifier: Modifier = Modifier,
    onDone: (List<Long>) -> Unit,
    onCancel: () -> Unit,
) {
    var ordered by remember(alarms) { mutableStateOf(alarms) }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { SORT_ROW_HEIGHT.toPx() }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_reorder_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            TextButton(onClick = { onDone(ordered.map { it.id }) }) {
                Text(stringResource(R.string.action_done))
            }
        }
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            ordered.forEach { alarm ->
                val isDragged = dragId == alarm.id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SORT_ROW_HEIGHT)
                        .offset { IntOffset(0, if (isDragged) dragOffset.roundToInt() else 0) }
                        .zIndex(if (isDragged) 1f else 0f)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    dragId = alarm.id
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    dragId = null
                                    dragOffset = 0f
                                    onDone(ordered.map { it.id })
                                },
                                onDragCancel = {
                                    dragId = null
                                    dragOffset = 0f
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                val current = ordered.indexOfFirst { it.id == dragId }
                                if (current < 0) return@detectDragGestures
                                dragOffset += dragAmount.y
                                val steps = (dragOffset / rowHeightPx).roundToInt()
                                if (steps != 0) {
                                    val target = (current + steps).coerceIn(0, ordered.lastIndex)
                                    if (target != current) {
                                        ordered = ordered.movedTo(current, target)
                                        dragOffset -= steps * rowHeightPx
                                    }
                                }
                            }
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (isDragged) MaterialTheme.colorScheme.surfaceContainerHigh
                                else MaterialTheme.colorScheme.surfaceContainer,
                            )
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TimeText(text = formatTime(alarm.hour, alarm.minute), fontSize = 26.sp)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = alarm.label.ifBlank { repeatSummary(alarm.daysMask) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "\u2630",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
