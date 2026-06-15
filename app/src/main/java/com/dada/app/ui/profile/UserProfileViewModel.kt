package com.dada.app.ui.profile

import com.dada.core.common.base.BaseViewModel
import com.dada.core.network.model.UserProfile
import com.dada.domain.user.repository.ImUserRepository
import com.dada.domain.user.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val imUserRepository: ImUserRepository,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    fun loadUserProfile(userId: Long) {
        runAsync(onError = { e ->
            _uiState.update { it.copy(error = e.message ?: "加载失败") }
        }) {
            _uiState.update { it.copy(isLoading = true) }
            val profile = userProfileRepository.getUserProfile(userId).getOrThrow()
            _uiState.update { it.copy(isLoading = false, profile = profile) }
            syncToLocalDatabase(profile)
        }
    }

    fun updateUsername(userId: Long, username: String) {
        if (username.isBlank()) {
            _uiState.update { it.copy(error = "昵称不能为空") }
            return
        }
        runAsync(onError = { e ->
            _uiState.update { it.copy(isLoading = false, error = e.message ?: "更新失败") }
        }) {
            _uiState.update { it.copy(isLoading = true) }
            userProfileRepository.updateProfile(userId, username).getOrThrow()
            _uiState.update { it.copy(isLoading = false, updateSuccess = "更新成功") }
            loadUserProfile(userId)
        }
    }

    fun uploadAvatar(userId: Long, imageFile: File) {
        runAsync(onError = { e ->
            _uiState.update { it.copy(isLoading = false, error = e.message ?: "上传失败") }
        }) {
            _uiState.update { it.copy(isLoading = true) }
            val avatarUrl = userProfileRepository.uploadAvatar(userId, imageFile).getOrThrow()
            _uiState.update { it.copy(isLoading = false, uploadSuccess = avatarUrl) }
            loadUserProfile(userId)
        }
    }

    fun clearEvent() {
        _uiState.update { it.copy(error = null, uploadSuccess = null, updateSuccess = null) }
    }

    private suspend fun syncToLocalDatabase(profile: UserProfile) {
        val currentProfile = imUserRepository.get()
        imUserRepository.save(
            userId = profile.id,
            deviceId = currentProfile?.deviceId ?: "",
            username = profile.username,
            avatar = profile.avatar,
            coverImage = profile.coverImage,
        )
    }
}

data class UserProfileUiState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val error: String? = null,
    val uploadSuccess: String? = null,
    val updateSuccess: String? = null,
)
