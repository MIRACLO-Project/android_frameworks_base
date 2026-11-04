/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package miui.util;

import android.content.Context;
import android.net.Uri;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Stand-in for MIUI's haptics helper, used by the stock Xiaomi camera (shutter, mode dial, zoom
 * steps). MIUI's own effects ("ext" effects) are not available, so the camera falls back to its
 * plain feedback, which is played here as Android's standard tick.
 *
 * @hide
 */
public class HapticFeedbackUtil {
    private final Vibrator mVibrator;

    public HapticFeedbackUtil(Context context, boolean onlyWhenEnabled) {
        mVibrator = context != null ? context.getSystemService(Vibrator.class) : null;
    }

    public static boolean isSupportLinearMotorVibrate() {
        return false;
    }

    public static boolean isSupportLinearMotorVibrate(int effect) {
        return false;
    }

    public boolean isSupportExtHapticFeedback(int effect) {
        return false;
    }

    public boolean isSupportedEffect(int effect) {
        return false;
    }

    private boolean tick() {
        try {
            if (mVibrator == null || !mVibrator.hasVibrator()) return false;
            mVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // MIUI-only effects: not available.
    public boolean performExtHapticFeedback(int effect) { return false; }
    public boolean performExtHapticFeedback(int effect, double strength, String tag) { return false; }
    public boolean performExtHapticFeedback(int effect, int strength) { return false; }
    public boolean performExtHapticFeedback(int effect, int strength, boolean always) { return false; }
    public boolean performExtHapticFeedback(int effect, boolean always) { return false; }
    public boolean performExtHapticFeedback(Uri effect) { return false; }
    public boolean performExtHapticFeedback(Uri effect, boolean always) { return false; }
    public boolean performExtHapticFeedback(VibrationAttributes attrs, int effect) { return false; }
    public boolean performExtHapticFeedback(VibrationAttributes attrs, int effect, boolean always) { return false; }
    public boolean performExtHapticFeedback(VibrationAttributes attrs, int effect, double strength, String tag) { return false; }
    public boolean performExtHapticFeedback(VibrationAttributes attrs, Uri effect, boolean always) { return false; }

    // Plain feedback: Android's standard tick.
    public boolean performHapticFeedback(int effect, double strength, String tag) { return tick(); }
    public boolean performHapticFeedback(int effect, boolean always) { return tick(); }
    public boolean performHapticFeedback(int effect, boolean always, int usage) { return tick(); }
    public boolean performHapticFeedback(VibrationAttributes attrs, int effect, double strength, String tag) { return tick(); }
    public boolean performHapticFeedback(VibrationAttributes attrs, int effect, boolean always) { return tick(); }
    public boolean performHapticFeedback(VibrationAttributes attrs, int effect, boolean always, int usage) { return tick(); }
    public boolean performHapticFeedback(VibrationAttributes attrs, String effect, boolean always) { return tick(); }
    public boolean performHapticFeedback(VibrationAttributes attrs, String effect, boolean always, int usage) { return tick(); }
    public boolean performHapticFeedback(String effect, int strength, boolean always) { return tick(); }
    public boolean performHapticFeedback(String effect, boolean always) { return tick(); }
    public boolean performHapticFeedback(String effect, boolean always, int usage) { return tick(); }

    public void release() {}

    public void stop() {}
}
