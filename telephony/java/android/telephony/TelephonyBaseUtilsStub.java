/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package android.telephony;

/**
 * Xiaomi's stock IMS service (org.codeaurora.ims, used for VoLTE and VoNR) asks this class
 * whether it runs on MIUI before it decides on its HD-voice icon. Xiaomi's framework has it;
 * ours does not, so without it the service dies on its first call. MiraCLO is not MIUI.
 *
 * @hide
 */
public final class TelephonyBaseUtilsStub {
    private TelephonyBaseUtilsStub() {}

    /** Always false: this is not MIUI. */
    public static boolean isMiuiRom() {
        return false;
    }
}
