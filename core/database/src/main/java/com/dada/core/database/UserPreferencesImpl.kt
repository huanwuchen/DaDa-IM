package com.dada.core.database

import com.dada.core.common.utils.KvUtil
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesImpl @Inject constructor() : UserPreferences {

    override fun saveToken(token: String) {
        KvUtil.putString("access_token", token)
    }

    override fun getToken(): String {
        return KvUtil.getString("access_token")
    }

    override fun saveUserId(id: Long) {
        KvUtil.putLong("user_id", id)
    }

    override fun getUserId(): Long {
        return KvUtil.getLong("user_id")
    }

    override fun saveUserName(name: String?) {
        KvUtil.putString("user_name", name ?: "")
    }

    override fun getUserName(): String {
        return KvUtil.getString("user_name")
    }

    override fun saveUserAvatar(avatar: String?) {
        KvUtil.putString("user_avatar", avatar ?: "")
    }

    override fun getUserAvatar(): String? {
        val v = KvUtil.getString("user_avatar")
        return v.ifBlank { null }
    }

    override fun saveUserNickname(nickname: String?) {
        KvUtil.putString("user_nickname", nickname ?: "")
    }

    override fun getUserNickname(): String {
        return KvUtil.getString("user_nickname")
    }

    override fun saveDeviceId(deviceId: String) {
        KvUtil.putString("device_id", deviceId)
    }

    override fun getDeviceId(): String {
        return KvUtil.getString("device_id")
    }

    override fun generateDeviceId(): String {
        var deviceId = getDeviceId()
        if (deviceId.isEmpty()) {
            deviceId = try {
                "client-${UUID.randomUUID()}"
            } catch (_: Exception) {
                "client-${System.currentTimeMillis()}-${(Math.random() * 1_000_000).toInt()}"
            }
            saveDeviceId(deviceId)
        }
        return deviceId
    }

    override fun saveCoverImage(coverImage: String?) {
        if (coverImage != null) {
            KvUtil.putString("cover_image", coverImage)
        }
    }

    override fun getCoverImage(): String? {
        val v = KvUtil.getString("cover_image")
        return v.ifBlank { null }
    }

    override fun saveUser(
        userId: Long,
        deviceId: String,
        username: String,
        avatar: String?,
        coverImage: String?,
    ) {
        saveUserId(userId)
        saveDeviceId(deviceId)
        saveUserName(username)
        if (avatar != null) saveUserAvatar(avatar)
        if (coverImage != null) saveCoverImage(coverImage)
    }

    override fun isLoggedIn(): Boolean {
        return getUserId() > 0
    }

    override fun clearUserData() {
        KvUtil.remove("access_token")
        KvUtil.remove("user_id")
        KvUtil.remove("user_name")
        KvUtil.remove("user_avatar")
        KvUtil.remove("user_nickname")
        KvUtil.remove("device_id")
        KvUtil.remove("cover_image")
    }
}
