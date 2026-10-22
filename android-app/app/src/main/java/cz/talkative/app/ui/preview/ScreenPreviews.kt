package cz.talkative.app.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.SentenceEntry
import cz.talkative.app.domain.model.Word
import cz.talkative.app.ui.about.AboutScreen
import cz.talkative.app.ui.admin.AdminScreen
import cz.talkative.app.ui.admin.AdminUiState
import cz.talkative.app.ui.arasaac.ArasaacScreen
import cz.talkative.app.ui.arasaac.ArasaacUiState
import cz.talkative.app.ui.client.ClientScreen
import cz.talkative.app.ui.client.ClientUiState
import cz.talkative.app.ui.config.ConfigScreen
import cz.talkative.app.ui.config.ConfigUiState
import cz.talkative.app.ui.draw.DrawScreen
import cz.talkative.app.ui.draw.DrawUiState
import cz.talkative.app.ui.exchange.ExchangeScreen
import cz.talkative.app.ui.exchange.ExchangeUiState
import cz.talkative.app.ui.notice.NoticeScreen
import cz.talkative.app.ui.theme.TalkativeTheme

// Náhledy jednotlivých obrazovek v Android Studiu (Split / Design). Obrazovky jsou bezstavové, takže stačí
// ukázkový stav a prázdné obsluhy událostí; obrázky slov se v náhledu nezobrazují (chybí úložiště souborů).

private const val TABLET_WIDTH_DP = 1000
private const val TABLET_HEIGHT_DP = 640

private const val PROFILE = "default"
private val folder = Folder("f1", PROFILE, "Jídlo", parentId = null, sortOrder = 0, updatedAt = 0)
private val words = listOf("Chci", "Ne", "Ano", "Pít", "Jíst", "Pomoc").mapIndexed { index, title ->
    Word("w$index", PROFILE, title, parentId = null, sortOrder = index + 1, updatedAt = 0)
}
private val items = listOf(folder) + words

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) = TalkativeTheme(darkTheme = false, content = content)

@Preview(name = "Klient", widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP, showBackground = true)
@Composable
private fun ClientScreenPreview() = PreviewTheme {
    ClientScreen(
        state = ClientUiState(
            items = items,
            sentence = words.take(3).mapIndexed { index, word -> SentenceEntry(index, word) },
            isLoading = false,
        ),
        onEvent = {},
        onOpenAdmin = {},
        isLandscape = true,
    )
}

@Preview(name = "Klient – prázdná věta", widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP, showBackground = true)
@Composable
private fun ClientScreenEmptyPreview() = PreviewTheme {
    ClientScreen(state = ClientUiState(items = items, isLoading = false), onEvent = {}, onOpenAdmin = {}, isLandscape = true)
}

@Preview(name = "Administrace", widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP, showBackground = true)
@Composable
private fun AdminScreenPreview() = PreviewTheme {
    AdminScreen(state = AdminUiState(items = items, isLoading = false), onEvent = {}, onBackToClient = {})
}

@Preview(name = "Nastavení", widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP, showBackground = true)
@Composable
private fun ConfigScreenPreview() = PreviewTheme {
    ConfigScreen(state = ConfigUiState(isLoading = false), onEvent = {}, onBack = {})
}

@Preview(name = "Export a import", widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP, showBackground = true)
@Composable
private fun ExchangeScreenPreview() = PreviewTheme {
    ExchangeScreen(
        state = ExchangeUiState(),
        onEvent = {},
        onBack = {},
        onChooseExportFile = {},
        onChooseArchive = {},
    )
}

@Preview(name = "Hledání ARASAAC", widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP, showBackground = true)
@Composable
private fun ArasaacScreenPreview() = PreviewTheme {
    ArasaacScreen(state = ArasaacUiState(query = "jíst"), onEvent = {}, onOpenAbout = {}, onBack = {})
}

@Preview(name = "Kreslení", widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP, showBackground = true)
@Composable
private fun DrawScreenPreview() = PreviewTheme {
    val side = 512
    DrawScreen(
        state = DrawUiState(),
        pixels = IntArray(side * side) { WHITE },
        onEvent = {},
        onClose = {},
    )
}

@Preview(name = "O aplikaci", widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP, showBackground = true)
@Composable
private fun AboutScreenPreview() = PreviewTheme {
    AboutScreen(versionName = "1.0", onBack = {}, onOpenLink = {})
}

@Preview(name = "Úvodní oznámení", widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP, showBackground = true)
@Composable
private fun NoticeScreenPreview() = PreviewTheme {
    NoticeScreen(versionName = "1.0", onAcknowledge = {}, onLeave = {}, onOpenLink = {})
}

private const val WHITE = 0xFFFFFFFF.toInt()
