package com.example.mobilereader.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobilereader.R
import com.example.mobilereader.data.AppTheme
import com.example.mobilereader.ui.theme.MobileReaderTheme
import com.example.mobilereader.ui.theme.colorSchemeFor

private data class PlaceholderBook(
    val coverRes: Int,
    val titleRes: Int,
    val authorRes: Int,
    val progress: Float,
)

private val placeholderBooks = listOf(
    PlaceholderBook(R.drawable.cover_alice, R.string.library_book_alice, R.string.library_author_carroll, 0.56f),
    PlaceholderBook(R.drawable.cover_dracula, R.string.library_book_dracula, R.string.library_author_stoker, 0.54f),
    PlaceholderBook(R.drawable.cover_little_women, R.string.library_book_little_women, R.string.library_author_alcott, 0.28f),
    PlaceholderBook(R.drawable.cover_oz, R.string.library_book_oz, R.string.library_author_baum, 0.72f),
    PlaceholderBook(R.drawable.cover_sherlock, R.string.library_book_sherlock, R.string.library_author_doyle, 0.39f),
    PlaceholderBook(R.drawable.cover_time_machine, R.string.library_book_time_machine, R.string.library_author_wells, 0.65f),
)

@Composable
fun LibraryScreen(
    theme: AppTheme,
    loadingBook: Boolean,
    openingProblem: String?,
    onOpenBook: () -> Unit,
) {
    val pageColors = colorSchemeFor(theme)
    Scaffold(
        containerColor = pageColors.surface,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!loadingBook) onOpenBook() },
                modifier = Modifier.height(56.dp),
                expanded = true,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                text = {
                    Text(
                        text = stringResource(
                            if (loadingBook) R.string.library_adding_book else R.string.library_add_book
                        ),
                        fontSize = 14.sp,
                    )
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(14.dp),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                text = stringResource(R.string.library_title),
                modifier = Modifier.fillMaxWidth(),
                color = pageColors.onSurface,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.library_search_placeholder),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 14.sp,
                )
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryFilterLabel(stringResource(R.string.library_filter_all), selected = true)
                LibraryFilterLabel(stringResource(R.string.library_filter_reading), selected = false)
                LibraryFilterLabel(stringResource(R.string.library_filter_finished), selected = false)
            }
            if (openingProblem != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = openingProblem,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(placeholderBooks) { book ->
                    PlaceholderBookCard(book, pageColors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun LibraryFilterLabel(label: String, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PlaceholderBookCard(book: PlaceholderBook, textColor: Color) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(book.coverRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(book.titleRes),
            modifier = Modifier.fillMaxWidth(),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(book.authorRes),
            color = textColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(book.progress)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LibraryScreenLightPreview() {
    MobileReaderTheme(AppTheme.WHITE) {
        LibraryScreen(theme = AppTheme.WHITE, loadingBook = false, openingProblem = null, onOpenBook = {})
    }
}

@Preview(name = "Dark", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LibraryScreenDarkPreview() {
    MobileReaderTheme(AppTheme.OLED) {
        LibraryScreen(theme = AppTheme.OLED, loadingBook = false, openingProblem = null, onOpenBook = {})
    }
}
