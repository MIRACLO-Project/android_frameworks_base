/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package miui.security;

import android.content.Context;
import android.content.Intent;

/**
 * Stand-in for MIUI's app-lock service ("security"), which the stock Xiaomi camera asks whether it
 * is locked. MIRACLO has no MIUI app lock: nothing is ever locked and every check passes.
 *
 * @hide
 */
public class SecurityManager {
    public SecurityManager(Context context) {}

    public boolean checkAccessControlPass(String packageName) {
        return true;
    }

    public boolean checkAccessControlPass(String packageName, Intent intent) {
        return true;
    }

    public boolean checkAccessControlPassAsUser(String packageName, int userId) {
        return true;
    }

    public boolean getApplicationAccessControlEnabled(String packageName) {
        return false;
    }

    public boolean getApplicationAccessControlEnabledAsUser(String packageName, int userId) {
        return false;
    }
}
