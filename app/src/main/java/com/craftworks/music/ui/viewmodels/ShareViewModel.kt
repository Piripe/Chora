package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.craftworks.music.data.model.ShareExpiration
import com.craftworks.music.data.providers.media.MediaProvider
import com.craftworks.music.data.repository.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val shareRepository: ShareRepository
) : ViewModel() {

    /** See [ShareRepository.share]. */
    suspend fun share(provider: MediaProvider, ids: List<String>, description: String?, expiration: ShareExpiration): String? =
        shareRepository.share(provider, ids, description, expiration)
}
