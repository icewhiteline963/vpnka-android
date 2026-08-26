package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager

/**
 * Переключатель VPN с виджета — ОТДЕЛЬНЫЙ и НЕэкспортируемый ресивер.
 *
 * Раньше это же действие обрабатывал `WidgetProvider`, а он обязан быть
 * экспортированным: иначе система не доставит ему `APPWIDGET_UPDATE` и
 * виджет вообще перестанет работать. Побочный эффект — любое стороннее
 * приложение на телефоне могло отправить наше действие и ВЫКЛЮЧИТЬ VPN
 * у человека, ничего не спрашивая.
 *
 * Развели: системная часть осталась в экспортированном `WidgetProvider`,
 * а собственно переключение уехало сюда, за `exported="false"`. Снаружи
 * достучаться нельзя, изнутри — обычный PendingIntent.
 */
class WidgetToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AppConfig.BROADCAST_ACTION_WIDGET_CLICK) {
            return
        }
        if (CoreServiceManager.isRunning()) {
            CoreServiceManager.stopVService(context)
        } else {
            CoreServiceManager.startVServiceFromToggle(context)
        }
    }
}
