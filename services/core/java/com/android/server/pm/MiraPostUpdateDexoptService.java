/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.pm;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.Slog;

import com.android.server.IoThread;
import com.android.server.SystemService;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Optimizes apps once, soon after the first boot of a new build.
 *
 * <p>An update replaces the system, so the compiled code of every installed app has to be made
 * again. Android does that only while the phone is idle and charging, and until then apps run
 * slower. MIRACLO starts that work two minutes after the first boot of each new build instead, so
 * the phone is smooth from the start. It runs once per build, in the background.
 */
public final class MiraPostUpdateDexoptService extends SystemService {

    private static final String TAG = "MiraPostUpdateDexopt";
    private static final long DELAY_AFTER_BOOT_MS = 2 * 60 * 1000;

    private final File mStamp = new File(Environment.getDataSystemDirectory(),
            "miraclo_dexopt_build");

    public MiraPostUpdateDexoptService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {}

    @Override
    public void onBootPhase(int phase) {
        if (phase != PHASE_BOOT_COMPLETED) return;
        final Handler handler = IoThread.getHandler();
        handler.post(() -> {
            if (Build.FINGERPRINT.equals(readStamp())) return;
            handler.postDelayed(this::optimize, DELAY_AFTER_BOOT_MS);
        });
    }

    private void optimize() {
        try {
            DexOptHelper.getArtManagerLocal().startBackgroundDexoptJob();
            Files.write(mStamp.toPath(), Build.FINGERPRINT.getBytes(StandardCharsets.UTF_8));
            Slog.i(TAG, "Optimizing apps for " + Build.FINGERPRINT);
        } catch (Exception e) {
            Slog.w(TAG, "Could not start app optimization", e);
        }
    }

    private String readStamp() {
        try {
            return new String(Files.readAllBytes(mStamp.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
