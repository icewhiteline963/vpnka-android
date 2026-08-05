package com.v2ray.ang.enums

/**
 * Enum defining different notification channels.
 * Each channel has a unique channelId, notificationId, and display name.
 */
enum class NotificationChannelType(
    val channelId: String,
    val channelName: String,
    val notificationId: Int,
    /**
     * Android's IMPORTANCE_* value. The service channels stay LOW — they are
     * status, not news, and should never make a sound. The expiry reminder
     * is the opposite: it is worth a glance the moment it arrives, or it
     * arrives the day after the subscription stopped.
     */
    val importance: Int = android.app.NotificationManager.IMPORTANCE_LOW
) {
    SUBSCRIPTION_UPDATE(
        channelId = "subscription_update_channel",
        channelName = "Subscription Update Service",
        notificationId = 13
    ),
    CORE_TEST(
        channelId = "core_test_channel",
        channelName = "Core Test Service",
        notificationId = 12
    ),
    EXPIRY_REMINDER(
        channelId = "expiry_reminder_channel",
        channelName = "Окончание подписки",
        notificationId = 14,
        importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
    ),
    UPDATE_AVAILABLE(
        channelId = "update_available_channel",
        channelName = "Обновление приложения",
        notificationId = 15,
        importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
    ),
    SUPPORT_REPLY(
        channelId = "support_reply_channel",
        channelName = "Ответ поддержки",
        notificationId = 16,
        importance = android.app.NotificationManager.IMPORTANCE_HIGH
    ),
    MESSENGER(
        channelId = "messenger_channel",
        channelName = "Сообщения",
        notificationId = 17,
        importance = android.app.NotificationManager.IMPORTANCE_HIGH
    )
}