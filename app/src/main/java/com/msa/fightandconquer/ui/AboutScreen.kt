package com.msa.fightandconquer.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.BuildConfig
import com.msa.fightandconquer.R

/**
 * Static "About" page: identity and version, what the game is and what inspired it,
 * credits, links, and the bundled open-source licenses. No ViewModel state — everything
 * comes from resources plus [BuildConfig].
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val openLink = rememberLinkOpener()
    // Hoisted: the lambdas below run outside composition and can't call stringResource.
    val sourceUrl = stringResource(R.string.about_link_source_url)
    val contactUri = stringResource(R.string.about_link_contact_uri)
    val licenseUrl = stringResource(R.string.about_license_url)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        AboutHeader()

        AboutSection(R.string.about_section_game) {
            AboutBody(stringResource(R.string.about_game_body))
            Spacer(Modifier.height(10.dp))
            AboutBody(stringResource(R.string.about_game_inspiration))
        }

        AboutSection(R.string.about_section_credits) {
            CreditLine(R.string.about_credits_author_label, R.string.about_credits_author_name)
            Spacer(Modifier.height(12.dp))
            CreditLine(R.string.about_credits_built_label, R.string.about_credits_built_body)
            Spacer(Modifier.height(12.dp))
            AboutBody(stringResource(R.string.about_credits_note))
        }

        AboutSection(R.string.about_section_links) {
            LinkRow(
                labelRes = R.string.about_link_source_label,
                valueRes = R.string.about_link_source_value,
                descriptionRes = R.string.cd_about_link_source,
                onClick = { openLink(sourceUrl) },
            )
            LinkRow(
                labelRes = R.string.about_link_contact_label,
                valueRes = R.string.about_link_contact_value,
                descriptionRes = R.string.cd_about_link_contact,
                onClick = { openLink(contactUri) },
            )
        }

        AboutSection(R.string.about_section_licenses) {
            AboutBody(stringResource(R.string.about_licenses_intro))
            for (library in ABOUT_LIBRARIES) {
                Spacer(Modifier.height(14.dp))
                LicenseRow(library)
            }
            Spacer(Modifier.height(6.dp))
            LinkRow(
                labelRes = R.string.about_license_full_text_label,
                valueRes = R.string.about_license_full_text_value,
                descriptionRes = R.string.cd_about_link_license,
                onClick = { openLink(licenseUrl) },
            )
        }

        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = onBack) {
            Text(stringResource(R.string.common_back), color = UiColors.ink)
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** A bundled third-party dependency, as shown in the licenses section. */
private class AboutLibrary(
    @StringRes val nameRes: Int,
    @StringRes val useRes: Int,
    @StringRes val licenseRes: Int,
)

// Versions in these strings mirror gradle/libs.versions.toml — update both together.
private val ABOUT_LIBRARIES = listOf(
    AboutLibrary(R.string.about_lib_filament, R.string.about_lib_filament_use, R.string.about_license_apache2),
    AboutLibrary(R.string.about_lib_kotlin_math, R.string.about_lib_kotlin_math_use, R.string.about_license_apache2),
    AboutLibrary(R.string.about_lib_compose, R.string.about_lib_compose_use, R.string.about_license_apache2),
    AboutLibrary(R.string.about_lib_kotlin, R.string.about_lib_kotlin_use, R.string.about_license_apache2),
)

@Composable
private fun AboutHeader() {
    // piece_capital is a plain PNG; the launcher icon is an adaptive-icon XML, which
    // painterResource cannot inflate.
    Box(
        Modifier
            .size(132.dp)
            .background(UiColors.panel, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.piece_capital),
            contentDescription = null,
            Modifier.size(104.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    Text(
        stringResource(R.string.app_name),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = UiColors.ink,
    )
    Text(
        stringResource(R.string.about_tagline),
        fontSize = 13.sp,
        color = UiColors.inkMuted,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        fontSize = 12.sp,
        color = UiColors.inkFaint,
    )
}

@Composable
private fun AboutSection(@StringRes titleRes: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(titleRes).uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = UiColors.inkFaint,
            modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = UiColors.panel,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp), content = content)
        }
    }
}

@Composable
private fun AboutBody(text: String) {
    Text(text, fontSize = 13.sp, color = UiColors.ink, lineHeight = 18.sp)
}

@Composable
private fun MicroLabel(@StringRes labelRes: Int) {
    Text(
        stringResource(labelRes).uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = UiColors.inkFaint,
    )
}

@Composable
private fun CreditLine(@StringRes labelRes: Int, @StringRes valueRes: Int) {
    Column(Modifier.fillMaxWidth()) {
        MicroLabel(labelRes)
        Spacer(Modifier.height(2.dp))
        AboutBody(stringResource(valueRes))
    }
}

@Composable
private fun LicenseRow(library: AboutLibrary) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(library.nameRes),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = UiColors.ink,
        )
        Text(
            stringResource(library.useRes),
            fontSize = 13.sp,
            color = UiColors.inkSecondary,
            lineHeight = 18.sp,
        )
        Text(
            stringResource(library.licenseRes),
            fontSize = 12.sp,
            color = UiColors.inkFaint,
        )
    }
}

@Composable
private fun LinkRow(
    @StringRes labelRes: Int,
    @StringRes valueRes: Int,
    @StringRes descriptionRes: Int,
    onClick: () -> Unit,
) {
    val description = stringResource(descriptionRes)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 8.dp),
    ) {
        MicroLabel(labelRes)
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(valueRes),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            // The faction pastels and `coin` fail contrast for body text on `panel`;
            // `positive` is the only accent dark enough. Underlined so the affordance
            // survives without color.
            color = UiColors.positive,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/** Opens external URIs, degrading to a toast when nothing on the device can handle one. */
@Composable
private fun rememberLinkOpener(): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val unavailable = stringResource(R.string.about_link_unavailable)
    return remember(uriHandler, context, unavailable) {
        { uri: String ->
            // AndroidUriHandler rethrows ActivityNotFoundException as IllegalArgumentException,
            // so catch broadly — a dead tap is fine here, a crash is not.
            runCatching { uriHandler.openUri(uri) }.onFailure {
                Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
