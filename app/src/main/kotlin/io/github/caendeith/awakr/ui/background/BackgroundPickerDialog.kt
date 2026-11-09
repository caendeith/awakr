package io.github.caendeith.awakr.ui.background

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.caendeith.awakr.R
import kotlinx.coroutines.launch
import java.io.File

/**
 * Full screen background picker.
 *
 * Built-ins are drawn live with the same code the ring screen uses, so the tile is exactly what the
 * alarm will show. Photos are imported into the app's private folder and can be deleted here.
 *
 * @param currentId the alarm's current background, blank for "follow the app setting"
 * @param onPick receives the new id, or "" for the default background
 */
@Composable
fun BackgroundPickerDialog(
    currentId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photos by remember { mutableStateOf<List<File>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var importFailed by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh) { photos = BackgroundStore.photos(context) }

    val onImported: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            scope.launch {
                val id = BackgroundStore.import(context, uri)
                if (id == null) {
                    importFailed = true
                } else {
                    refresh++
                    onPick(id)
                }
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onImported)
    // Some vendors ship the photo picker broken; the plain document picker always works.
    val fallbackPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), onImported)

    fun openPicker() {
        val launched = runCatching {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }.isSuccess
        if (!launched) fallbackPicker.launch("image/*")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bg_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.bg_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TabChip(
                        label = stringResource(R.string.bg_tab_builtin),
                        selected = tab == 0,
                        onClick = { tab = 0 },
                    )
                    TabChip(
                        label = stringResource(R.string.bg_tab_photos),
                        selected = tab == 1,
                        onClick = { tab = 1 },
                    )
                }

                Box(Modifier.weight(1f)) {
                    if (tab == 0) {
                        BuiltinGrid(currentId = currentId, onPick = onPick)
                    } else {
                        PhotoGrid(
                            photos = photos,
                            currentId = currentId,
                            onAdd = { openPicker() },
                            onPick = onPick,
                            onDelete = { pendingDelete = it },
                        )
                    }
                }

                if (importFailed) {
                    Text(
                        text = stringResource(R.string.bg_import_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }

                DefaultRow(
                    selected = currentId.isNullOrBlank(),
                    onClick = { onPick("") },
                )
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.bg_delete_title)) },
            text = { Text(stringResource(R.string.bg_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        BackgroundStore.delete(context, toDelete)
                        refresh++
                        // The alarm must not keep pointing at a file that just disappeared.
                        if (toDelete == currentId) onPick("")
                    },
                ) {
                    Text(stringResource(R.string.bg_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun BuiltinGrid(currentId: String?, onPick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackgroundFamily.entries.forEach { family ->
            item(key = "family-" + family.name, span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(familyLabel(family)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(BackgroundCatalog.of(family), key = { it.key }) { background ->
                val id = BackgroundCatalog.idOf(background)
                BackgroundTile(
                    name = stringResource(background.nameRes),
                    selected = id == currentId,
                    onClick = { onPick(id) },
                ) {
                    ProceduralBackground(background.key, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<File>,
    currentId: String?,
    onAdd: () -> Unit,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "add-photo", span = { GridItemSpan(maxLineSpan) }) {
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.bg_add_photo))
            }
        }
        if (photos.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.bg_photos_empty),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.bg_photos_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(photos, key = { it.absolutePath }) { file ->
                val id = BackgroundFiles.idOf(file)
                BackgroundTile(
                    name = stringResource(R.string.bg_photo_name, BackgroundFiles.timeLabel(file)),
                    selected = id == currentId,
                    onClick = { onPick(id) },
                    onDelete = { onDelete(id) },
                ) {
                    PhotoThumb(file.absolutePath, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun BackgroundTile(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    preview: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .aspectRatio(0.82f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier,
            ),
    ) {
        preview()
        // Name plate: a short gradient scrim keeps the label readable over a bright photo.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    ),
                ),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.bg_selected),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.bg_delete_photo),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun PhotoThumb(path: String, modifier: Modifier = Modifier) {
    val bitmap by androidx.compose.runtime.produceState<ImageBitmap?>(initialValue = null, path) {
        value = BackgroundStore.load(path, THUMB_EDGE)
    }
    val image = bitmap
    if (image != null) {
        androidx.compose.foundation.Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun DefaultRow(selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.bg_default_row),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.bg_selected),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Label of a background id for the editor row; blank and unresolvable ids read as the default. */
@Composable
fun backgroundLabel(id: String?): String {
    if (id.isNullOrBlank()) return stringResource(R.string.bg_value_default)
    return when (val ref = BackgroundIds.parse(id)) {
        is BackgroundRef.Builtin -> BackgroundCatalog.byKey(ref.key)
            ?.let { stringResource(it.nameRes) }
            ?: stringResource(R.string.bg_value_default)

        is BackgroundRef.Local -> {
            val file = File(ref.path)
            if (file.isFile) {
                stringResource(R.string.bg_photo_name, BackgroundFiles.timeLabel(file))
            } else {
                stringResource(R.string.bg_value_default)
            }
        }

        null -> stringResource(R.string.bg_value_default)
    }
}

private fun familyLabel(family: BackgroundFamily): Int = when (family) {
    BackgroundFamily.GRADIENTS -> R.string.bg_family_gradients
    BackgroundFamily.SCENES -> R.string.bg_family_scenes
}

private const val THUMB_EDGE = 400
