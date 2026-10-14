package com.craftworks.music.data.repository

import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.OfflineManager
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(
    private val offlineManager: OfflineManager
) {

    suspend fun getRadios(): List<MediaModel.InternetRadioStation> = coroutineScope {
        offlineManager.runOrDefault(listOf()) { it.getInternetRadioStations() }
    }

    suspend fun createRadio(name:String, streamUrl:String, homepageUrl:String) {
        offlineManager.runOrDefault(false) { it.createInternetRadioStation(name, streamUrl, homepageUrl) }
    }

    suspend fun modifyRadio(radio: MediaModel.InternetRadioStation) {
        offlineManager.runOrDefault(false, MediaProviderManager.getProvider(radio.providerId)) {
            it.updateInternetRadioStation(radio.id, radio.name, radio.streamUrl, radio.homepageUrl)
        }
    }

    suspend fun deleteRadio(providerId: String, id: String){
        offlineManager.runOrDefault(false, MediaProviderManager.getProvider(providerId)) {
            it.deleteInternetRadioStation(id)
        }
    }
}
