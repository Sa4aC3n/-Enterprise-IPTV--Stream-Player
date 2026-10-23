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

    fun disableKidsMode(pin: String): Boolean {
        if (pin.trim() == getStoredPin().trim()) {
            prefs.edit().putBoolean("kids_mode_active", false).apply()
            _isKidsModeActive.value = false
            return true
        }
        return false
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
