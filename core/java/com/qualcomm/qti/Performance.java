/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.qualcomm.qti;

import android.content.Context;
import android.util.Log;

/**
 * MIRACLO's open implementation of the class Android's BoostFramework looks for. It passes touch,
 * scroll, launch and animation boosts to Qualcomm's perf service (libqti-perfd-client_system.so
 * from the stock firmware) through libmiraperf_jni. Where the service or library is missing
 * (the emulator), every call does nothing and returns "not handled".
 *
 * @hide
 */
public class Performance {
    private static final String TAG = "MiraPerf";
    private static volatile int sState; // 0 not tried, 1 ready, 2 unavailable
    private int mLastHandle = -1;

    /** Loads the bridge on first use, never inside the zygote (it starts binder threads). */
    private static boolean ready() {
        int st = sState;
        if (st != 0) return st == 1;
        if (android.os.Process.myUid() == 0) return false;
        synchronized (Performance.class) {
            if (sState == 0) {
                boolean ok;
                try {
                    System.loadLibrary("miraperf_jni");
                    ok = native_init();
                } catch (Throwable t) {
                    ok = false;
                }
                sState = ok ? 1 : 2;
                if (!ok) Log.i(TAG, "perf service not available; boosts are off");
            }
            return sState == 1;
        }
    }

    public Performance() {}

    public Performance(Context context) {}

    public Performance(boolean isUntrustedDomain) {}

    public int perfLockAcquire(int duration, int... list) {
        if (!ready()) return -1;
        int h = native_lock_acq(0, duration, list);
        if (h > 0) mLastHandle = h;
        return h;
    }

    public int perfLockRelease() {
        if (!ready() || mLastHandle <= 0) return -1;
        int r = native_lock_rel(mLastHandle);
        mLastHandle = -1;
        return r;
    }

    public int perfLockReleaseHandler(int handle) {
        return ready() ? native_lock_rel(handle) : -1;
    }

    public int perfHint(int hint, String pkg, int duration, int type) {
        if (!ready()) return -1;
        int h = native_hint(hint, pkg, duration, type);
        if (h > 0) mLastHandle = h;
        return h;
    }

    public int perfHintRelease() {
        return perfLockRelease();
    }

    public int perfGetFeedback(int req, String pkg) {
        return ready() ? native_get_feedback(req, pkg) : -1;
    }

    public int perfGetFeedbackExtn(int req, String pkg, int numArgs, int... list) {
        return ready() ? native_get_feedback(req, pkg) : -1;
    }

    public int perfIOPrefetchStart(int pid, String pkgName, String codePath) {
        return -1;
    }

    public int perfIOPrefetchStop() {
        return -1;
    }

    public String perfGetProp(String prop, String defVal) {
        return defVal;
    }

    public int perfLockAcqAndRelease(int handle, int duration, int numArgs, int reserveNumArgs,
            int... list) {
        if (!ready()) return -1;
        return native_lock_acq(handle, duration, list);
    }

    public void perfEvent(int eventId, String pkg, int numArgs, int... list) {
        if (ready()) native_event(eventId, pkg, list);
    }

    public String perfSyncRequest(int opcode) {
        return null;
    }

    public int perfHintAcqRel(int handle, int hint, String pkg, int duration, int type,
            int numArgs, int... list) {
        return ready() ? native_hint_acq_rel(handle, hint, pkg, duration, type, list) : -1;
    }

    public int perfHintRenew(int handle, int hint, String pkg, int duration, int type,
            int numArgs, int... list) {
        return ready() ? native_hint_acq_rel(handle, hint, pkg, duration, type, list) : -1;
    }

    public double perfGetHalVer() {
        return ready() ? 2.3 : 0.0;
    }

    public int perfUXEngine_events(int opcode, int pid, String pkgName, int lat, String codePath) {
        return -1;
    }

    public String perfUXEngine_trigger(int opcode) {
        return null;
    }

    private static native boolean native_init();
    private static native int native_lock_acq(int handle, int duration, int[] list);
    private static native int native_lock_rel(int handle);
    private static native int native_hint(int hint, String pkg, int duration, int type);
    private static native int native_get_feedback(int req, String pkg);
    private static native void native_event(int eventId, String pkg, int[] list);
    private static native int native_hint_acq_rel(int handle, int hint, String pkg, int duration,
            int type, int[] list);
}
