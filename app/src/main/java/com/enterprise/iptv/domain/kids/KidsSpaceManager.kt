package com.enterprise.iptv.domain.kids

import android.content.Context
import android.content.SharedPreferences
import com.enterprise.iptv.domain.model.Channel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KidsSpaceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kids_safe_space_prefs", Context.MODE_PRIVATE)

    private val _isKidsModeActive = MutableStateFlow(prefs.getBoolean("kids_mode_active", false))
    val isKidsModeActive = _isKidsModeActive.asStateFlow()

    private val defaultPin = "0000"
    private var failedAttempts = 0
    private var lockoutUntilMs = 0L

    private fun getStoredPin(): String = prefs.getString("kids_pin", defaultPin) ?: defaultPin

    fun isKidsChannel(channel: Channel): Boolean {
        val cat = (channel.groupTitle).lowercase()
        val name = channel.name.lowercase()

        val keywords = listOf(
            "طفل", "أطفال", "اطفال", "كرتون", "رسوم", "kids", "cartoon",
            "animation", "anime", "children", "براعم", "طيور", "ماجد",
            "spacetoon", "سبيستون", "educational", "تعليم", "توم وجيري", "ديزني", "disney", "nick"
        )
        return keywords.any { cat.contains(it) || name.contains(it) }
    }

    fun enableKidsMode(): Boolean {
        prefs.edit().putBoolean("kids_mode_active", true).apply()
        _isKidsModeActive.value = true
        return true
    }

    fun isLockedOut(): Boolean = System.currentTimeMillis() < lockoutUntilMs

    fun getRemainingLockoutSeconds(): Int {
        val remaining = (lockoutUntilMs - System.currentTimeMillis()) / 1000
        return if (remaining > 0) remaining.toInt() else 0
    }

    fun disableKidsMode(pin: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < lockoutUntilMs) {
            return false // قفل مؤقت ضد التخمين المتكرر
        }

        if (pin.trim() == getStoredPin().trim()) {
            failedAttempts = 0
            lockoutUntilMs = 0L
            prefs.edit().putBoolean("kids_mode_active", false).apply()
            _isKidsModeActive.value = false
            return true
        } else {
            failedAttempts++
            if (failedAttempts >= 5) {
                lockoutUntilMs = now + 30_000L // حظر لمدة 30 ثانية بعد 5 محاولات متتالية خاطئة
                failedAttempts = 0
            }
            return false
        }
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        if (oldPin.trim() == getStoredPin().trim() && newPin.trim().length == 4) {
            prefs.edit().putString("kids_pin", newPin.trim()).apply()
            return true
        }
        return false
    }

    fun getPin(): String = getStoredPin()

    fun restorePin(pin: String) {
        if (pin.trim().length == 4) {
            prefs.edit().putString("kids_pin", pin.trim()).apply()
        }
    }
}
