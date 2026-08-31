package com.v2ray.ang.handler

import android.content.Context
import android.os.Environment
import android.os.StatFs

/**
 * Сколько места на устройстве и сколько занято нашими загрузками.
 *
 * Блок «Память» в макете стоит первым на экране скачанного — и правильно:
 * человек, который качает фильмы, упирается в место раньше, чем во что-либо
 * ещё, а узнаёт об этом обычно из невнятной ошибки посреди загрузки.
 */
object DeviceStorage {

    data class Info(val totalBytes: Long, val freeBytes: Long, val ourBytes: Long)

    fun read(context: Context): Info {
        val stat = runCatching {
            StatFs(Environment.getExternalStorageDirectory().absolutePath)
        }.getOrNull()
        val total = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
        val free = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L

        // Своё считаем по кэшу загрузок: точный учёт по MediaStore потребовал
        // бы обхода всей папки «Загрузки», включая чужие файлы.
        val ours = runCatching {
            context.cacheDir.listFiles()?.filter { it.name.startsWith("yt_") }
                ?.sumOf { it.length() } ?: 0L
        }.getOrDefault(0L)

        return Info(total, free, ours)
    }

    fun fmt(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 ->
            "%.1f ГБ".format(bytes / 1024.0 / 1024 / 1024).replace('.', ',')
        bytes >= 1024L * 1024 -> "${bytes / 1024 / 1024} МБ"
        bytes > 0 -> "${bytes / 1024} КБ"
        else -> "0"
    }
}
