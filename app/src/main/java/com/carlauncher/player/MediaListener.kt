package com.carlauncher.player

import android.service.notification.NotificationListenerService

/**
 * این سرویس هیچ کاری نمی‌کند؛ فقط وجودش لازم است تا کاربر «دسترسی به نوتیفیکیشن» را فعال کند
 * و لانچر بتواند پخش‌کننده‌های دیگر (Spotify، YouTube Music و ...) را ببیند و کنترل کند.
 */
class MediaListener : NotificationListenerService()
