package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.downloader.DownloaderScreen
import com.example.ui.screens.downloader.DownloaderViewModel
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.library.LibraryViewModel
import com.example.ui.screens.reader.ReaderScreen
import com.example.ui.screens.reader.ReaderViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.theme.EditorialBg
import com.example.ui.theme.EditorialOnPrimary
import com.example.ui.theme.EditorialOutline
import com.example.ui.theme.EditorialPrimary
import com.example.ui.theme.EditorialPrimaryContainer
import com.example.ui.theme.EditorialSurface
import com.example.ui.theme.EditorialTextDim
import com.example.ui.theme.EditorialTextPrimary
import com.example.ui.theme.EditorialTextSecondary

enum class AppTab(val title: String, val icon: ImageVector) {
  LIBRARY("LIBRARY", Icons.Default.MenuBook),
  DOWNLOAD("DOWNLOAD", Icons.Default.CloudDownload),
  SETTINGS("SETTINGS", Icons.Default.Settings)
}

data class ActiveReaderSession(
  val seriesUri: String,
  val chapterUri: String,
  val seriesTitle: String,
  val chapterName: String
)

@Composable
fun AppNavigation(
  libraryViewModel: LibraryViewModel = viewModel(),
  downloaderViewModel: DownloaderViewModel = viewModel(),
  settingsViewModel: SettingsViewModel = viewModel(),
  readerViewModel: ReaderViewModel = viewModel()
) {
  var currentTab by remember { mutableStateOf(AppTab.LIBRARY) }
  var activeReaderSession by remember { mutableStateOf<ActiveReaderSession?>(null) }

  if (activeReaderSession != null) {
    // Immersive Manga Reader Screen
    ReaderScreen(
      viewModel = readerViewModel,
      onBack = {
        activeReaderSession = null
        libraryViewModel.refreshLibrary()
      }
    )
  } else {
    // Main App Shell with Editorial Bottom Bar
    Scaffold(
      containerColor = EditorialBg,
      floatingActionButton = {
        if (currentTab == AppTab.LIBRARY) {
          FloatingActionButton(
            onClick = { currentTab = AppTab.DOWNLOAD },
            containerColor = EditorialPrimary,
            contentColor = EditorialOnPrimary,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
              .padding(bottom = 16.dp, end = 8.dp)
              .testTag("fab_download_series")
          ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Download", modifier = Modifier.size(28.dp))
          }
        }
      },
      bottomBar = {
        EditorialBottomNav(
          currentTab = currentTab,
          onTabSelected = { currentTab = it }
        )
      }
    ) { paddingValues ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        when (currentTab) {
          AppTab.LIBRARY -> {
            LibraryScreen(
              viewModel = libraryViewModel,
              onOpenReader = { sUri, chUri, title, chName ->
                readerViewModel.loadChapter(sUri, chUri, title, chName)
                activeReaderSession = ActiveReaderSession(sUri, chUri, title, chName)
              },
              onNavigateToDownloader = { currentTab = AppTab.DOWNLOAD }
            )
          }

          AppTab.DOWNLOAD -> {
            DownloaderScreen(viewModel = downloaderViewModel)
          }

          AppTab.SETTINGS -> {
            SettingsScreen(viewModel = settingsViewModel)
          }
        }
      }
    }
  }
}

@Composable
fun EditorialBottomNav(
  currentTab: AppTab,
  onTabSelected: (AppTab) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    color = EditorialSurface,
    border = androidx.compose.foundation.BorderStroke(1.dp, EditorialOutline),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .height(84.dp)
        .padding(horizontal = 24.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      AppTab.values().forEach { tab ->
        val isSelected = currentTab == tab

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier
            .clickable { onTabSelected(tab) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("nav_tab_${tab.name.lowercase()}")
        ) {
          Box(
            modifier = Modifier
              .clip(CircleShape)
              .background(if (isSelected) EditorialPrimaryContainer else Color.Transparent)
              .padding(horizontal = 18.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = tab.icon,
              contentDescription = tab.title,
              tint = if (isSelected) EditorialPrimary else EditorialTextDim,
              modifier = Modifier.size(22.dp)
            )
          }

          Text(
            text = tab.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = if (isSelected) EditorialPrimary else EditorialTextSecondary
          )
        }
      }
    }
  }
}
