package com.example.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EditorialAccentGreen
import com.example.ui.theme.EditorialBg
import com.example.ui.theme.EditorialOnPrimary
import com.example.ui.theme.EditorialOutline
import com.example.ui.theme.EditorialPrimary
import com.example.ui.theme.EditorialPrimaryContainer
import com.example.ui.theme.EditorialSurface
import com.example.ui.theme.EditorialTextDim
import com.example.ui.theme.EditorialTextPrimary
import com.example.ui.theme.EditorialTextSecondary

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val libraryUriStr by viewModel.libraryUri.collectAsState()
  val downloadWorkers by viewModel.downloadWorkers.collectAsState()
  val readerZoom by viewModel.readerZoom.collectAsState()
  val validateImages by viewModel.validateImages.collectAsState()

  val folderPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
          android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
      } catch (e: Exception) {
        e.printStackTrace()
      }
      viewModel.setLibraryFolder(uri)
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(EditorialBg),
    contentPadding = PaddingValues(bottom = 100.dp)
  ) {
    // Header
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = EditorialPrimary,
            letterSpacing = (-0.5).sp
          )
          Text(
            text = "PREFERENCES & STORAGE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = EditorialTextSecondary,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(top = 2.dp)
          )
        }

        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(EditorialSurface),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            tint = EditorialPrimary,
            modifier = Modifier.size(22.dp)
          )
        }
      }
    }

    // Section 1: Storage Location
    item {
      SettingsSection(title = "LIBRARY STORAGE") {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = EditorialSurface,
          border = androidx.compose.foundation.BorderStroke(1.dp, EditorialOutline),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .padding(16.dp)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(EditorialPrimaryContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Folder,
                  contentDescription = null,
                  tint = EditorialPrimary,
                  modifier = Modifier.size(18.dp)
                )
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Persistent Storage Folder",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = EditorialTextPrimary
                )
                Text(
                  text = viewModel.getDisplayPath(),
                  fontSize = 12.sp,
                  color = EditorialTextDim,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            Button(
              onClick = { folderPickerLauncher.launch(null) },
              colors = ButtonDefaults.buttonColors(
                containerColor = EditorialPrimary,
                contentColor = EditorialOnPrimary
              ),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_change_storage_button")
            ) {
              Text("Change Storage Folder (SAF)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
          }
        }
      }
    }

    // Section 2: Download Workers Concurrency
    item {
      SettingsSection(title = "DOWNLOAD SPEED & CONCURRENCY") {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = EditorialSurface,
          border = androidx.compose.foundation.BorderStroke(1.dp, EditorialOutline),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .padding(16.dp)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(EditorialPrimaryContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Speed,
                  contentDescription = null,
                  tint = EditorialPrimary,
                  modifier = Modifier.size(18.dp)
                )
              }

              Column {
                Text(
                  text = "Concurrent Download Workers",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = EditorialTextPrimary
                )
                Text(
                  text = "Active: $downloadWorkers threads",
                  fontSize = 12.sp,
                  color = EditorialTextSecondary
                )
              }
            }

            // Worker selector chips (4, 8, 12, 16)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf(4, 8, 12, 16).forEach { count ->
                val isSelected = downloadWorkers == count
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) EditorialPrimaryContainer else EditorialBg)
                    .border(
                      1.dp,
                      if (isSelected) EditorialPrimary else EditorialOutline,
                      RoundedCornerShape(10.dp)
                    )
                    .clickable { viewModel.setDownloadWorkers(count) }
                    .padding(vertical = 10.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "$count",
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) EditorialPrimary else EditorialTextDim
                  )
                }
              }
            }

            // Image Validation Toggle
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Validate & Verify Images",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium,
                  color = EditorialTextPrimary
                )
                Text(
                  text = "Redownloads corrupt or 0-byte images",
                  fontSize = 11.sp,
                  color = EditorialTextSecondary
                )
              }

              Switch(
                checked = validateImages,
                onCheckedChange = { viewModel.setValidateImages(it) },
                colors = SwitchDefaults.colors(
                  checkedThumbColor = EditorialOnPrimary,
                  checkedTrackColor = EditorialPrimary,
                  uncheckedThumbColor = EditorialTextSecondary,
                  uncheckedTrackColor = EditorialBg
                )
              )
            }
          }
        }
      }
    }

    // Section 3: Reader Preferences
    item {
      SettingsSection(title = "READER PREFERENCES") {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = EditorialSurface,
          border = androidx.compose.foundation.BorderStroke(1.dp, EditorialOutline),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .padding(16.dp)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(EditorialPrimaryContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.ZoomIn,
                  contentDescription = null,
                  tint = EditorialPrimary,
                  modifier = Modifier.size(18.dp)
                )
              }

              Column {
                Text(
                  text = "Default Reader Zoom",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = EditorialTextPrimary
                )
                Text(
                  text = "Current: ${(readerZoom * 100).toInt()}%",
                  fontSize = 12.sp,
                  color = EditorialTextSecondary
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf(1.0f to "100%", 1.25f to "125%", 1.5f to "150%").forEach { (zoom, label) ->
                val isSelected = (readerZoom - zoom).let { it >= -0.05f && it <= 0.05f }
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) EditorialPrimaryContainer else EditorialBg)
                    .border(
                      1.dp,
                      if (isSelected) EditorialPrimary else EditorialOutline,
                      RoundedCornerShape(10.dp)
                    )
                    .clickable { viewModel.setReaderZoom(zoom) }
                    .padding(vertical = 10.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) EditorialPrimary else EditorialTextDim
                  )
                }
              }
            }
          }
        }
      }
    }

    // Section 4: About & Privacy
    item {
      SettingsSection(title = "ABOUT & PRIVACY") {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = EditorialSurface,
          border = androidx.compose.foundation.BorderStroke(1.dp, EditorialOutline),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .padding(16.dp)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(Color(0xFF143820)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = EditorialAccentGreen,
                  modifier = Modifier.size(18.dp)
                )
              }

              Column {
                Text(
                  text = "Manwa Manager v1.0.0",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = EditorialTextPrimary
                )
                Text(
                  text = "Native Android Edition • Offline-First",
                  fontSize = 12.sp,
                  color = EditorialTextSecondary
                )
              }
            }

            Text(
              text = "• Uses Android Storage Access Framework (SAF)\n• External directory remains safe across installs\n• No external server/python runtime required\n• Fully offline capable reader",
              fontSize = 12.sp,
              color = EditorialTextDim,
              lineHeight = 18.sp,
              modifier = Modifier.padding(top = 4.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
fun SettingsSection(
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = title,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = EditorialTextSecondary,
      letterSpacing = 1.sp
    )
    content()
  }
}
