package com.example.mobilereader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mobilereader.data.AppSettings
import com.example.mobilereader.data.AppTheme
import com.example.mobilereader.data.ReaderSettings
import com.example.mobilereader.data.TranslationSettings
import com.example.mobilereader.data.model.Book
import com.example.mobilereader.data.model.Bookmark
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.ParagraphStyle
import com.example.mobilereader.data.model.Quote
import com.example.mobilereader.ui.preview.PreviewBooks
import com.example.mobilereader.ui.theme.MobileReaderTheme

private val russianBook: Book
    get() = PreviewBooks.current.copy(
        title = "Собака Баскервилей",
        author = "Артур Конан Дойл",
        language = "ru",
        bookmarks = listOf(Bookmark("bookmark", 1, 0, "Дорога тянулась через пустошь.", 0L)),
        quotes = listOf(Quote("quote", "Вдалеке мерцал одинокий огонь.", 0, 0L, 1, 0, 1, 32)),
    )

private fun russianReaderState() = ReaderState.Ready(
    book = russianBook,
    items = listOf(
        ReaderItem(0, ContentElement.Heading("Глава первая", level = 1)),
        ReaderItem(0, ContentElement.Paragraph(AnnotatedString(
            "Дорога тянулась через пустошь. Вдалеке мерцал одинокий огонь. " +
                "За холмами медленно поднимался туман, скрывая тропу и низкие каменные ограды. " +
                "Путешественники шли молча: каждый шаг отдавался в сырой земле, а ветер доносил запах вереска."
        ))),
        ReaderItem(0, ContentElement.Paragraph(buildAnnotatedString {
            append("Когда показался старый дом, она остановилась. ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Окно на втором этаже было открыто") }
            append(", хотя все уверяли, что там давно никто не живёт. ")
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("Может быть, это всего лишь ветер") }
            append(", подумала она, но всё равно ускорила шаг.")
        })),
        ReaderItem(0, ContentElement.Heading("У ворот", level = 2)),
        ReaderItem(0, ContentElement.Paragraph(
            AnnotatedString("«На пустоши никогда не бывает по-настоящему тихо», — сказал проводник. «Слушайте внимательно, и она сама расскажет вам, куда идти»."),
            style = ParagraphStyle.QUOTE,
        )),
        ReaderItem(0, ContentElement.Paragraph(AnnotatedString(
            "Они дошли до ворот уже в сумерках. На железных прутьях блестели капли дождя; " +
                "над дорожкой нависали ветви, а за ними виднелась узкая полоска света. " +
                "Она вспомнила письмо, полученное утром, и снова перечитала последнюю строку."
        ))),
        ReaderItem(0, ContentElement.Divider),
        ReaderItem(0, ContentElement.Paragraph(AnnotatedString(
            "У крыльца их встретил хозяин дома. Он говорил спокойно и даже улыбался, " +
                "но не пригласил гостей внутрь, пока часы в прихожей не пробили девять. " +
                "В этот миг из глубины коридора донёсся глухой звук, похожий на шаги."
        ))),
        ReaderItem(0, ContentElement.Paragraph(AnnotatedString(
            "Она обернулась к проводнику. Тот смотрел не на дверь, а в сторону пустоши. " +
                "Туман уже скрыл дорогу, по которой они пришли, и возврат казался невозможным. " +
                "Оставалось войти и выяснить, кто зажёг свет в заброшенной комнате."
        ))),
        ReaderItem(1, ContentElement.Heading("Глава вторая", level = 1)),
    ),
    chapterStarts = listOf(0, 9),
    chapterTitles = listOf("Глава первая", "Глава вторая"),
    chapterDepths = listOf(0, 0),
    notes = emptyMap(),
    navigation = listOf(
        ReaderNavigationEntry("Глава первая", 0, ReaderNavigationTarget.ReadingOrder(0, 0)),
        ReaderNavigationEntry("Глава вторая", 0, ReaderNavigationTarget.ReadingOrder(9, 1)),
    ),
)

@Preview(name = "01 · Выбор книги", locale = "ru", showBackground = true, widthDp = 412, heightDp = 916)
@Composable
private fun BookPickerRu() {
    MobileReaderTheme(AppTheme.WHITE) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("MobileReader", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            Text("Откройте книгу EPUB, чтобы начать чтение", style = MaterialTheme.typography.titleLarge)
            Button(onClick = {}) { Text("Открыть EPUB") }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Preview(name = "02 · Чтение", locale = "ru", showBackground = true, widthDp = 412, heightDp = 916)
@Composable
private fun ReaderPageRu() = ReaderChapterRu(0)

@Preview(name = "02б · Цитата", locale = "ru", showBackground = true, widthDp = 412, heightDp = 916)
@Composable
private fun ReaderChapterStylesRu() = ReaderChapterRu(4)

@Composable
private fun ReaderChapterRu(firstItemIndex: Int) {
    MobileReaderTheme(AppTheme.WHITE) {
        val colors = readerColors(AppTheme.WHITE)
        val chapterItems = russianReaderState().items.takeWhile { it.chapterIndex == 0 }.drop(firstItemIndex)
        Column(Modifier.fillMaxSize().background(colors.background).padding(horizontal = 24.dp)) {
            Text(russianBook.title, Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 32.dp), color = colors.secondaryText)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                chapterItems.forEach { item ->
                    when (val element = item.element) {
                        is ContentElement.Heading -> Text(
                            element.styledText,
                            style = if (element.level == 1) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                            color = colors.text,
                        )
                        is ContentElement.Paragraph -> Text(
                            element.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (element.style == ParagraphStyle.QUOTE) colors.secondaryText else colors.text,
                        )
                        ContentElement.Divider -> HorizontalDivider(color = colors.secondaryText)
                        else -> Unit
                    }
                }
            }
            Text("42% · Страница 101 из 240", Modifier.padding(bottom = 28.dp), color = colors.secondaryText)
        }
    }
}

@Composable
private fun ReaderPanelRu(section: ReaderPanelSection) {
    MobileReaderTheme(AppTheme.WHITE) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            ReaderPanelsContent(
                colorScheme = MaterialTheme.colorScheme,
                ready = russianReaderState(),
                book = russianBook,
                currentChapter = 0,
                chapterStartPages = listOf(0, 10),
                section = section,
                onChapterClick = {},
                onBookmarkClick = {},
                onRemoveBookmark = {},
                onCopyQuote = {},
                onQuoteClick = {},
                onRemoveQuote = {},
            )
        }
    }
}

@Preview(name = "03 · Содержание", locale = "ru", showBackground = true, widthDp = 412, heightDp = 650)
@Composable
private fun ContentsRu() = ReaderPanelRu(ReaderPanelSection.CONTENTS)

@Preview(name = "04 · Закладки", locale = "ru", showBackground = true, widthDp = 412, heightDp = 650)
@Composable
private fun BookmarksRu() = ReaderPanelRu(ReaderPanelSection.BOOKMARKS)

@Preview(name = "05 · Цитаты", locale = "ru", showBackground = true, widthDp = 412, heightDp = 650)
@Composable
private fun QuotesRu() = ReaderPanelRu(ReaderPanelSection.QUOTES)

@Preview(name = "06 · Поиск", locale = "ru", showBackground = true, widthDp = 412, heightDp = 650)
@Composable
private fun SearchRu() {
    MobileReaderTheme(AppTheme.WHITE) {
        ReaderSearchOverlay(
            visible = true,
            query = "пустошь",
            colors = readerColors(AppTheme.WHITE),
            chapterTitles = listOf("Глава первая", "Глава вторая"),
            results = SearchResults(
                query = "пустошь",
                matches = listOf(SearchMatch(1, 0, 28, "Дорога тянулась через ", "пустошь", ".")),
                totalCount = 1,
            ),
            pages = null,
            onResultClick = {},
            onGoToPage = {},
            onClose = {},
        )
    }
}

@Composable
private fun ReaderSettingsRu(category: Int) {
    MobileReaderTheme(AppTheme.WHITE) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            ReaderSettingsControls(
                selectedCategoryIndex = category,
                onCategorySelected = {},
                settings = ReaderSettings(),
                appSettings = AppSettings(),
                colors = readerColors(AppTheme.WHITE),
                brightness = null,
                onUpdate = {},
                onUpdateApp = {},
                onBrightnessChange = {},
                translationSettings = TranslationSettings(),
                onSaveTranslationSettings = {},
            )
        }
    }
}

@Preview(name = "07 · Внешний вид", locale = "ru", showBackground = true, widthDp = 412, heightDp = 650)
@Composable
private fun AppearanceRu() = ReaderSettingsRu(0)

@Preview(name = "08 · Страницы", locale = "ru", showBackground = true, widthDp = 412, heightDp = 650)
@Composable
private fun PagesRu() = ReaderSettingsRu(1)

@Preview(name = "09 · Перевод", locale = "ru", showBackground = true, widthDp = 412, heightDp = 650)
@Composable
private fun TranslationSettingsRu() = ReaderSettingsRu(2)

@Preview(name = "09 · Контекстный перевод", locale = "ru", showBackground = true, widthDp = 412, heightDp = 650)
@Composable
private fun ContextualTranslationRu() {
    MobileReaderTheme(AppTheme.WHITE) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Контекстный перевод", style = MaterialTheme.typography.headlineSmall)
            Text("moor", style = MaterialTheme.typography.titleMedium)
            Text("Перевод", style = MaterialTheme.typography.titleSmall)
            Text("пустошь", style = MaterialTheme.typography.bodyLarge)
            Text("Объяснение", style = MaterialTheme.typography.titleSmall)
            Text(
                "Здесь moor означает открытую болотистую местность. В описании дороги слово подчёркивает безлюдный, мрачный пейзаж.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("Оттенок смысла", style = MaterialTheme.typography.titleSmall)
            Text(
                "В контексте английской готической прозы «пустошь» звучит тревожнее, чем нейтральное «равнина».",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
