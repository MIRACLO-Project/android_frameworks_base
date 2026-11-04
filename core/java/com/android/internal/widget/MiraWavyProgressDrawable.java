/*
 * Copyright (C) 2026 MIRACLO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.widget;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.PathInterpolator;

/**
 * MIRACLO: Material 3 Expressive progress bars for Android's own horizontal ProgressBar.
 *
 * <p>The played part is a moving wave while the task runs; at 100% (or 0%) it flattens smoothly.
 * The rest of the bar is a flat track after a small gap, with a dot at its end. The layers keep
 * the ids and types apps expect (background, secondaryProgress, progress as a ScaleDrawable), so
 * tints and code that reach into the layers keep working; only the drawing changes.
 *
 * <p>Turned off with {@code setprop persist.sys.miraclo.wavy_progress false}.
 */
public class MiraWavyProgressDrawable extends LayerDrawable {

    /** True unless turned off with the system property. */
    public static boolean isEnabled() {
        try {
            return SystemProperties.getBoolean("persist.sys.miraclo.wavy_progress", true);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A layer that only carries a level and a tint; the parent draws everything. */
    static final class Level extends Drawable {
        ColorStateList mTint;

        @Override
        public void draw(Canvas canvas) {
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setTintList(ColorStateList tint) {
            mTint = tint;
            invalidateSelf();
        }

        @Override
        public void setTintBlendMode(BlendMode blendMode) {
        }

        @Override
        protected boolean onLevelChange(int level) {
            invalidateSelf();
            return true;
        }

        int color(int[] state, int fallback) {
            return mTint != null ? mTint.getColorForState(state, fallback) : fallback;
        }
    }

    /** Draws the wave and the flat track; shared by the determinate and indeterminate bars. */
    static final class Painter {
        final float mDensity;
        final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path mPath = new Path();

        Painter(float density) {
            mDensity = density;
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        float dp(float v) {
            return v * mDensity;
        }

        /** Stroke width that fits the bounds. */
        float stroke(Rect b) {
            return Math.max(1f, Math.min(dp(4f), b.height()));
        }

        /** Wave height that fits the bounds (0 when the bar is too thin). */
        float maxAmp(Rect b, float stroke) {
            return Math.max(0f, Math.min(dp(3f), (b.height() - stroke) / 2f));
        }

        void wave(Canvas c, float from, float to, float cy, float amp, float phase, float stroke,
                int color, int alpha) {
            if (to - from < 0.5f) return;
            final float length = dp(36f);
            mPath.rewind();
            final double k = 2 * Math.PI / length;
            float x = from;
            mPath.moveTo(x, (float) (cy + amp * Math.sin((x - phase * length) * k)));
            final float step = Math.max(1f, dp(1.5f));
            while (x < to) {
                x = Math.min(to, x + step);
                mPath.lineTo(x, (float) (cy + amp * Math.sin((x - phase * length) * k)));
            }
            mPaint.setColor(color);
            mPaint.setAlpha(Color.alpha(color) * alpha / 255);
            mPaint.setStrokeWidth(stroke);
            c.drawPath(mPath, mPaint);
        }

        void line(Canvas c, float from, float to, float cy, float stroke, int color, int alpha) {
            if (to - from < 0.5f) return;
            mPaint.setColor(color);
            mPaint.setAlpha(Color.alpha(color) * alpha / 255);
            mPaint.setStrokeWidth(stroke);
            c.drawLine(from, cy, to, cy, mPaint);
        }

        void dot(Canvas c, float x, float cy, float stroke, int color, int alpha) {
            mPaint.setColor(color);
            mPaint.setAlpha(Color.alpha(color) * alpha / 255);
            mPaint.setStrokeWidth(stroke);
            c.drawPoint(x, cy, mPaint);
        }
    }

    /** The theme's progress colour (colorControlActivated). */
    static int themeColor(Context context) {
        TypedArray a = context.obtainStyledAttributes(
                new int[] { android.R.attr.colorControlActivated });
        try {
            return a.getColor(0, 0xFF0B57D0);
        } finally {
            a.recycle();
        }
    }

    private final Painter mPainter;
    private final int mActive;
    private int mAlpha = 255;
    private float mAmp = 0f;
    private long mLastFrame = 0L;
    private float mPhase = 0f;

    public MiraWavyProgressDrawable(Context context) {
        this(new Drawable[] {
                new Level(),
                new ScaleDrawable(new Level(), Gravity.START, 1f, -1f),
                new ScaleDrawable(new Level(), Gravity.START, 1f, -1f),
        }, context);
    }

    private MiraWavyProgressDrawable(Drawable[] layers, Context context) {
        super(layers);
        setId(0, android.R.id.background);
        setId(1, android.R.id.secondaryProgress);
        setId(2, android.R.id.progress);
        mPainter = new Painter(context.getResources().getDisplayMetrics().density);
        mActive = themeColor(context);
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
        super.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return mAlpha;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    private static int levelOf(Drawable d) {
        return d != null ? d.getLevel() : 0;
    }

    private static Level levelLayer(Drawable d) {
        if (d instanceof Level) return (Level) d;
        if (d instanceof ScaleDrawable && ((ScaleDrawable) d).getDrawable() instanceof Level) {
            return (Level) ((ScaleDrawable) d).getDrawable();
        }
        return null;
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect b = getBounds();
        if (b.isEmpty()) return;
        final Drawable progress = findDrawableByLayerId(android.R.id.progress);
        final Drawable background = findDrawableByLayerId(android.R.id.background);
        final int level = levelOf(progress);
        final float f = Math.max(0f, Math.min(1f, level / 10000f));
        final int[] state = getState();
        final Level pl = levelLayer(progress);
        final Level bl = levelLayer(background);
        final int active = pl != null ? pl.color(state, mActive) : mActive;
        final int track = bl != null && bl.mTint != null
                ? bl.color(state, mActive)
                : (active & 0x00FFFFFF) | (0x3D << 24);

        final Painter p = mPainter;
        final float stroke = p.stroke(b);
        final float cy = b.exactCenterY();
        final float start = b.left + stroke / 2f;
        final float end = b.right - stroke / 2f;

        // the wave eases in while running and flattens at 0% and 100%
        final long now = SystemClock.uptimeMillis();
        final float dt = mLastFrame == 0L ? 0f : Math.min(0.05f, (now - mLastFrame) / 1000f);
        mLastFrame = now;
        final boolean running = level > 0 && level < 10000 && isVisible();
        final float target = running ? p.maxAmp(b, stroke) : 0f;
        mAmp += (target - mAmp) * Math.min(1f, dt * 6f);
        if (Math.abs(target - mAmp) < 0.05f) mAmp = target;
        if (running) mPhase = (mPhase + dt * 0.9f) % 1f;

        final float x = start + (end - start) * f;
        final float gap = p.dp(4f) + stroke / 2f;
        // (ProgressBar mirrors the canvas itself for right-to-left layouts)
        p.wave(canvas, start, x, cy, mAmp, mPhase, stroke, active, mAlpha);
        if (level < 10000) {
            final float ts = level > 0 ? Math.min(end, x + gap) : start;
            p.line(canvas, ts, end, cy, stroke, track, mAlpha);
            p.dot(canvas, end, cy, stroke, active, mAlpha);
        }

        if (running || mAmp > 0f) invalidateSelf();
        else mLastFrame = 0L;
    }

    /**
     * The round loading spinner: a wavy arc that grows, shrinks and turns, as Material 3
     * Expressive draws it (no track).
     */
    public static class Spinner extends Drawable implements Animatable {
        private static final TimeInterpolator EASE = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
        /** One grow-and-shrink of the arc. */
        private static final long CYCLE = 1333L;
        /** One full turn of the whole spinner. */
        private static final long TURN = 1568L;
        /** How far the arc moves on at each cycle, in degrees. */
        private static final float STEP = 250f;

        private final Painter mPainter;
        private final int mThemeColor;
        private ColorStateList mTint;
        private int mAlpha = 255;
        private boolean mRunning;
        private long mStart;

        public Spinner(Context context) {
            mPainter = new Painter(context.getResources().getDisplayMetrics().density);
            mThemeColor = themeColor(context);
        }

        @Override
        public void start() {
            if (mRunning) return;
            mRunning = true;
            mStart = SystemClock.uptimeMillis();
            invalidateSelf();
        }

        @Override
        public void stop() {
            mRunning = false;
        }

        @Override
        public boolean isRunning() {
            return mRunning;
        }

        @Override
        public void setTintList(ColorStateList tint) {
            mTint = tint;
            invalidateSelf();
        }

        @Override
        public void setTintBlendMode(BlendMode blendMode) {
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            invalidateSelf();
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            mAlpha = alpha;
            invalidateSelf();
        }

        @Override
        public int getAlpha() {
            return mAlpha;
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void draw(Canvas canvas) {
            final Rect b = getBounds();
            if (b.isEmpty()) return;
            final Painter p = mPainter;
            final int color = mTint != null
                    ? mTint.getColorForState(getState(), mThemeColor) : mThemeColor;
            final float size = Math.min(b.width(), b.height());
            // stroke and wave scale with the spinner (4dp and 1.2dp at 48dp)
            final float stroke = Math.max(1f, size / 12f);
            final float amp = size / 40f;
            final float r = size / 2f - stroke / 2f - amp - size * 0.06f;
            if (r <= 0) return;
            final float cx = b.exactCenterX();
            final float cy = b.exactCenterY();

            final long t = mRunning ? SystemClock.uptimeMillis() - mStart : 0L;
            final long cycleIndex = t / CYCLE;
            final float f = (t % CYCLE) / (float) CYCLE;
            final float head = EASE.getInterpolation(Math.min(1f, f * 2f)) * STEP;
            final float tail = EASE.getInterpolation(Math.max(0f, f * 2f - 1f)) * STEP;
            final float turn = (t % TURN) / (float) TURN * 360f;
            final float startDeg = turn + (cycleIndex % 36) * STEP + tail - 90f;
            final float sweep = 18f + head - tail;

            // the wave: about one crest every 12dp of the ring, moving round as it turns
            final float waves = Math.max(5f, Math.round((float) (2 * Math.PI * r) / p.dp(12f)));
            final float phase = (t % 900L) / 900f * 360f;
            p.mPath.rewind();
            final float stepDeg = 3f;
            for (float a = 0f; a <= sweep + 0.001f; a += stepDeg) {
                final float deg = startDeg + Math.min(a, sweep);
                final double rad = Math.toRadians(deg);
                final double wr = Math.toRadians(deg * waves - phase);
                final float rr = (float) (r + amp * Math.sin(wr));
                final float x = (float) (cx + rr * Math.cos(rad));
                final float y = (float) (cy + rr * Math.sin(rad));
                if (a == 0f) p.mPath.moveTo(x, y);
                else p.mPath.lineTo(x, y);
            }
            p.mPaint.setColor(color);
            p.mPaint.setAlpha(Color.alpha(color) * mAlpha / 255);
            p.mPaint.setStrokeWidth(stroke);
            canvas.drawPath(p.mPath, p.mPaint);
            if (mRunning && isVisible()) invalidateSelf();
        }
    }

    /**
     * The indeterminate bar: a wavy piece travels along the flat track, speeding up and slowing
     * down as Material 3 draws it.
     */
    public static class Indeterminate extends Drawable implements Animatable {
        private static final TimeInterpolator EASE = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
        private static final long CYCLE = 1800L;

        private final Painter mPainter;
        private final int mThemeColor;
        private ColorStateList mTint;
        private int mAlpha = 255;
        private boolean mRunning;
        private long mStart;

        public Indeterminate(Context context) {
            mPainter = new Painter(context.getResources().getDisplayMetrics().density);
            mThemeColor = themeColor(context);
        }

        @Override
        public void start() {
            if (mRunning) return;
            mRunning = true;
            mStart = SystemClock.uptimeMillis();
            invalidateSelf();
        }

        @Override
        public void stop() {
            mRunning = false;
        }

        @Override
        public boolean isRunning() {
            return mRunning;
        }

        @Override
        public void setTintList(ColorStateList tint) {
            mTint = tint;
            invalidateSelf();
        }

        @Override
        public void setTintBlendMode(BlendMode blendMode) {
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            invalidateSelf();
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            mAlpha = alpha;
            invalidateSelf();
        }

        @Override
        public int getAlpha() {
            return mAlpha;
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void draw(Canvas canvas) {
            final Rect b = getBounds();
            if (b.isEmpty()) return;
            final Painter p = mPainter;
            final int active = mTint != null
                    ? mTint.getColorForState(getState(), mThemeColor) : mThemeColor;
            final int track = (active & 0x00FFFFFF) | (0x3D << 24);
            final float stroke = p.stroke(b);
            final float cy = b.exactCenterY();
            final float start = b.left + stroke / 2f;
            final float end = b.right - stroke / 2f;
            final float w = end - start;
            final long now = SystemClock.uptimeMillis();
            final float t = ((now - mStart) % CYCLE) / (float) CYCLE;
            // head and tail of the travelling piece
            final float head = start + w * EASE.getInterpolation(Math.min(1f, t * 1.25f));
            final float tail = start + w * EASE.getInterpolation(Math.max(0f, t * 1.25f - 0.25f));
            final float gap = p.dp(4f) + stroke / 2f;
            final float phase = (now % 1100L) / 1100f;
            p.line(canvas, start, tail - gap, cy, stroke, track, mAlpha);
            p.wave(canvas, tail, head, cy, p.maxAmp(b, stroke), phase, stroke, active, mAlpha);
            p.line(canvas, head + gap, end, cy, stroke, track, mAlpha);
            if (mRunning && isVisible()) invalidateSelf();
        }
    }
}
