package com.v2ray.ang.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.handler.VpnkaAccount
import com.v2ray.ang.util.QRCodeDecoder

/**
 * Экран «Поделиться VPNкой»: QR реф-ссылки + системный share + статистика
 * приглашённых (ник · сколько принёс · дата). Кто отсканирует QR и подключится
 * — становится рефералом поделившегося (привязка на стороне приложения:
 * `vpnka://ref?code=…` → register/attach). Данные — из `GET /app/referral`.
 */
@Composable
fun VpnkaShareScreen(
    referral: VpnkaAccount.Referral?,
    onBack: () -> Unit,
    onShareLink: (String) -> Unit,
) {
    VpnkaPage(title = "Поделиться VPNкой", onBack = onBack) {
        if (referral == null) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Загружаем реф-ссылку…",
                color = VpnkaColors.TextMuted,
                fontSize = 15.sp,
            )
            return@VpnkaPage
        }

        // QR рисуем в фоне — bitmap не должен блокировать первый кадр.
        val qr: ImageBitmap? by produceState<ImageBitmap?>(null, referral.link) {
            value = if (referral.link.isNotEmpty())
                QRCodeDecoder.createQRCode(referral.link, 600)?.asImageBitmap()
            else null
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Отсканируйте или отправьте ссылку — человек откроет Телеграм, " +
                "получит приложение и станет вашим рефералом. За каждую его " +
                "оплату — ${referral.bonusPercent}% вам на баланс.",
            color = VpnkaColors.TextMuted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))

        // QR на белой плашке (чтобы читался камерой на любой теме).
        if (qr != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(bitmap = qr!!, contentDescription = "QR", Modifier.size(220.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Ссылка + кнопка «Поделиться» (системный share-intent).
        Text(
            referral.link,
            color = VpnkaColors.TextFaint,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(VpnkaColors.Accent)
                .clickable { onShareLink(referral.link) }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Поделиться ссылкой",
                color = VpnkaColors.OnAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Приглашено: ${referral.invitedCount}  ·  оплатили: " +
                "${referral.paidInvitedCount}  ·  начислено: ${referral.totalRewardRub} ₽",
            color = VpnkaColors.TextStrong,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))

        if (referral.referrals.isEmpty()) {
            Text(
                "Пока никого не пригласили. Поделитесь ссылкой выше 💙",
                color = VpnkaColors.TextMuted,
                fontSize = 14.sp,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(referral.referrals) { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VpnkaColors.BgOffCentre)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.name,
                                color = VpnkaColors.TextStrong,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                r.date.take(10) + (if (r.paid) "" else "  · ещё не оплатил"),
                                color = VpnkaColors.TextFaint,
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            "+${r.broughtRub} ₽",
                            color = if (r.broughtRub > 0) VpnkaColors.Green else VpnkaColors.TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
