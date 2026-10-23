package com.enterprise.iptv.domain.backup

import android.content.Context
import android.net.Uri
import com.enterprise.iptv.data.local.dao.ChannelDao
import com.enterprise.iptv.data.local.entity.ChannelEntity
import com.enterprise.iptv.domain.kids.KidsSpaceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * حالات نتيجة عملية النسخ الاحتياطي والاستعادة
 */
sealed class BackupOperationResult {
    data class Success(val message: String, val restoredFavoritesCount: Int) : BackupOperationResult()
    data class Error(val errorMessage: String) : BackupOperationResult()
}

/**
 * مدير النسخ الاحتياطي والاستعادة الشامل (Backup & Restore Manager)
 * يتيح تصدير واسترجاع القنوات المفضلة، وإعدادات المشغل عبر بروتوكول SAF الرسمي بنظام أندرويد
 */
@Singleton
class BackupRestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: ChannelDao,
    private val kidsSpaceManager: KidsSpaceManager
) {
    private val _lastOperationResult = MutableStateFlow<BackupOperationResult?>(null)
    val lastOperationResult = _lastOperationResult.asStateFlow()

    fun clearResult() {
        _lastOperationResult.value = null
    }

    /**
     * حساب إجمالي القنوات المفضلة الحالية
     */
    suspend fun getCurrentFavoritesCount(): Int = withContext(Dispatchers.IO) {
        try {
            channelDao.getFavoritesCount()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * إنشاء كائن النسخة الاحتياطية الحالية
     */
    suspend fun createBackupPayload(): BackupPayload = withContext(Dispatchers.IO) {
        val favorites = channelDao.getFavoriteChannels().map { entity ->
            ChannelBackupItem(
                name = entity.name,
                streamUrl = entity.streamUrl,
                backupUrls = if (entity.backupUrls.isNullOrBlank()) emptyList() else entity.backupUrls.split("||").filter { it.isNotBlank() },
                logoUrl = entity.logoUrl,
                groupTitle = entity.groupTitle,
                countryCode = entity.countryCode,
                isFavorite = entity.isFavorite
            )
        }

        val settings = AppSettingsBackup(
            isKidsModeEnabled = kidsSpaceManager.isKidsModeActive.value,
            kidsPin = kidsSpaceManager.getPin()
        )

        BackupPayload(
            favorites = favorites,
            settings = settings
        )
    }

    /**
     * تصدير النسخة الاحتياطية إلى مسار URI محدد من قِبل المستخدم (عبر SAF)
     */
    suspend fun exportBackup(destinationUri: Uri): BackupOperationResult = withContext(Dispatchers.IO) {
        try {
            val payload = createBackupPayload()
            val jsonString = payload.toJsonString()

            context.contentResolver.openOutputStream(destinationUri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.write(jsonString)
                    writer.flush()
                }
            } ?: run {
                val err = BackupOperationResult.Error("تعذر الوصول إلى مسار الحفظ المختار")
                _lastOperationResult.value = err
                return@withContext err
            }

            val success = BackupOperationResult.Success(
                message = "تم تصدير النسخة الاحتياطية بنجاح (${payload.favorites.size} قنوات مفضلة)",
                restoredFavoritesCount = payload.favorites.size
            )
            _lastOperationResult.value = success
            success
        } catch (e: Exception) {
            val err = BackupOperationResult.Error("خطأ أثناء كتابة النسخة الاحتياطية: ${e.localizedMessage}")
            _lastOperationResult.value = err
            err
        }
    }

    /**
     * استعادة النسخة الاحتياطية من ملف JSON محدد من قِبل المستخدم (عبر SAF)
     */
    suspend fun restoreBackup(sourceUri: Uri): BackupOperationResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: run {
                val err = BackupOperationResult.Error("تعذر قراءة ملف النسخة الاحتياطية المحدد")
                _lastOperationResult.value = err
                return@withContext err
            }

            val payload = BackupPayload.fromJsonString(jsonString)

            var restoredCount = 0

            // استعادة وتطبيق القنوات المفضلة
            payload.favorites.forEach { fav ->
                val updated = channelDao.updateFavoriteByStreamOrName(fav.streamUrl, fav.name, true)
                if (updated > 0) {
                    restoredCount += updated
                } else {
                    val newEntity = ChannelEntity(
                        id = UUID.randomUUID().toString(),
                        name = fav.name,
                        streamUrl = fav.streamUrl,
                        backupUrls = if (fav.backupUrls.isEmpty()) null else fav.backupUrls.joinToString("||"),
                        logoUrl = fav.logoUrl,
                        groupTitle = fav.groupTitle,
                        countryCode = fav.countryCode,
                        languageCode = null,
                        isFavorite = true,
                        playlistId = "restored_backup"
                    )
                    channelDao.insertChannels(listOf(newEntity))
                    restoredCount++
                }
            }

            // استعادة وتطبيق إعدادات فضاء الأطفال إن وُجدت
            if (payload.settings.kidsPin.length == 4) {
                kidsSpaceManager.restorePin(payload.settings.kidsPin)
            }
            if (payload.settings.isKidsModeEnabled) {
                kidsSpaceManager.enableKidsMode()
            }

            val success = BackupOperationResult.Success(
                message = "تمت استعادة $restoredCount قناة مفضلة واسترجاع الإعدادات بنجاح",
                restoredFavoritesCount = restoredCount
            )
            _lastOperationResult.value = success
            success
        } catch (e: Exception) {
            val err = BackupOperationResult.Error("الملف المحدد غير متوافق أو تالف: ${e.localizedMessage}")
            _lastOperationResult.value = err
            err
        }
    }
}
