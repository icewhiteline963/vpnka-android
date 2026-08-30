package com.v2ray.ang.handler

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Что сейчас держит фоновый плеер.
 *
 * Плеер живёт в службе и переживает уход с экрана — значит экран должен
 * откуда-то узнать, что именно играет, чтобы показать это человеку и дать
 * остановить. Служба знает адрес потока, но не знает ни названия, ни
 * страницы ролика, а тащить их через границу процесса ради одной строки
 * дороже, чем помнить здесь.
 */
object YouTubeNowPlaying {
    var current by mutableStateOf<YouTubeService.Playback?>(null)
}
