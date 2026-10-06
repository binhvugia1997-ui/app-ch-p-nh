package com.aiphotographer.perception

/** Bounded engineering recovery seeds; CALIBRATION_REQUIRED, not a device gate. */
class RecoveryBudget {
    private var attempts = 0
    fun reset() { attempts = 0 }
    fun nextDelayMs(): Long? = if (attempts >= 3) null else 1000L shl attempts++
}
