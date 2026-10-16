package com.notchbuddy.android.settings

import android.content.Context
import android.content.SharedPreferences
import com.notchbuddy.android.model.ReactionSource
import com.notchbuddy.android.model.SpeedUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        "notch_buddy_prefs",
        Context.MODE_PRIVATE
    )

    private val _reactionSource = MutableStateFlow(
        ReactionSource.valueOf(
            prefs.getString(KEY_REACTION_SOURCE, ReactionSource.INTERNET_SPEED.name)
                ?: ReactionSource.INTERNET_SPEED.name
        )
    )
    val reactionSource: StateFlow<ReactionSource> = _reactionSource.asStateFlow()

    private val _speedUnit = MutableStateFlow(
        SpeedUnit.valueOf(
            prefs.getString(KEY_SPEED_UNIT, SpeedUnit.BYTES.name) ?: SpeedUnit.BYTES.name
        )
    )
    val speedUnit: StateFlow<SpeedUnit> = _speedUnit.asStateFlow()

    // Haptic feedback defaults to FALSE as requested. Can be toggled in settings.
    private val _hapticFeedbackEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_HAPTIC_ENABLED, false)
    )
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    private val _haloEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_HALO_ENABLED, true)
    )
    val haloEnabled: StateFlow<Boolean> = _haloEnabled.asStateFlow()

    private val _companionActive = MutableStateFlow(
        prefs.getBoolean(KEY_COMPANION_ACTIVE, true)
    )
    val companionActive: StateFlow<Boolean> = _companionActive.asStateFlow()

    private val _notchRingActive = MutableStateFlow(
        prefs.getBoolean(KEY_NOTCH_RING_ACTIVE, true)
    )
    val notchRingActive: StateFlow<Boolean> = _notchRingActive.asStateFlow()

    private val _companionScale = MutableStateFlow(
        prefs.getFloat(KEY_COMPANION_SCALE, 1.0f)
    )
    val companionScale: StateFlow<Float> = _companionScale.asStateFlow()

    private val _happyThreshold = MutableStateFlow(
        prefs.getFloat(KEY_HAPPY_THRESHOLD, 50f)
    )
    val happyThreshold: StateFlow<Float> = _happyThreshold.asStateFlow()

    private val _focusThreshold = MutableStateFlow(
        prefs.getFloat(KEY_FOCUS_THRESHOLD, 500f)
    )
    val focusThreshold: StateFlow<Float> = _focusThreshold.asStateFlow()

    private val _strainThreshold = MutableStateFlow(
        prefs.getFloat(KEY_STRAIN_THRESHOLD, 2000f)
    )
    val strainThreshold: StateFlow<Float> = _strainThreshold.asStateFlow()

    fun setReactionSource(source: ReactionSource) {
        prefs.edit().putString(KEY_REACTION_SOURCE, source.name).apply()
        _reactionSource.value = source
    }

    fun setSpeedUnit(unit: SpeedUnit) {
        prefs.edit().putString(KEY_SPEED_UNIT, unit.name).apply()
        _speedUnit.value = unit
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
        _hapticFeedbackEnabled.value = enabled
    }

    fun setCompanionActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_COMPANION_ACTIVE, active).apply()
        _companionActive.value = active
    }

    fun setNotchRingActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_NOTCH_RING_ACTIVE, active).apply()
        _notchRingActive.value = active
    }

    fun setHaloEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HALO_ENABLED, enabled).apply()
        _haloEnabled.value = enabled
    }

    fun setCompanionScale(scale: Float) {
        prefs.edit().putFloat(KEY_COMPANION_SCALE, scale).apply()
        _companionScale.value = scale
    }

    fun setThresholds(happy: Float, focus: Float, strain: Float) {
        prefs.edit()
            .putFloat(KEY_HAPPY_THRESHOLD, happy)
            .putFloat(KEY_FOCUS_THRESHOLD, focus)
            .putFloat(KEY_STRAIN_THRESHOLD, strain)
            .apply()
        _happyThreshold.value = happy
        _focusThreshold.value = focus
        _strainThreshold.value = strain
    }

    private val _companionPosX = MutableStateFlow(
        prefs.getInt(KEY_COMPANION_POS_X, -1)
    )
    val companionPosX: StateFlow<Int> = _companionPosX.asStateFlow()

    private val _companionPosY = MutableStateFlow(
        prefs.getInt(KEY_COMPANION_POS_Y, -1)
    )
    val companionPosY: StateFlow<Int> = _companionPosY.asStateFlow()

    fun setCompanionPosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_COMPANION_POS_X, x).putInt(KEY_COMPANION_POS_Y, y).apply()
        _companionPosX.value = x
        _companionPosY.value = y
    }

    companion object {
        private const val KEY_REACTION_SOURCE = "key_reaction_source"
        private const val KEY_SPEED_UNIT = "key_speed_unit"
        private const val KEY_HAPTIC_ENABLED = "key_haptic_enabled"
        private const val KEY_HALO_ENABLED = "key_halo_enabled"
        private const val KEY_COMPANION_ACTIVE = "key_companion_active"
        private const val KEY_NOTCH_RING_ACTIVE = "key_notch_ring_active"
        private const val KEY_COMPANION_SCALE = "key_companion_scale"
        private const val KEY_HAPPY_THRESHOLD = "key_happy_threshold"
        private const val KEY_FOCUS_THRESHOLD = "key_focus_threshold"
        private const val KEY_STRAIN_THRESHOLD = "key_strain_threshold"
        private const val KEY_COMPANION_POS_X = "key_companion_pos_x"
        private const val KEY_COMPANION_POS_Y = "key_companion_pos_y"

        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context).also { INSTANCE = it }
            }
        }
    }
}
