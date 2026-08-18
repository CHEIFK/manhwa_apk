package com.example.ui.screens.downloader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ChapterDownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.ui.theme.EditorialAccentCrimson
import com.example.ui.theme.EditorialAccentGreen
import com.example.ui.theme.EditorialBg
import com.example.ui.theme.EditorialOnPrimary
import com.example.ui.theme.EditorialOutline
import com.example.ui.theme.EditorialOutlineVariant
import com.example.ui.theme.EditorialPrimary
import com.example.ui.theme.EditorialPrimaryContainer
import com.example.ui.theme.EditorialSurface
import com.example.ui.theme.EditorialTextDim
import com.example.ui.theme.EditorialTextPrimary
import com.example.ui.theme.EditorialTextSecondary

@Composable
fun DownloaderScreen(
  viewModel: DownloaderViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activeTask by viewModel.activeTask.collectAsState()
  val downloadHistory by viewModel.downloadHistory.collectAsState()
  val downloadWorkers by viewModel.downloadWorkers.collectAsState()
  val urlInput by viewModel.urlInput.collectAsState()
  val isFetching by viewModel.isFetching.collectAsState()
  val errorMessage by viewModel.errorMessage.collectAsState()
  val scrapedSeries by viewModel.scrapedSeries.collectAsState()
  val selectedChapterUrls by viewModel.selectedChapterUrls.collectAsState()

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
            text = "Manga Downloader",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = EditorialPrimary,
            letterSpacing = (-0.5).sp
          )
          Text(
            text = "$downloadWorkers CONCURRENT WORKERS • AUTO RESUME",
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
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            tint = EditorialPrimary,
            modifier = Modifier.size(22.dp)
          )
        }
      }
    }

    // Active Task Card (if running)
    if (activeTask != null) {
      item {
        ActiveDownloadCard(
          task = activeTask!!,
          onCancel = { viewModel.cancelDownload() }
        )
      }
    }

    // URL Input Card
    item {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = EditorialSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, EditorialOutline),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 8.dp)
      ) {
        Column(
          modifier = Modifier
            .padding(18.dp)
            .fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Text(
            text = "DISCOVER SERIES CHAPTERS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = EditorialTextSecondary,
            letterSpacing = 1.sp
          )

          OutlinedTextField(
            value = urlInput,
            onValueChange = { viewModel.setUrlInput(it) },
            placeholder = { Text("Paste series or chapter URL...", color = EditorialTextSecondary, fontSize = 13.sp) },
            leadingIcon = {
              Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = EditorialPrimary)
            },
            trailingIcon = {
              if (urlInput.isNotEmpty()) {
                IconButton(onClick = { viewModel.setUrlInput("") }) {
                  Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = EditorialTextSecondary)
                }
              }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = EditorialBg,
              unfocusedContainerColor = EditorialBg,
              focusedTextColor = EditorialTextPrimary,
              unfocusedTextColor = EditorialTextPrimary,
              focusedBorderColor = EditorialPrimary,
              unfocusedBorderColor = EditorialOutline
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("series_url_input_field")
          )

          // Fetch Button
          Button(
            onClick = { viewModel.fetchChapters() },
            enabled = !isFetching,
            colors = ButtonDefaults.buttonColors(
              containerColor = EditorialPrimary,
              contentColor = EditorialOnPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("fetch_chapters_button")
          ) {
            if (isFetching) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = EditorialOnPrimary,
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Scanning Web Pages...", fontWeight = FontWeight.SemiBold)
            } else {
              Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Scan & Discover Chapters", fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }

    // Error Alert
    if (errorMessage != null) {
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = Color(0xFF331418),
          border = androidx.compose.foundation.BorderStroke(1.dp, EditorialAccentCrimson.copy(alpha = 0.5f)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = EditorialAccentCrimson)
            Text(
              text = errorMessage ?: "",
              fontSize = 12.sp,
              color = EditorialTextPrimary,
              modifier = Modifier.weight(1f)
            )
            IconButton(
              onClick = { viewModel.clearError() },
              modifier = Modifier.size(24.dp)
            ) {
              Icon(imageVector = Icons.Default.Clear, contentDescription = "Dismiss", tint = EditorialTextSecondary)
            }
          }
        }
      }
    }

    // Discovered Chapters View
    if (scrapedSeries != null) {
      val series = scrapedSeries!!

      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = EditorialSurface,
          border = androidx.compose.foundation.BorderStroke(1.dp, EditorialOutline),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
          Column(
            modifier = Modifier
              .padding(16.dp)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            // Series Header Info
            Row(
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (series.coverUrl != null) {
                AsyncImage(
                  model = ImageRequest.Builder(context)
                    .data(series.coverUrl)
                    .crossfade(true)
                    .build(),
                  contentDescription = series.title,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                    .size(width = 54.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
                )
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = series.title,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = EditorialTextPrimary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${series.chapters.size} Chapters Found",
                  fontSize = 12.sp,
                  color = EditorialTextSecondary
                )
              }
            }

            // Select all / deselect all controls
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "${selectedChapterUrls.size} SELECTED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = EditorialPrimary,
                letterSpacing = 1.sp
              )

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(EditorialBg)
                    .clickable { viewModel.selectAllChapters() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                  Text("Select All", fontSize = 11.sp, color = EditorialTextDim)
                }

                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(EditorialBg)
                    .clickable { viewModel.deselectAllChapters() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                  Text("Deselect", fontSize = 11.sp, color = EditorialTextDim)
                }
              }
            }

            // Chapters checklist container
            Column(
              verticalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              series.chapters.forEach { chapter ->
                val isSelected = selectedChapterUrls.contains(chapter.url)
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0xFF1E2838) else EditorialBg)
                    .border(1.dp, if (isSelected) EditorialPrimaryContainer else EditorialOutline, RoundedCornerShape(10.dp))
                    .clickable { viewModel.toggleChapterSelection(chapter.url) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Checkbox(
                    checked = isSelected,
                    onCheckedChange = { viewModel.toggleChapterSelection(chapter.url) },
                    colors = CheckboxDefaults.colors(
                      checkedColor = EditorialPrimary,
                      checkmarkColor = EditorialOnPrimary,
                      uncheckedColor = EditorialOutlineVariant
                    ),
                    modifier = Modifier.size(24.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = chapter.name,
                    fontSize = 13.sp,
                    color = EditorialTextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }

            // Start Download Button
            Button(
              onClick = { viewModel.startDownloading() },
              enabled = selectedChapterUrls.isNotEmpty(),
              colors = ButtonDefaults.buttonColors(
                containerColor = EditorialPrimary,
                contentColor = EditorialOnPrimary
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("start_download_action_button")
            ) {
              Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Download ${selectedChapterUrls.size} Chapters", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // Past Download Tasks History
    if (downloadHistory.isNotEmpty()) {
      item {
        Text(
          text = "DOWNLOAD HISTORY",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = EditorialTextSecondary,
          letterSpacing = 1.sp,
          modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
        )
      }

      items(downloadHistory, key = { it.id }) { task ->
        HistoryTaskItem(task = task)
      }
    }
  }
}

@Composable
fun ActiveDownloadCard(
  task: DownloadTask,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = EditorialSurface,
    border = androidx.compose.foundation.BorderStroke(1.dp, EditorialPrimaryContainer),
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 8.dp)
  ) {
    Column(
      modifier = Modifier
        .padding(18.dp)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "DOWNLOADING",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = EditorialPrimary,
            letterSpacing = 1.sp
          )
          Text(
            text = task.seriesTitle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = EditorialTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF441820))
            .clickable(onClick = onCancel)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("cancel_download_button")
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Stop,
              contentDescription = "Cancel",
              tint = EditorialAccentCrimson,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "Cancel",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = EditorialAccentCrimson
            )
          }
        }
      }

      val currentChapter = task.chapters.getOrNull(task.currentChapterIndex)
      Text(
        text = "Current: ${currentChapter?.name ?: "Preparing..."} (${currentChapter?.downloadedPages ?: 0}/${currentChapter?.totalPages ?: 0} pages)",
        fontSize = 12.sp,
        color = EditorialTextSecondary
      )

      val progress = if (task.totalChapters > 0) {
        (task.completedChapters.toFloat() / task.totalChapters.toFloat()).coerceIn(0f, 1f)
      } else 0f

      LinearProgressIndicator(
        progress = { progress },
        color = EditorialPrimary,
        trackColor = EditorialBg,
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(CircleShape)
      )

      Text(
        text = "Completed ${task.completedChapters} of ${task.totalChapters} chapters • ${task.totalImagesDownloaded} images saved",
        fontSize = 11.sp,
        color = EditorialTextDim
      )
    }
  }
}

@Composable
fun HistoryTaskItem(
  task: DownloadTask,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = EditorialSurface,
    border = androidx.compose.foundation.BorderStroke(1.dp, EditorialOutline),
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 4.dp)
  ) {
    Row(
      modifier = Modifier
        .padding(14.dp)
        .fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = task.seriesTitle,
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          color = EditorialTextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "${task.completedChapters}/${task.totalChapters} Chapters • ${task.totalImagesDownloaded} Images",
          fontSize = 11.sp,
          color = EditorialTextSecondary
        )
      }

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(
            when (task.status) {
              DownloadStatus.COMPLETED -> Color(0xFF143820)
              DownloadStatus.FAILED -> Color(0xFF381418)
              DownloadStatus.CANCELLED -> Color(0xFF282828)
              else -> EditorialBg
            }
          )
          .padding(horizontal = 8.dp, vertical = 3.dp)
      ) {
        Text(
          text = task.status.name,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = when (task.status) {
            DownloadStatus.COMPLETED -> EditorialAccentGreen
            DownloadStatus.FAILED -> EditorialAccentCrimson
            else -> EditorialTextSecondary
          }
        )
      }
    }
  }
}
