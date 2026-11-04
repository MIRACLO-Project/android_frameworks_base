/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package miui.securityspace;

import android.content.Context;

/**
 * Stand-in for MIUI's second-space helper, used by the stock Xiaomi camera. MIRACLO has no MIUI
 * second space, so no app is granted cross-space access through it.
 *
 * @hide
 */
public class CrossUserUtils {
    public CrossUserUtils() {}

    public static boolean checkUidPermission(Context context, String packageName) {
        return false;
    }
}
