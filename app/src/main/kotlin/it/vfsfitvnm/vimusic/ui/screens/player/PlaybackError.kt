package it.vfsfitvnm.vimusic.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.center
import it.vfsfitvnm.vimusic.utils.medium
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.toast

@Composable
fun PlaybackError(
    isDisplayed: Boolean,
    messageProvider: () -> String,
    detailProvider: () -> String = { "" },
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val details = remember(isDisplayed) { if (isDisplayed) detailProvider() else "" }

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isDisplayed,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Spacer(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onDismiss() })
                    }
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
            )
        }

        AnimatedVisibility(
            visible = isDisplayed,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut() + slideOutVertically { it / 6 },
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colorPalette.background2)
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colorPalette.accent.copy(alpha = 0.16f))
                ) {
                    Image(
                        painter = painterResource(R.drawable.alert_circle_not_filled),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.accent),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))

                BasicText(
                    text = stringResource(R.string.playback_error_title),
                    style = typography.m.semiBold.copy(textAlign = TextAlign.Center)
                )

                Spacer(Modifier.height(8.dp))

                BasicText(
                    text = messageProvider(),
                    style = typography.s.secondary.center.copy(textAlign = TextAlign.Center)
                )

                if (details.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))

                    BasicText(
                        text = details,
                        style = typography.xxs.secondary.center.medium.copy(textAlign = TextAlign.Center),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )

                    SecondaryTextButton(
                        text = stringResource(R.string.copy_details),
                        onClick = {
                            clipboardManager.setText(AnnotatedString(details))
                            context.toast("Copiado")
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                SecondaryTextButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss
                )
            }
        }
    }
}
