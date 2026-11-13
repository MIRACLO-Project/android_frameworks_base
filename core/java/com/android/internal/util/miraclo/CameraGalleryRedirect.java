/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.util.miraclo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.MediaStore;

/**
 * The stock Xiaomi camera sends its thumbnail and "open gallery" taps to Xiaomi Gallery
 * (com.miui.gallery) by package name. MIRACLO has no Xiaomi Gallery, so those taps did nothing.
 * When Xiaomi Gallery is not installed, the camera's gallery intents go to the gallery the user
 * has instead (Google Photos or any other), in a task of its own.
 *
 * @hide
 */
public final class CameraGalleryRedirect {

    private static final String CAMERA = "com.android.camera";
    private static final String XIAOMI_GALLERY = "com.miui.gallery";
    private static final String CAMERA_REVIEW = "com.android.camera.action.REVIEW";

    private CameraGalleryRedirect() {}

    /** Rewrites {@code intent} in place when the stock camera targets the missing gallery. */
    public static void maybeRedirect(Context who, Intent intent) {
        if (who == null || intent == null || !XIAOMI_GALLERY.equals(intent.getPackage())
                || !CAMERA.equals(who.getPackageName())) {
            return;
        }
        final PackageManager pm = who.getPackageManager();
        try {
            pm.getPackageInfo(XIAOMI_GALLERY, 0);
            return; // Xiaomi Gallery is installed: leave the camera as it is.
        } catch (PackageManager.NameNotFoundException e) {
            // Not installed: send it to the user's gallery below.
        }

        intent.setPackage(null);
        final String action = intent.getAction();
        if (CAMERA_REVIEW.equals(action)) {
            // Review a photo or video just taken: the standard review action, or viewing it
            // when no gallery offers review.
            intent.setAction(MediaStore.ACTION_REVIEW);
            if (intent.resolveActivity(pm) == null) {
                intent.setAction(Intent.ACTION_VIEW);
            }
        } else if (Intent.ACTION_MAIN.equals(action) || action == null) {
            // Open the gallery itself.
            intent.setAction(Intent.ACTION_MAIN);
            intent.setSelector(
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}
