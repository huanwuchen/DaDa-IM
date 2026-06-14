package com.dada.core.database

interface UserPreferences {
    fun saveToken(token: String)
    fun getToken(): String

    fun saveUserId(id: Long)
    fun getUserId(): Long

    fun saveUserName(name: String?)
    fun getUserName(): String

    fun saveUserAvatar(avatar: String?)
    fun getUserAvatar(): String?

    fun saveUserNickname(nickname: String?)
    fun getUserNickname(): String

    fun saveDeviceId(deviceId: String)
    fun getDeviceId(): String
    fun generateDeviceId(): String

    fun saveCoverImage(coverImage: String?)
    fun getCoverImage(): String?

    fun saveUser(
        userId: Long,
        deviceId: String,
        username: String,
        avatar: String?,
        coverImage: String? = null,
    )

    fun isLoggedIn(): Boolean
    fun clearUserData()
}
