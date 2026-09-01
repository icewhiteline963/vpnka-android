package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class UrlSchemeActivity : BaseComponentActivity() {

    /** Показан ли вопрос об импорте: тогда главный экран откроем после ответа. */
    private var asking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // super обязателен: без него Android бросает SuperNotCalledException.
        // Раньше активность жила один кадр и сразу закрывалась, поэтому
        // недосмотр не проявлялся; теперь на ней держится вопрос об импорте —
        // и падать она стала бы ровно там, где нужна.
        super.onCreate(savedInstanceState)
        try {
            intent.apply {
                if (action == Intent.ACTION_SEND) {
                    if ("text/plain" == type) {
                        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                            asking = parseUri(it, null)
                        }
                    }
                } else if (action == Intent.ACTION_VIEW) {
                    when (data?.host) {
                        "install-config" -> {
                            val uri: Uri? = intent.data
                            val shareUrl = uri?.getQueryParameter("url").orEmpty()
                            asking = parseUri(shareUrl, uri?.fragment)
                        }

                        "install-sub" -> {
                            val uri: Uri? = intent.data
                            val shareUrl = uri?.getQueryParameter("url").orEmpty()
                            asking = parseUri(shareUrl, uri?.fragment)
                        }

                        // Back from a card payment. Nothing to parse — the
                        // subscription arrives via the webhook, not via this
                        // link — so this only asks MainActivity to open the
                        // profile and re-read it. No toast: the user did
                        // nothing wrong, they just paid.
                        "paid" -> {
                            AngApplication.vpnkaJustPaid = true
                        }

                        else -> {
                            toastError(R.string.toast_failure)
                        }
                    }
                }
            }

            // Пока висит вопрос об импорте, главный экран не открываем —
            // иначе диалог окажется под ним и человек ответит вслепую.
            if (!asking) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
            finish()
        }
    }

    @Composable
    override fun ScreenContent() {
    }

    /**
     * Импорт по ссылке — только после подтверждения и только ДОПОЛНЕНИЕМ.
     *
     * Ссылка приходит СНАРУЖИ: по ней может прийти кто угодно с любого сайта.
     * Раньше она молча импортировалась с `append = false`, а это стирает
     * серверы группы и подставляет чужие; импортированный профиль вдобавок
     * мог оказаться выбранным автоматически, если совпал по названию — а наши
     * названия это страны, их несложно угадать. Сырой JSON-конфиг при этом
     * управляет маршрутизацией и DNS целиком, то есть чужая ссылка могла
     * увести весь трафик куда угодно.
     *
     * Теперь: спрашиваем человека, показывая адрес источника, и НИКОГДА не
     * затираем то, что у него уже есть.
     */
    private fun parseUri(uriString: String?, fragment: String?): Boolean {
        if (uriString.isNullOrEmpty()) {
            return false
        }

        var decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri == null) return false
        if (uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
            decodedUrl += "#${fragment}"
        }
        // В журнал адрес НЕ пишем: в нём бывает ключ подписки целиком, а лог
        // человек может отправить в поддержку.
        val shown = uri.host ?: decodedUrl.take(40)
        val target = decodedUrl

        android.app.AlertDialog.Builder(this)
            .setTitle("Добавить конфигурацию?")
            .setMessage(
                "Ссылка от «$shown» хочет добавить серверы в VPNka.\n\n" +
                    "Добавляйте только то, что прислали вы сами: чужая " +
                    "конфигурация может направить весь трафик через свои " +
                    "серверы. Ваши текущие серверы останутся на месте."
            )
            .setPositiveButton("Добавить") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    // append = true: дополняем, не затираем.
                    val (count, countSub) = AngConfigManager.importBatchConfig(target, "", true)
                    withContext(Dispatchers.Main) {
                        if (count + countSub > 0) {
                            toast(R.string.import_subscription_success)
                        } else {
                            toast(R.string.import_subscription_failure)
                        }
                        startActivity(Intent(this@UrlSchemeActivity, MainActivity::class.java))
                        finish()
                    }
                }
            }
            .setNegativeButton("Отмена") { _, _ ->
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .setOnCancelListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .show()
        return true
    }
}
