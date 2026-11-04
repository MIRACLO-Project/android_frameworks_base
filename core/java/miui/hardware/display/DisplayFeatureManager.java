/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package miui.hardware.display;

import android.os.IBinder;

/**
 * Stand-in for the MIUI display-effect service the stock Xiaomi camera calls (screen effects while
 * the viewfinder is open). On MIRACLO the display stack handles brightness itself, so every call
 * is accepted and does nothing.
 *
 * @hide
 */
public class DisplayFeatureManager {
    private static final DisplayFeatureManager sInstance = new DisplayFeatureManager();

    private DisplayFeatureManager() {}

    public static DisplayFeatureManager getInstance() {
        return sInstance;
    }

    public void setScreenEffect(int mode, int value) {}

    public void setScreenEffect(int mode, int value, int cookie) {}

    public void setScreenEffect(int mode, int value, int cookie, IBinder token) {}
}
