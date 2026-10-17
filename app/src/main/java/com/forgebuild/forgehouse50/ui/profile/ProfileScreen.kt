package com.forgebuild.forgehouse50.ui.profile

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.forgebuild.forgehouse50.ui.ExpressiveLinearProgress
import com.forgebuild.forgehouse50.update.UpdateManager
import com.forgebuild.forgehouse50.update.UpdateUiState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.AppCache
import com.forgebuild.forgehouse50.data.MeResponse
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.local.DownloadedTranslation
import com.forgebuild.forgehouse50.ui.AvatarAssets
import com.forgebuild.forgehouse50.ui.UserAvatar
import com.forgebuild.forgehouse50.ui.formatBytes
import com.forgebuild.forgehouse50.ui.formatDurationShort
import com.forgebuild.forgehouse50.work.KeepAliveService
import com.forgebuild.forgehouse50.work.ReminderWorker
import kotlinx.coroutines.launch

private val AVATAR_IDS = AvatarAssets.IDS

/**
 * Profile: stats, badges, profile photo with device cropping, illustration avatar picker,
 * downloaded-translation manager, sign out.
 * Cache-first: renders instantly using AppCache before first composition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repo: Repository,
    onSignedOut: () -> Unit,
    onManageTranslations: () -> Unit,
    onOpenChangelog: () -> Unit = {},
) {
    val context = LocalContext.current
    var showUpdateChoiceDialog by remember { mutableStateOf(false) }
    var availableUpdateForChoice by remember { mutableStateOf<com.forgebuild.forgehouse50.update.UpdateInfo?>(null) }
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf(AppCache.getMe(context)) }
    var pickingAvatar by remember { mutableStateOf(false) }
    var showPhotoOptionsSheet by remember { mutableStateOf(false) }
    var selectedBitmapForCrop by remember { mutableStateOf<Bitmap?>(null) }
    var isUploadingPicture by remember { mutableStateOf(false) }
    var uploadErrorMessage by remember { mutableStateOf<String?>(null) }

    var translations by remember { mutableStateOf<List<DownloadedTranslation>>(emptyList()) }
    var totalBytes by remember { mutableStateOf(0L) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bmp = ProfilePictureProcessor.decodeBitmap(context, uri)
            if (bmp != null) {
                selectedBitmapForCrop = bmp
            } else {
                uploadErrorMessage = "Could not load selected photo."
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            selectedBitmapForCrop = bitmap
        }
    }

    suspend fun refreshTranslations() {
        val list = repo.downloadedTranslations()
        translations = list
        totalBytes = repo.translationsTotalSizeBytes()
    }

    LaunchedEffect(Unit) {
        refreshTranslations()
        runCatching { repo.api.me() }.onSuccess {
            me = it
            AppCache.saveMe(context, it)
            repo.session.userId = it.id
            repo.session.userName = it.name
            repo.session.userRole = it.role
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))

        // Avatar + Name header Card
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tappable avatar with edit badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clickable { showPhotoOptionsSheet = true },
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    UserAvatar(
                        avatarId = me?.avatar_id,
                        userId = me?.id,
                        pictureVersion = me?.picture_version,
                        sessionToken = repo.session.sessionToken,
                        size = 72.dp,
                        contentDescription = "Profile picture",
                        modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit photo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (listOfNotNull(me?.name, me?.surname?.takeIf { it.isNotBlank() }).joinToString(" "))
                            .ifBlank { repo.session.userName ?: "ForgeHouse Member" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        me?.email ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap photo to change",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showPhotoOptionsSheet = true },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // User stats card
        val s = me?.stats
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "${s?.points ?: 0} points · ${s?.days_completed ?: 0} days · ${s?.chapters ?: 0} chapters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Streak ${s?.streak_current ?: 0} (best ${s?.streak_longest ?: 0}) · " +
                        formatDurationShort(s?.reading_seconds ?: 0) + " read · ${s?.notes_total ?: 0} notes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val badges = me?.badges ?: emptyList()
        if (badges.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            badges.forEach { b ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(b.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(b.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }



        Spacer(Modifier.height(16.dp))

        // Action row: In-app update status
        val updateState by UpdateManager.uiState.collectAsState()

        Surface(
            onClick = {
                when (val s = updateState) {
                    is UpdateUiState.UpToDate -> {
                        UpdateManager.check(context, repo.session, force = true)
                    }
                    is UpdateUiState.Available -> {
                        availableUpdateForChoice = s.update
                        showUpdateChoiceDialog = true
                    }
                    is UpdateUiState.Downloading -> {
                        if (s.isHidden) {
                            UpdateManager.startDownload(context, repo.session, s.update, isHidden = false)
                        }
                    }
                    is UpdateUiState.ReadyToInstall, is UpdateUiState.PermissionRequired -> {
                        UpdateManager.triggerInstall(context)
                    }
                    is UpdateUiState.Error -> {
                        if (s.update != null) {
                            UpdateManager.startDownload(context, repo.session, s.update, isHidden = false)
                        } else {
                            UpdateManager.check(context, repo.session, force = true)
                        }
                    }
                }
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (icon, iconContainerColor, iconContentColor) = when (updateState) {
                        is UpdateUiState.UpToDate -> Triple(
                            Icons.Filled.CheckCircle,
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        is UpdateUiState.Available -> Triple(
                            Icons.Filled.SystemUpdate,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        is UpdateUiState.Downloading -> Triple(
                            Icons.Filled.Downloading,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        is UpdateUiState.ReadyToInstall, is UpdateUiState.PermissionRequired -> Triple(
                            Icons.Filled.DownloadDone,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        is UpdateUiState.Error -> Triple(
                            Icons.Filled.SystemUpdate,
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = iconContainerColor,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = iconContentColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    val labelText = when (val s = updateState) {
                        is UpdateUiState.UpToDate -> "You are up to date"
                        is UpdateUiState.Available -> "Update available, tap to download"
                        is UpdateUiState.Downloading -> "Downloading ${(s.progress * 100).toInt()}%"
                        is UpdateUiState.ReadyToInstall, is UpdateUiState.PermissionRequired -> "Update ready to install, tap to install"
                        is UpdateUiState.Error -> "Update failed — tap to retry"
                    }
                    Text(
                        labelText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (updateState is UpdateUiState.Downloading) {
                    val progressVal = (updateState as UpdateUiState.Downloading).progress
                    Spacer(Modifier.height(8.dp))
                    ExpressiveLinearProgress(
                        progress = { progressVal },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Action row: Manage translations
        Surface(
            onClick = onManageTranslations,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.LibraryBooks,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "Manage translations",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Action row: What's new
        Surface(
            onClick = onOpenChangelog,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.NewReleases,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "What's new",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Action row: Sign out
        Surface(
            onClick = {
                scope.launch {
                    runCatching { repo.api.logout() }
                    ReminderWorker.cancel(context)
                    KeepAliveService.stop(context)
                    repo.session.clear()
                    AppCache.clear(context)
                    try {
                        coil.Coil.imageLoader(context).diskCache?.clear()
                        coil.Coil.imageLoader(context).memoryCache?.clear()
                    } catch (_: Throwable) {}
                    onSignedOut()
                }
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "Sign out",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // Photo options bottom sheet (Item 7b.1)
    if (showPhotoOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoOptionsSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Profile Photo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // 1. Choose from gallery
                Surface(
                    onClick = {
                        showPhotoOptionsSheet = false
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("Choose from gallery", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }

                // 2. Take photo
                Surface(
                    onClick = {
                        showPhotoOptionsSheet = false
                        takePicture.launch(null)
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("Take photo", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }

                // 3. Use an illustration avatar
                Surface(
                    onClick = {
                        showPhotoOptionsSheet = false
                        pickingAvatar = true
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Face, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("Use an illustration avatar", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }

                // 4. Remove my picture (only when a picture is set)
                if (me?.picture_version != null) {
                    Surface(
                        onClick = {
                            showPhotoOptionsSheet = false
                            scope.launch {
                                runCatching { repo.api.removeProfilePicture() }.onSuccess {
                                    val updated = me?.copy(picture_version = null)
                                    me = updated
                                    if (updated != null) AppCache.saveMe(context, updated)
                                }.onFailure { err ->
                                    uploadErrorMessage = "Could not remove picture: ${err.message}"
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(16.dp))
                            Text("Remove my picture", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Square crop dialog (Item 7b.2)
    selectedBitmapForCrop?.let { bmp ->
        var zoomFactor by remember { mutableFloatStateOf(1f) }
        val processed = remember(bmp, zoomFactor) {
            ProfilePictureProcessor.processAndCompress(bmp, scaleFactor = zoomFactor)
        }

        AlertDialog(
            onDismissRequest = {
                if (!isUploadingPicture) selectedBitmapForCrop = null
            },
            title = { Text("Adjust photo") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = processed.previewBitmap.asImageBitmap(),
                            contentDescription = "Photo preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Zoom",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = zoomFactor,
                        onValueChange = { zoomFactor = it },
                        valueRange = 1f..3f,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your picture is visible to other participants.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    if (isUploadingPicture) {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isUploadingPicture,
                    onClick = {
                        isUploadingPicture = true
                        scope.launch {
                            val res = runCatching {
                                repo.api.uploadProfilePicture(processed.bytes, processed.contentType)
                            }
                            isUploadingPicture = false
                            res.onSuccess { okRes ->
                                val updated = me?.copy(picture_version = okRes.points_awarded.takeIf { it > 0 } ?: (me?.picture_version ?: 0) + 1)
                                me = updated
                                if (updated != null) AppCache.saveMe(context, updated)
                                selectedBitmapForCrop = null
                            }.onFailure { err ->
                                uploadErrorMessage = err.message ?: "Upload failed. Please check connection and retry."
                            }
                        }
                    },
                ) {
                    Text("Save & Upload")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isUploadingPicture,
                    onClick = { selectedBitmapForCrop = null },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // Error alert dialog
    uploadErrorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { uploadErrorMessage = null },
            title = { Text("Photo upload") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { uploadErrorMessage = null }) {
                    Text("OK")
                }
            },
        )
    }

    // Illustration avatar picker (existing)
    if (pickingAvatar) {
        AlertDialog(
            onDismissRequest = { pickingAvatar = false },
            title = { Text("Choose your avatar") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(AVATAR_IDS) { id ->
                        Image(
                            painter = painterResource(AvatarAssets.resFor(id)),
                            contentDescription = id,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    scope.launch {
                                        runCatching { repo.api.setAvatar(id) }.onSuccess {
                                            val updated = me?.copy(avatar_id = id)
                                            me = updated
                                            if (updated != null) AppCache.saveMe(context, updated)
                                        }
                                        pickingAvatar = false
                                    }
                                },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingAvatar = false }) { Text("Cancel") } },
        )
    }

    if (showUpdateChoiceDialog && availableUpdateForChoice != null) {
        val upd = availableUpdateForChoice!!
        AlertDialog(
            onDismissRequest = { showUpdateChoiceDialog = false },
            title = { Text("Update available") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Version ${upd.versionName} is available. Choose how you would like to get this update:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showUpdateChoiceDialog = false
                            repo.session.preferredUpdateMode = "in_app"
                            UpdateManager.startDownload(context, repo.session, upd, isHidden = false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Download in the app")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showUpdateChoiceDialog = false
                            repo.session.preferredUpdateMode = "browser"
                            com.forgebuild.forgehouse50.update.UpdateChecker.openInBrowser(context, upd.apkUrl)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open download link in browser")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showUpdateChoiceDialog = false }) {
                    Text("Later")
                }
            },
        )
    }
}
