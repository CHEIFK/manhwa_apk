package com.example.ui.screens.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.data.model.MangaChapter
import com.example.data.model.MangaSeries
import com.example.data.storage.StorageManager
import com.example.ui.theme.EditorialAccentCrimson
import com.example.ui.theme.EditorialAccentGreen
import com.example.ui.theme.EditorialBg
import com.example.ui.theme.EditorialOnPrimary
import com.example.ui.theme.EditorialOutline
import com.example.ui.theme.EditorialOutlineVariant
import com.example.ui.theme.EditorialPrimary
import com.example.ui.theme.EditorialPrimaryContainer
import com.example.ui.theme.EditorialSecondary
import com.example.ui.theme.EditorialSurface
import com.example.ui.theme.EditorialTextDim
import com.example.ui.theme.EditorialTextPrimary
import com.example.ui.theme.EditorialTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
  viewModel: LibraryViewModel,
  onOpenReader: (seriesUri: String, chapterUri: String, seriesTitle: String, chapterName: String) -> Unit,
  onNavigateToDownloader: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val libraryUriStr by viewModel.libraryUri.collectAsState()
  val isScanning by viewModel.isScanning.collectAsState()
  val filteredSeries by viewModel.filteredSeries.collectAsState()
  val searchQuery by viewModel.searchQuery.collectAsState()
  val selectedSeries by viewModel.selectedSeries.collectAsState()
  val seriesChapters by viewModel.seriesChapters.collectAsState()
  val isLoadingChapters by viewModel.isLoadingChapters.collectAsState()
  val errorMessage by viewModel.errorMessage.collectAsState()

  var isSearchVisible by remember { mutableStateOf(false) }
  var seriesToDelete by remember { mutableStateOf<MangaSeries?>(null) }

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
      viewModel.setLibraryFolderUri(uri)
    }
  }

  // Deletion Confirmation Dialog
  if (seriesToDelete != null) {
    val target = seriesToDelete!!
    AlertDialog(
      onDismissRequest = { seriesToDelete = null },
      title = {
        Text(
          text = "Delete Series",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = EditorialTextPrimary
        )
      },
      text = {
        Text(
          text = "Are you sure you want to delete \"${target.title}\"?\n\nThis will permanently remove all downloaded chapters and image files from your library folder.",
          fontSize = 14.sp,
          color = EditorialTextSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteSeries(target)
            seriesToDelete = null
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = EditorialAccentCrimson,
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.testTag("confirm_delete_series_button")
        ) {
          Text("Delete", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { seriesToDelete = null },
          modifier = Modifier.testTag("cancel_delete_series_button")
        ) {
          Text("Cancel", color = EditorialTextSecondary)
        }
      },
      containerColor = EditorialSurface,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.testTag("delete_confirmation_dialog")
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(EditorialBg)
  ) {
    // Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "Manwa Manager",
          fontSize = 24.sp,
          fontWeight = FontWeight.SemiBold,
          color = EditorialPrimary,
          letterSpacing = (-0.5).sp
        )
        Text(
          text = "V 1.0.0 • LOCAL LIBRARY",
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          color = EditorialTextSecondary,
          letterSpacing = 1.2.sp,
          modifier = Modifier.padding(top = 2.dp)
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Search Button
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(EditorialSurface)
            .clickable { isSearchVisible = !isSearchVisible }
            .testTag("search_toggle_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
            contentDescription = "Search",
            tint = EditorialPrimary,
            modifier = Modifier.size(20.dp)
          )
        }

        // Refresh Button
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(EditorialSurface)
            .clickable { viewModel.refreshLibrary() }
            .testTag("refresh_library_button"),
          contentAlignment = Alignment.Center
        ) {
          if (isScanning) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              color = EditorialPrimary,
              strokeWidth = 2.dp
            )
          } else {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh",
              tint = EditorialPrimary,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }

    // Search bar field
    AnimatedVisibility(
      visible = isSearchVisible,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { viewModel.setSearchQuery(it) },
        placeholder = { Text("Search manga titles...", color = EditorialTextSecondary) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = EditorialSurface,
          unfocusedContainerColor = EditorialSurface,
          focusedTextColor = EditorialTextPrimary,
          unfocusedTextColor = EditorialTextPrimary,
          focusedBorderColor = EditorialPrimary,
          unfocusedBorderColor = EditorialOutline
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 6.dp)
          .testTag("search_input_field")
      )
    }

    // Error Alert Banner
    if (errorMessage != null) {
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
            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = EditorialTextSecondary)
          }
        }
      }
    }

    // Active Storage Card
    val displayPath = remember(libraryUriStr) {
      if (libraryUriStr != null) {
        StorageManager.getDisplayPath(context, Uri.parse(libraryUriStr))
      } else {
        "No Library Folder Selected"
      }
    }

    Surface(
      shape = RoundedCornerShape(16.dp),
      color = EditorialSurface,
      border = androidx.compose.foundation.BorderStroke(1.dp, EditorialOutline),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
      Row(
        modifier = Modifier
          .padding(14.dp)
          .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(EditorialPrimaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "Storage Folder",
            tint = EditorialPrimary,
            modifier = Modifier.size(20.dp)
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "ACTIVE STORAGE",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = EditorialTextSecondary,
            letterSpacing = 1.sp
          )
          Text(
            text = displayPath,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = EditorialTextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Box(
          modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFF003355))
            .clickable { folderPickerLauncher.launch(null) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("change_storage_folder_button")
        ) {
          Text(
            text = if (libraryUriStr == null) "Select" else "Change",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = EditorialPrimary
          )
        }
      }
    }

    // Main Content
    if (libraryUriStr == null) {
      // Empty Folder State
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier.padding(24.dp)
        ) {
          Box(
            modifier = Modifier
              .size(72.dp)
              .clip(CircleShape)
              .background(EditorialSurface),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.FolderOpen,
              contentDescription = null,
              tint = EditorialPrimary,
              modifier = Modifier.size(36.dp)
            )
          }

          Text(
            text = "Select Your Manga Library",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = EditorialTextPrimary
          )

          Text(
            text = "Choose an existing manga/manhwa directory from your device storage or SD card using the Storage Access Framework.",
            fontSize = 13.sp,
            color = EditorialTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )

          Button(
            onClick = { folderPickerLauncher.launch(null) },
            colors = ButtonDefaults.buttonColors(
              containerColor = EditorialPrimary,
              contentColor = EditorialOnPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("select_library_folder_action")
          ) {
            Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Choose Library Folder", fontWeight = FontWeight.SemiBold)
          }
        }
      }
    } else if (filteredSeries.isEmpty() && !isScanning) {
      // No series found in selected folder
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.padding(16.dp)
        ) {
          Icon(
            imageVector = Icons.Default.MenuBook,
            contentDescription = null,
            tint = EditorialOutlineVariant,
            modifier = Modifier.size(56.dp)
          )

          Text(
            text = "No Manga Series Found",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = EditorialTextPrimary
          )

          Text(
            text = "Your library directory is currently empty. You can download new series, create sample offline chapters, or refresh.",
            fontSize = 13.sp,
            color = EditorialTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = { viewModel.createSampleDemoSeries() },
              colors = ButtonDefaults.buttonColors(
                containerColor = EditorialSurface,
                contentColor = EditorialPrimary
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.testTag("create_demo_series_button")
            ) {
              Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Create Demo Series", fontSize = 12.sp)
            }

            Button(
              onClick = onNavigateToDownloader,
              colors = ButtonDefaults.buttonColors(
                containerColor = EditorialPrimary,
                contentColor = EditorialOnPrimary
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.testTag("go_to_downloader_button")
            ) {
              Text("Download", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    } else {
      // Titles section header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "RECENT TITLES",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = EditorialTextSecondary,
          letterSpacing = 1.sp
        )
        Text(
          text = "${filteredSeries.size} Series",
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = EditorialPrimary
        )
      }

      // Series Grid
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
          .fillMaxSize()
          .testTag("series_grid")
      ) {
        items(filteredSeries, key = { it.id }) { series ->
          SeriesCard(
            series = series,
            onClick = { viewModel.selectSeries(series) },
            onDeleteClick = { seriesToDelete = series }
          )
        }
      }
    }
  }

  // Chapter Selector Modal Bottom Sheet
  if (selectedSeries != null) {
    ModalBottomSheet(
      onDismissRequest = { viewModel.selectSeries(null) },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = EditorialSurface,
      contentColor = EditorialTextPrimary,
      dragHandle = {
        Box(
          modifier = Modifier
            .padding(vertical = 10.dp)
            .width(36.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(EditorialOutline)
        )
      }
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 8.dp)
      ) {
        // Series Info Header in sheet
        Row(
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(bottom = 16.dp)
        ) {
          Box(
            modifier = Modifier
              .size(width = 60.dp, height = 80.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(EditorialBg)
              .border(1.dp, EditorialOutline, RoundedCornerShape(12.dp))
          ) {
            if (selectedSeries?.coverUri != null) {
              AsyncImage(
                model = ImageRequest.Builder(context)
                  .data(selectedSeries!!.coverUri)
                  .crossfade(true)
                  .build(),
                contentDescription = selectedSeries!!.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = selectedSeries?.title ?: "",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = EditorialTextPrimary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${seriesChapters.size} Chapters • ${selectedSeries?.totalPages ?: 0} Pages",
              fontSize = 12.sp,
              color = EditorialTextSecondary,
              modifier = Modifier.padding(top = 2.dp)
            )
          }

          // Delete Series button inside bottom sheet
          IconButton(
            onClick = {
              seriesToDelete = selectedSeries
            },
            modifier = Modifier.testTag("sheet_delete_series_button")
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Delete Series",
              tint = EditorialAccentCrimson,
              modifier = Modifier.size(24.dp)
            )
          }
        }

        Text(
          text = "CHAPTERS",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = EditorialTextSecondary,
          letterSpacing = 1.sp,
          modifier = Modifier.padding(bottom = 8.dp)
        )

        if (isLoadingChapters) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = EditorialPrimary)
          }
        } else if (seriesChapters.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(140.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("No chapter folders found in this series.", color = EditorialTextSecondary, fontSize = 13.sp)
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .height(340.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(seriesChapters, key = { it.id }) { chapter ->
              ChapterListItem(
                chapter = chapter,
                onReadClick = {
                  val series = selectedSeries ?: return@ChapterListItem
                  viewModel.selectSeries(null)
                  onOpenReader(series.id, chapter.id, series.title, chapter.name)
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
fun SeriesCard(
  series: MangaSeries,
  onClick: () -> Unit,
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("series_card_${series.title.replace(" ", "_")}"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(3f / 4f)
        .clip(RoundedCornerShape(20.dp))
        .background(EditorialSurface)
        .border(1.dp, EditorialOutline, RoundedCornerShape(20.dp))
    ) {
      if (series.coverUri != null) {
        AsyncImage(
          model = ImageRequest.Builder(context)
            .data(series.coverUri)
            .crossfade(true)
            .build(),
          contentDescription = series.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.MenuBook,
            contentDescription = null,
            tint = EditorialOutlineVariant,
            modifier = Modifier.size(36.dp)
          )
        }
      }

      // Bottom gradient overlay
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(Color.Transparent, Color(0x99111318), Color(0xEE111318)),
              startY = 150f
            )
          )
      )

      // Top right delete button overlay
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(8.dp)
          .size(32.dp)
          .clip(CircleShape)
          .background(Color(0xBB111318))
          .clickable(onClick = onDeleteClick)
          .testTag("delete_series_button_${series.title.replace(" ", "_")}"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.DeleteOutline,
          contentDescription = "Delete ${series.title}",
          tint = EditorialAccentCrimson,
          modifier = Modifier.size(18.dp)
        )
      }

      // Tag badge
      Box(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(10.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(
            if (series.totalPages > 0) EditorialAccentGreen else EditorialPrimary
          )
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          text = if (series.totalPages > 0) "READ" else "NEW",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = if (series.totalPages > 0) Color.White else EditorialOnPrimary
        )
      }
    }

    Column {
      Text(
        text = series.title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = EditorialTextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = "Ch. ${series.chapterCount} • ${series.totalPages} Pages",
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        color = EditorialTextSecondary,
        modifier = Modifier.padding(top = 1.dp)
      )
    }
  }
}

@Composable
fun ChapterListItem(
  chapter: MangaChapter,
  onReadClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(EditorialBg)
      .border(1.dp, EditorialOutline, RoundedCornerShape(12.dp))
      .clickable(onClick = onReadClick)
      .padding(horizontal = 14.dp, vertical = 12.dp)
      .testTag("chapter_item_${chapter.name.replace(" ", "_")}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = chapter.name,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = EditorialTextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = "${chapter.pageCount} Pages",
        fontSize = 11.sp,
        color = EditorialTextSecondary
      )
    }

    Box(
      modifier = Modifier
        .size(32.dp)
        .clip(CircleShape)
        .background(EditorialPrimaryContainer),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = "Read Chapter",
        tint = EditorialPrimary,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}
