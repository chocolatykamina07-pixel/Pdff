package com.example.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.FilterType
import com.example.processing.ImageProcessor
import com.example.ui.theme.PillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterEnhanceScreen(
    viewModel: DocumentEditorViewModel,
    documentId: Long,
    pageId: Long,
    onDone: () -> Unit
) {
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()

    var baseCroppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var filterThumbs by remember { mutableStateOf<Map<FilterType, Bitmap>>(emptyMap()) }

    var selectedFilter by remember { mutableStateOf(FilterType.AUTO_ENHANCE) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var sharpness by remember { mutableFloatStateOf(0.2f) }
    var removeShadows by remember { mutableStateOf(false) }
    var removeBlemishes by remember { mutableStateOf(false) }
    var applyToAll by remember { mutableStateOf(false) }
    var holdingCompare by remember { mutableStateOf(false) }

    LaunchedEffect(pageId) {
        viewModel.loadDocument(documentId)
        val page = viewModel.getPage(pageId)
        if (page != null) {
            selectedFilter = runCatching { FilterType.valueOf(page.filter) }.getOrDefault(FilterType.AUTO_ENHANCE)
            brightness = page.brightness
            contrast = page.contrast
            sharpness = page.sharpness
            removeShadows = page.removeShadows
            removeBlemishes = page.removeBlemishes

            withContext(Dispatchers.IO) {
                val raw = ImageProcessor.decodeSampledBitmap(page.originalImagePath, 1000)
                if (raw != null) {
                    val quad = viewModel.decodeCropQuad(page.cropPointsJson)
                    val cropped = ImageProcessor.applyPerspectiveCrop(raw, quad, page.rotation, page.flipHorizontal)
                    raw.recycle()
                    baseCroppedBitmap = cropped

                    // Generate live mini thumbnails for all 6 filters
                    val thumbBase = Bitmap.createScaledBitmap(cropped, 120, (120f * cropped.height / cropped.width).toInt().coerceAtLeast(80), true)
                    val thumbs = FilterType.entries.associateWith { f ->
                        ImageProcessor.applyFilterAndAdjustments(thumbBase, f)
                    }
                    thumbBase.recycle()
                    filterThumbs = thumbs
                }
            }
        }
    }

    LaunchedEffect(baseCroppedBitmap, selectedFilter, brightness, contrast, sharpness, removeShadows, removeBlemishes) {
        val base = baseCroppedBitmap ?: return@LaunchedEffect
        delay(80)
        previewBitmap = withContext(Dispatchers.IO) {
            ImageProcessor.applyFilterAndAdjustments(
                base,
                filter = selectedFilter,
                brightness = brightness,
                contrast = contrast,
                sharpness = sharpness,
                removeShadows = removeShadows,
                removeBlemishes = removeBlemishes
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Filters & Enhance") },
                actions = {
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        holdingCompare = true
                                        tryAwaitRelease()
                                        holdingCompare = false
                                    }
                                )
                            }
                            .testTag("filter_compare_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Compare, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (holdingCompare) "Original" else stringResource(R.string.hold_to_compare),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            // Preview Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                val shown = if (holdingCompare) baseCroppedBitmap else (previewBitmap ?: baseCroppedBitmap)
                if (shown != null) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = "Filtered document preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator()
                }
            }

            // Controls Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Live Filter Strip
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(FilterType.entries) { filter ->
                        val selected = selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .width(78.dp)
                                .clickable { selectedFilter = filter }
                                .testTag("filter_chip_${filter.name.lowercase()}")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(6.dp)
                            ) {
                                val thumb = filterThumbs[filter]
                                Box(
                                    modifier = Modifier
                                        .size(width = 62.dp, height = 74.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb.asImageBitmap(),
                                            contentDescription = filter.label,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Toggles: Remove Shadows & Remove Blemishes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = removeShadows,
                        onClick = { removeShadows = !removeShadows },
                        label = { Text(stringResource(R.string.remove_shadows)) }
                    )
                    FilterChip(
                        selected = removeBlemishes,
                        onClick = { removeBlemishes = !removeBlemishes },
                        label = { Text(stringResource(R.string.remove_blemishes)) }
                    )
                }

                // Sliders: Brightness, Contrast, Sharpness
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.brightness), style = MaterialTheme.typography.labelMedium)
                        Text("${brightness.toInt()}", style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        valueRange = -50f..50f
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.contrast), style = MaterialTheme.typography.labelMedium)
                        Text(String.format("%.2fx", contrast), style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = contrast,
                        onValueChange = { contrast = it },
                        valueRange = 0.6f..1.8f
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.sharpness), style = MaterialTheme.typography.labelMedium)
                        Text(String.format("%.0f%%", sharpness * 100f), style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = sharpness,
                        onValueChange = { sharpness = it },
                        valueRange = 0f..1f
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pages.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { applyToAll = !applyToAll }
                        ) {
                            Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                            Text(stringResource(R.string.apply_all_pages), style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            viewModel.saveFilters(
                                pageId = pageId,
                                filterType = selectedFilter,
                                brightness = brightness,
                                contrast = contrast,
                                sharpness = sharpness,
                                removeShadows = removeShadows,
                                removeBlemishes = removeBlemishes,
                                applyToAll = applyToAll,
                                onComplete = onDone
                            )
                        },
                        enabled = !isBusy,
                        shape = PillShape,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("filter_save_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            }
        }
    }
}
