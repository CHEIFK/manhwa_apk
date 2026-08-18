package com.example.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.EditorialBg
import com.example.ui.theme.EditorialOutline
import com.example.ui.theme.EditorialOutlineVariant
import com.example.ui.theme.EditorialPrimary
import com.example.ui.theme.EditorialPrimaryContainer
import com.example.ui.theme.EditorialSurface
import com.example.ui.theme.EditorialTextDim
import com.example.ui.theme.EditorialTextPrimary
import com.example.ui.theme.EditorialTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
  viewModel: ReaderViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val seriesTitle by viewModel.seriesTitle.collectAsState()
  val currentChapter by viewModel.currentChapter.collectAsState()
  val allChapters by viewModel.allChapters.collectAsState()
  val pages by viewModel.pages.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val zoomScale by viewModel.zoomScale.collectAsState()
  val isControlsVisible by viewModel.isControlsVisible.collectAsState()

  val listState = rememberLazyListState()

  val currentPageNumber by remember {
    derivedStateOf {
      if (pages.isEmpty()) 0 else (listState.firstVisibleItemIndex + 1).coerceAtMost(pages.size)
    }
  }

  val currentChapterIndex = remember(currentChapter, allChapters) {
    allChapters.indexOfFirst { it.id == currentChapter?.id }
  }

  var isChapterDropdownOpen by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(EditorialBg)
  ) {
    if (isLoading) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(color = EditorialPrimary)
      }
    } else if (pages.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            text = "No pages found in this chapter",
            color = EditorialTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
          )
          Text(
            text = "Check that images (.jpg, .png, .webp) exist in folder",
            color = EditorialTextSecondary,
            fontSize = 12.sp
          )
        }
      }
    } else {
      // Continuous Vertical Manga Reader
      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(Unit) {
            detectTapGestures(
              onTap = {
                viewModel.toggleControls()
              },
              onDoubleTap = {
                val nextZoom = if (zoomScale > 1.2f) 1.0f else 1.75f
                viewModel.setZoom(nextZoom)
              }
            )
          }
          .graphicsLayer {
            scaleX = zoomScale
            scaleY = zoomScale
          }
          .testTag("manga_reader_scroll_view")
      ) {
        itemsIndexed(pages, key = { _, page -> page.uri.toString() }) { index, page ->
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("reader_page_${index + 1}"),
            contentAlignment = Alignment.Center
          ) {
            AsyncImage(
              model = ImageRequest.Builder(context)
                .data(page.uri)
                .crossfade(true)
                .build(),
              contentDescription = "Page ${index + 1}",
              contentScale = ContentScale.FillWidth,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }
    }

    // Top Controls Overlay
    AnimatedVisibility(
      visible = isControlsVisible,
      enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
      exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter)
    ) {
      Surface(
        color = EditorialSurface.copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, EditorialOutline),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.testTag("reader_back_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Library",
                tint = EditorialPrimary
              )
            }

            Column {
              Text(
                text = seriesTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = EditorialTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = currentChapter?.name ?: "Chapter",
                fontSize = 12.sp,
                color = EditorialTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          // Zoom pill
          Box(
            modifier = Modifier
              .clip(CircleShape)
              .background(Color(0xFF003355))
              .clickable { viewModel.setZoom(1.0f) }
              .padding(horizontal = 10.dp, vertical = 5.dp)
          ) {
            Text(
              text = "${(zoomScale * 100).toInt()}%",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = EditorialPrimary
            )
          }
        }
      }
    }

    // Bottom Controls Overlay
    AnimatedVisibility(
      visible = isControlsVisible,
      enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
      exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter)
    ) {
      Surface(
        color = EditorialSurface.copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, EditorialOutline),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Page Indicator & Zoom Controls Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Page Count Badge
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(EditorialPrimaryContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
              Text(
                text = "Page $currentPageNumber / ${pages.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = EditorialPrimary
              )
            }

            // Zoom Quick Controls (- / +)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(EditorialBg)
                  .clickable { viewModel.setZoom(zoomScale - 0.25f) }
                  .testTag("reader_zoom_out_button"),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Remove,
                  contentDescription = "Zoom Out",
                  tint = EditorialPrimary,
                  modifier = Modifier.size(16.dp)
                )
              }

              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(EditorialBg)
                  .clickable { viewModel.setZoom(zoomScale + 0.25f) }
                  .testTag("reader_zoom_in_button"),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = "Zoom In",
                  tint = EditorialPrimary,
                  modifier = Modifier.size(16.dp)
                )
              }

              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(EditorialBg)
                  .clickable { viewModel.setZoom(1.0f) }
                  .testTag("reader_zoom_reset_button"),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.RestartAlt,
                  contentDescription = "Reset Zoom",
                  tint = EditorialTextSecondary,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }

          // Chapter Navigation Controls (Prev | Chapter Dropdown | Next)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Previous Chapter Button
            IconButton(
              onClick = { viewModel.goToPreviousChapter() },
              enabled = currentChapterIndex > 0,
              modifier = Modifier.testTag("reader_prev_chapter_button")
            ) {
              Icon(
                imageVector = Icons.Default.NavigateBefore,
                contentDescription = "Previous Chapter",
                tint = if (currentChapterIndex > 0) EditorialPrimary else EditorialOutlineVariant
              )
            }

            // Chapter Dropdown Selector
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(EditorialBg)
                .border(1.dp, EditorialOutline, RoundedCornerShape(12.dp))
                .clickable { isChapterDropdownOpen = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("reader_chapter_dropdown_trigger"),
              contentAlignment = Alignment.Center
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
              ) {
                Text(
                  text = currentChapter?.name ?: "Select Chapter",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium,
                  color = EditorialTextPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Icon(
                  imageVector = Icons.Default.KeyboardArrowDown,
                  contentDescription = null,
                  tint = EditorialPrimary,
                  modifier = Modifier.size(18.dp)
                )
              }

              DropdownMenu(
                expanded = isChapterDropdownOpen,
                onDismissRequest = { isChapterDropdownOpen = false },
                modifier = Modifier
                  .background(EditorialSurface)
                  .border(1.dp, EditorialOutline)
              ) {
                allChapters.forEach { ch ->
                  DropdownMenuItem(
                    text = {
                      Text(
                        text = ch.name,
                        color = if (ch.id == currentChapter?.id) EditorialPrimary else EditorialTextPrimary,
                        fontWeight = if (ch.id == currentChapter?.id) FontWeight.Bold else FontWeight.Normal
                      )
                    },
                    onClick = {
                      viewModel.switchChapter(ch)
                      isChapterDropdownOpen = false
                    }
                  )
                }
              }
            }

            // Next Chapter Button
            IconButton(
              onClick = { viewModel.goToNextChapter() },
              enabled = currentChapterIndex >= 0 && currentChapterIndex < allChapters.size - 1,
              modifier = Modifier.testTag("reader_next_chapter_button")
            ) {
              Icon(
                imageVector = Icons.Default.NavigateNext,
                contentDescription = "Next Chapter",
                tint = if (currentChapterIndex >= 0 && currentChapterIndex < allChapters.size - 1)
                  EditorialPrimary else EditorialOutlineVariant
              )
            }
          }
        }
      }
    }
  }
}
