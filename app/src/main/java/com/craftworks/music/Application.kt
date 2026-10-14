package com.craftworks.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.MigrationManager
import com.craftworks.music.managers.OfflineScrobbleManager
import com.craftworks.music.managers.SmartCacheManager
import com.craftworks.music.player.PrefetchedArtworkInterceptor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ChoraApplication : Application(), ImageLoaderFactory {
    @Inject lateinit var smartCacheManager: SmartCacheManager
    @Inject lateinit var offlineScrobbleManager: OfflineScrobbleManager

    override fun onCreate() {
        super.onCreate()
        MigrationManager.init(this)
        MediaProviderManager.init(this)
        smartCacheManager.init()
        offlineScrobbleManager.init()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(PrefetchedArtworkInterceptor(smartCacheManager::getArtworkFile)) }
            .build()
}
