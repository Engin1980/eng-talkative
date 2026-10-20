package cz.talkative.app.ui.about

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import cz.talkative.app.R
import cz.talkative.app.domain.pictogram.ArasaacLinks
import cz.talkative.app.ui.common.openLink
import cz.talkative.app.ui.theme.TalkativeTheme
import cz.talkative.app.ui.common.ScrollableColumn

/** Značka klepnutelného prvku s adresou, např. `about-link-https://arasaac.org`. */
fun aboutLinkTag(url: String) = "about-link-$url"

/** Značka loga ARASAAC. */
const val ABOUT_LOGO_TAG = "about-logo"

/** Napojení obrazovky na systém: verze aplikace a otevření odkazů v prohlížeči. */
@Composable
fun AboutRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember { versionNameOf(context) }
    AboutScreen(versionName = versionName, onBack = onBack, onOpenLink = { openLink(context, it) })
}

/** Verze z `PackageManager`; kdyby ji nešlo zjistit, vrátí prázdný text. */
fun versionNameOf(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }

/** Bezstavová obrazovka „O aplikaci“ v administraci. */
@Composable
fun AboutScreen(
    versionName: String,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = TalkativeTheme.dimens
    BackHandler { onBack() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(dimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.about_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onBack, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.about_back))
            }
        }
        AboutContent(versionName = versionName, onOpenLink = onOpenLink, modifier = Modifier.weight(1f))
    }
}

/** Obsah About (název, verze, ARASAAC, soukromí); sdílí ho i úvodní obrazovka při prvním spuštění. Posuvný. */
@Composable
fun AboutContent(versionName: String, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val dimens = TalkativeTheme.dimens
    ScrollableColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        if (versionName.isNotEmpty()) {
            Text(stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.bodyLarge)
        }
        Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyLarge)

        HorizontalDivider(modifier = Modifier.padding(vertical = dimens.spaceSmall))
        Text(stringResource(R.string.about_section_arasaac), style = MaterialTheme.typography.titleLarge)
        // Logo slouží jen k označení původu piktogramů; neupravuje se a neslouží jako logo aplikace.
        Image(
            painter = painterResource(R.drawable.logo_arasaac),
            contentDescription = stringResource(R.string.about_logo_arasaac),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = dimens.logoMaxWidth)
                .fillMaxWidth()
                .heightIn(min = dimens.minTouchTarget)
                .clickable(role = Role.Button) { onOpenLink(ArasaacLinks.SITE) }
                .testTag(ABOUT_LOGO_TAG),
        )
        Text(stringResource(R.string.about_arasaac_what), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.arasaac_credit), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.arasaac_credit_long), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.about_arasaac_license_meaning), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.about_arasaac_contact), style = MaterialTheme.typography.bodyMedium)
        LinkRow(R.string.about_link_arasaac, ArasaacLinks.SITE, onOpenLink)
        LinkRow(R.string.about_link_terms, ArasaacLinks.TERMS, onOpenLink)
        LinkRow(R.string.about_link_license, ArasaacLinks.LICENSE, onOpenLink)

        HorizontalDivider(modifier = Modifier.padding(vertical = dimens.spaceSmall))
        Text(stringResource(R.string.about_section_privacy), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Odkaz: popisek je klepnutelný a adresa je vidět jako text, aby šla opsat, když chybí prohlížeč. */
@Composable
private fun LinkRow(labelRes: Int, url: String, onOpenLink: (String) -> Unit) {
    val dimens = TalkativeTheme.dimens
    Column {
        TextButton(
            onClick = { onOpenLink(url) },
            modifier = Modifier.heightIn(min = dimens.minTouchTarget).testTag(aboutLinkTag(url)),
        ) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium)
        }
        Text(url, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = dimens.spaceMedium))
    }
}
