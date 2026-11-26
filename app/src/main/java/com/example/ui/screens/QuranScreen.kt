package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import com.example.ui.theme.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.QuranPrefs
import com.example.data.local.QuranRepository
import com.example.data.model.QuranAyah
import com.example.data.model.QuranJuzMeta
import com.example.data.model.QuranPageData
import com.example.data.model.QuranSurahMeta
import com.example.ui.theme.AmberStar
import com.example.ui.theme.AmiriQuranFontFamily
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonDark
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyElevated
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SilverText
import com.example.ui.theme.WarmHighlight
import com.example.ui.viewmodel.QuranUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranScreen(
    quranState: QuranUiState,
    quranPrefs: QuranPrefs,
    onEnsureLoaded: () -> Unit,
    onPageSettled: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onPaperModeChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onEnsureLoaded()
    }

    if (quranState.pages.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                if (quranState.errorMessage != null && !quranState.isLoading) {
                    Text(
                        text = quranState.errorMessage,
                        style = MaterialTheme.typography.titleMedium,
                        color = CrimsonBright,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onEnsureLoaded,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                } else {
                    CircularProgressIndicator(color = CrimsonBright)
                    Text(
                        text = stringResource(R.string.quran_loading),
                        style = MaterialTheme.typography.titleMedium,
                        color = SilverText
                    )
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = (quranPrefs.lastPage - 1).coerceIn(0, 603),
        pageCount = { 604 }
    )
    val coroutineScope = rememberCoroutineScope()
    var showIndexSheet by remember { mutableStateOf(false) }
    var showDisplayBar by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { idx ->
                onPageSettled(idx + 1)
            }
    }

    val currentPageNumber = pagerState.currentPage + 1
    val currentPageData = quranState.pages[currentPageNumber]
    val isCurrentBookmarked = quranPrefs.bookmarkPage == currentPageNumber

    val jumpToPage: (Int) -> Unit = { targetPage ->
        val clampedIdx = (targetPage - 1).coerceIn(0, 603)
        coroutineScope.launch {
            if (kotlin.math.abs(pagerState.currentPage - clampedIdx) <= 3) {
                pagerState.animateScrollToPage(clampedIdx)
            } else {
                pagerState.scrollToPage(clampedIdx)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
        ) {
            // Top Mushaf control bar
            Surface(
                color = NavyDeep.copy(alpha = 0.80f),
                border = BorderStroke(1.dp, NavyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentPageData?.primarySurahName ?: stringResource(R.string.quran_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = IceWhite,
                                maxLines = 1
                            )
                            Text(
                                text = "${stringResource(R.string.quran_riwayah)} • بدون إنترنت • الجزء ${QuranRepository.toArabicIndicNumber(currentPageData?.juzNumber ?: 1)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmHighlight,
                                maxLines = 1
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { showDisplayBar = !showDisplayBar },
                                modifier = Modifier.testTag("quran_display_settings_button")
                            ) {
                                Icon(
                                    imageVector = AppIcons.TextFields,
                                    contentDescription = stringResource(R.string.quran_font_size),
                                    tint = if (showDisplayBar) CrimsonBright else IceWhite
                                )
                            }
                            IconButton(
                                onClick = { onToggleBookmark(currentPageNumber) },
                                modifier = Modifier.testTag("quran_bookmark_button")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentBookmarked) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                                    contentDescription = stringResource(R.string.quran_bookmark_save),
                                    tint = if (isCurrentBookmarked) AmberStar else IceWhite
                                )
                            }
                            Surface(
                                onClick = { showIndexSheet = true },
                                shape = RoundedCornerShape(12.dp),
                                color = CrimsonRed,
                                modifier = Modifier.testTag("quran_index_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.FormatListBulleted,
                                        contentDescription = stringResource(R.string.quran_index),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.quran_index),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = showDisplayBar) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NavyPrimary)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.quran_font_size),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = IceWhite
                                    )
                                    IconButton(
                                        onClick = { onFontSizeChange((quranPrefs.fontSizeSp - 2f).coerceAtLeast(18f)) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(NavyElevated)
                                    ) {
                                        Icon(AppIcons.Remove, contentDescription = null, tint = IceWhite, modifier = Modifier.size(16.dp))
                                    }
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = "${quranPrefs.fontSizeSp.toInt()}",
                                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                            color = WarmHighlight
                                        )
                                    }
                                    IconButton(
                                        onClick = { onFontSizeChange((quranPrefs.fontSizeSp + 2f).coerceAtMost(36f)) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(CrimsonRed)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }

                                Surface(
                                    onClick = { onPaperModeChange(!quranPrefs.paperMode) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (quranPrefs.paperMode) Color(0xFFFAF3E0) else ObsidianBlack,
                                    border = BorderStroke(1.dp, NavyBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (quranPrefs.paperMode) AppIcons.WbSunny else AppIcons.NightsStay,
                                            contentDescription = null,
                                            tint = if (quranPrefs.paperMode) CrimsonDark else AmberStar,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (quranPrefs.paperMode) {
                                                stringResource(R.string.quran_paper_mode)
                                            } else {
                                                stringResource(R.string.quran_night_mode)
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (quranPrefs.paperMode) Color(0xFF1A1612) else IceWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right-to-left HorizontalPager mimicking real Mushaf page flipping
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("quran_horizontal_pager")
            ) { pageIndex ->
                val pageNum = pageIndex + 1
                val pageData = quranState.pages[pageNum]
                if (pageData != null) {
                    MushafPageView(
                        pageData = pageData,
                        fontSizeSp = quranPrefs.fontSizeSp,
                        paperMode = quranPrefs.paperMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom page navigation bar (Previous / Page # / Bookmark Jump / Next)
            Surface(
                color = NavyDeep,
                border = BorderStroke(1.dp, NavyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (currentPageNumber > 1) jumpToPage(currentPageNumber - 1) },
                        enabled = currentPageNumber > 1,
                        modifier = Modifier.testTag("quran_prev_page_button")
                    ) {
                        Icon(AppIcons.KeyboardArrowLeft, contentDescription = null)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(stringResource(R.string.quran_prev_page))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { showIndexSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            color = NavyPrimary,
                            border = BorderStroke(1.dp, NavyBorder)
                        ) {
                            Text(
                                text = "صفحة ${QuranRepository.toArabicIndicNumber(currentPageNumber)} / ٦٠٤",
                                style = MaterialTheme.typography.labelLarge,
                                color = IceWhite,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        if (quranPrefs.bookmarkPage != currentPageNumber) {
                            Surface(
                                onClick = { jumpToPage(quranPrefs.bookmarkPage) },
                                shape = RoundedCornerShape(12.dp),
                                color = CrimsonContainer,
                                border = BorderStroke(1.dp, CrimsonRed)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        AppIcons.Bookmark,
                                        contentDescription = null,
                                        tint = AmberStar,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = QuranRepository.toArabicIndicNumber(quranPrefs.bookmarkPage),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = WarmHighlight
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { if (currentPageNumber < 604) jumpToPage(currentPageNumber + 1) },
                        enabled = currentPageNumber < 604,
                        modifier = Modifier.testTag("quran_next_page_button")
                    ) {
                        Text(stringResource(R.string.quran_next_page))
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(AppIcons.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }

    if (showIndexSheet) {
        QuranIndexBottomSheet(
            surahs = quranState.surahs,
            juzList = quranState.juzList,
            searchResults = quranState.searchResults,
            currentPage = currentPageNumber,
            onSearchQueryChange = onSearchQueryChange,
            onJumpToPage = { page ->
                jumpToPage(page)
                showIndexSheet = false
            },
            onDismiss = { showIndexSheet = false }
        )
    }
}

@Composable
private fun MushafPageView(
    pageData: QuranPageData,
    fontSizeSp: Float,
    paperMode: Boolean,
    modifier: Modifier = Modifier
) {
    val pageBackground = if (paperMode) Color(0xFFFAF3E0) else ObsidianBlack
    val innerFrameBg = if (paperMode) Color(0xFFFFFBF2) else NavyDeep
    val outerBorderColor = if (paperMode) CrimsonDark else NavyBorder
    val innerBorderColor = if (paperMode) Color(0xFFB8941F) else CrimsonRed.copy(alpha = 0.7f)
    val inkColor = if (paperMode) Color(0xFF15120E) else IceWhite
    val verseMarkColor = if (paperMode) CrimsonDark else AmberStar
    val headerTextColor = if (paperMode) NavyPrimary else WarmHighlight

    Box(
        modifier = modifier
            .background(pageBackground)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        // Double ornate Mushaf border
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, outerBorderColor, RoundedCornerShape(16.dp))
                .padding(4.dp)
                .border(1.dp, innerBorderColor, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(innerFrameBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top page metadata ribbon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الجزء ${QuranRepository.toArabicIndicNumber(pageData.juzNumber)} • الحزب ${QuranRepository.toArabicIndicNumber(pageData.hizbNumber)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = headerTextColor
                )
                Text(
                    text = pageData.primarySurahName,
                    style = MaterialTheme.typography.labelLarge,
                    color = headerTextColor
                )
            }

            HorizontalDivider(
                color = innerBorderColor.copy(alpha = 0.55f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            pageData.blocks.forEach { block ->
                if (block.showSurahHeader) {
                    SurahTitleBanner(
                        surah = block.surahMeta,
                        paperMode = paperMode
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (block.showBasmalah) {
                    Text(
                        text = QuranRepository.BASMALAH_UTHMANI,
                        fontFamily = AmiriQuranFontFamily,
                        fontSize = (fontSizeSp + 1f).sp,
                        lineHeight = ((fontSizeSp + 1f) * 1.9f).sp,
                        color = if (paperMode) NavyPrimary else AmberStar,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                val annotatedAyahs = remember(block.ayahs, verseMarkColor, inkColor) {
                    buildAnnotatedString {
                        block.ayahs.forEach { ayah ->
                            withStyle(SpanStyle(color = inkColor)) {
                                append(ayah.displayText)
                            }
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    color = verseMarkColor,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("﴿${QuranRepository.toArabicIndicNumber(ayah.numberInSurah)}﴾")
                                if (ayah.sajda) {
                                    append(" ۩")
                                }
                            }
                            append(" ")
                        }
                    }
                }

                Text(
                    text = annotatedAyahs,
                    fontFamily = AmiriQuranFontFamily,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 2.0f).sp,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))
            HorizontalDivider(
                color = innerBorderColor.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
            )
            Text(
                text = "﴿ ${QuranRepository.toArabicIndicNumber(pageData.pageNumber)} ﴾",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = headerTextColor
            )
        }
    }
}

@Composable
private fun SurahTitleBanner(
    surah: QuranSurahMeta,
    paperMode: Boolean
) {
    val brush = if (paperMode) {
        Brush.horizontalGradient(listOf(NavyPrimary, CrimsonDark, NavyPrimary))
    } else {
        Brush.horizontalGradient(listOf(NavyElevated, CrimsonRed, NavyElevated))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, AmberStar),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = surah.revelationTypeAr,
                style = MaterialTheme.typography.labelMedium,
                color = AmberStar
            )
            Text(
                text = surah.nameAr,
                fontFamily = AmiriQuranFontFamily,
                fontSize = 22.sp,
                color = Color.White
            )
            Text(
                text = "آياتها ${QuranRepository.toArabicIndicNumber(surah.ayahCount)}",
                style = MaterialTheme.typography.labelMedium,
                color = AmberStar
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuranIndexBottomSheet(
    surahs: List<QuranSurahMeta>,
    juzList: List<QuranJuzMeta>,
    searchResults: List<QuranAyah>,
    currentPage: Int,
    onSearchQueryChange: (String) -> Unit,
    onJumpToPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 0 = Surahs, 1 = Juzs, 2 = Verse Search
    var activeTab by remember { mutableIntStateOf(0) }
    var pageInput by remember(currentPage) { mutableStateOf(currentPage.toString()) }
    var searchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavyDeep
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(AppIcons.MenuBook, contentDescription = null, tint = CrimsonBright)
                Text(
                    text = stringResource(R.string.quran_index),
                    style = MaterialTheme.typography.headlineMedium,
                    color = IceWhite
                )
            }

            // Quick direct page jump row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 3) {
                            pageInput = input
                        }
                    },
                    label = { Text(stringResource(R.string.quran_go_to_page), color = SilverText) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            pageInput.toIntOrNull()?.let { p ->
                                onJumpToPage(p.coerceIn(1, 604))
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonBright,
                        unfocusedBorderColor = NavyBorder,
                        focusedContainerColor = NavyPrimary,
                        unfocusedContainerColor = NavyPrimary,
                        focusedTextColor = IceWhite,
                        unfocusedTextColor = IceWhite
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        pageInput.toIntOrNull()?.let { p ->
                            onJumpToPage(p.coerceIn(1, 604))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.quran_go))
                }
            }

            // Sub-tabs: السور | الأجزاء | بحث
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NavyPrimary,
                border = BorderStroke(1.dp, NavyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        0 to stringResource(R.string.quran_surahs),
                        1 to stringResource(R.string.quran_juzs),
                        2 to stringResource(R.string.quran_search_tab)
                    ).forEach { (idx, label) ->
                        val selected = activeTab == idx
                        Surface(
                            onClick = { activeTab = idx },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) CrimsonRed else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color.White else SilverText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            when (activeTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(surahs, key = { it.number }) { surah ->
                            Surface(
                                onClick = { onJumpToPage(surah.startPage) },
                                shape = MaterialTheme.shapes.small,
                                color = NavyPrimary.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, NavyBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(CrimsonContainer)
                                                .border(1.dp, CrimsonRed, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = QuranRepository.toArabicIndicNumber(surah.number),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = WarmHighlight
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = surah.nameAr,
                                                fontFamily = AmiriQuranFontFamily,
                                                fontSize = 19.sp,
                                                color = IceWhite
                                            )
                                            Text(
                                                text = "${surah.revelationTypeAr} • ${surah.ayahCount} آية",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SilverText
                                            )
                                        }
                                    }
                                    Text(
                                        text = "ص ${QuranRepository.toArabicIndicNumber(surah.startPage)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = AmberStar
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(juzList, key = { it.juzNumber }) { juz ->
                            Surface(
                                onClick = { onJumpToPage(juz.startPage) },
                                shape = MaterialTheme.shapes.small,
                                color = NavyPrimary.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, NavyBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = juz.arabicName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = IceWhite
                                        )
                                        Text(
                                            text = "يبدأ من ${juz.startSurahName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SilverText
                                        )
                                    }
                                    Text(
                                        text = "ص ${QuranRepository.toArabicIndicNumber(juz.startPage)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = AmberStar
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                onSearchQueryChange(it)
                            },
                            placeholder = { Text(stringResource(R.string.quran_search_hint), color = SilverText) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CrimsonBright) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonBright,
                                unfocusedBorderColor = NavyBorder,
                                focusedContainerColor = NavyPrimary,
                                unfocusedContainerColor = NavyPrimary,
                                focusedTextColor = IceWhite,
                                unfocusedTextColor = IceWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(275.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(
                                searchResults,
                                key = { "${it.surahNumber}_${it.numberInSurah}" }
                            ) { ayah ->
                                Surface(
                                    onClick = { onJumpToPage(ayah.page) },
                                    shape = MaterialTheme.shapes.small,
                                    color = NavyPrimary.copy(alpha = 0.85f),
                                    border = BorderStroke(1.dp, NavyBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${ayah.surahNameAr} • الآية ${QuranRepository.toArabicIndicNumber(ayah.numberInSurah)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = WarmHighlight
                                            )
                                            Text(
                                                text = "ص ${QuranRepository.toArabicIndicNumber(ayah.page)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = AmberStar
                                            )
                                        }
                                        Text(
                                            text = ayah.displayText,
                                            fontFamily = AmiriQuranFontFamily,
                                            fontSize = 18.sp,
                                            color = IceWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
