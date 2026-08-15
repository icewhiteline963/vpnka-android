package com.v2ray.ang.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Comment cap — the same 2000 chars the server enforces, so a long
 *  message is stopped while it is still being typed rather than rejected
 *  after the user hits send. */
private const val COMMENT_MAX = 2000

/**
 * The rating sheet: five stars, an optional comment, one send.
 *
 * Reached two ways — the "Оставить отзыв" row at the bottom of the home
 * screen, and the one-time prompt raised from a `review_request` notice.
 * `prompt` carries the notice text when it came from the server so the
 * person reads the same words we wrote, not a generic stand-in.
 *
 * The stars are plain text glyphs on purpose: an icon dependency for five
 * characters that every font already has would be a poor trade.
 */
@Composable
fun VpnkaReviewDialog(
    prompt: String?,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit,
) {
    var stars by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        // While a send is in flight the sheet stays put: dismissing it would
        // leave the person unsure whether their review went anywhere.
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Как вам ВПН?") },
        text = {
            Column {
                Text(
                    text = prompt?.takeIf { it.isNotBlank() }
                        ?: "Оцените работу сервиса — это занимает минуту, " +
                        "а нам показывает, что чинить в первую очередь.",
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    for (i in 1..5) {
                        Text(
                            text = if (i <= stars) "★" else "☆",
                            fontSize = 32.sp,
                            color = if (i <= stars) VpnkaColors.Accent
                            else VpnkaColors.TextFaint,
                            modifier = Modifier
                                .clickable(enabled = !sending) { stars = i }
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = {
                        if (it.length <= COMMENT_MAX) comment = it
                    },
                    enabled = !sending,
                    placeholder = {
                        Text("Что нравится или мешает? (необязательно)")
                    },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                // A review with no stars is not a review — `overall` is the
                // one field the server requires.
                enabled = stars > 0 && !sending,
                onClick = { onSubmit(stars, comment) },
            ) {
                Text(if (sending) "Отправляем…" else "Отправить")
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) {
                Text("Не сейчас")
            }
        },
    )
}
