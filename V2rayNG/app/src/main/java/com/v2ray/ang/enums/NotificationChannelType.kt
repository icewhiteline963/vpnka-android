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
    ),

    /**
     * The quiet badge for the service that holds the messenger socket while the
     * app is off screen. Android demands a notification for a foreground
     * service; this one is deliberately as unobtrusive as it is allowed to be.
     */
    MESSENGER_LINK(
        channelId = "messenger_link_channel",
        channelName = "Связь для звонков",
        notificationId = 18,
        importance = android.app.NotificationManager.IMPORTANCE_MIN
    ),

    /**
     * A ringing call. IMPORTANCE_HIGH so it arrives as a heads-up even when the
     * full-screen intent is not granted (Android 14 hands that only to apps the
     * system considers calling apps).
     */
    CALL_INCOMING(
        channelId = "call_incoming_channel",
        channelName = "Входящий звонок",
        notificationId = 19,
        importance = android.app.NotificationManager.IMPORTANCE_HIGH
    )
}