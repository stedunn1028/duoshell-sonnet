package com.duoshell.sonnet

import android.service.notification.NotificationListenerService

/**
 * Deliberately empty. Android only lets an app read active media sessions (now-playing title and
 * transport controls) if the user has granted "Notification access". This component exists solely
 * to be that grant target. It never reads, stores or forwards any notification.
 */
class MediaAccessService : NotificationListenerService()
