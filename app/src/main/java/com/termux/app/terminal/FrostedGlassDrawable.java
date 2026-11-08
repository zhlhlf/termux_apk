package com.termux.app.terminal;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * fork: rounded, translucent "frosted glass" background for the extra keys bar.
 *
 * There is no in-window backdrop blur on Android (RenderEffect only affects the view it is set
 * on, and blurring the terminal itself would blur the text), so the wallpaper behind the bar is
 * blurred once into a small bitmap and drawn back, cropped to exactly the region the bar covers,
 * clipped to a rounded rectangle. A translucent tint and a hairline highlight go on top, which is
 * what actually reads as glass.
 *
 * The backdrop view must be the one that paints the wallpaper as its background, so the crop can be
 * aligned with it. If that background is not a {@link BitmapDrawable} the drawable falls back to the
 * tint alone instead of drawing a mismatched image.
 */
public class FrostedGlassDrawable extends Drawable {

    /** Blur is computed on a downscaled copy; smaller is faster and smoother. */
    private static final int DOWNSCALE = 8;
    private static final int BLUR_RADIUS = 6;
    private static final int BLUR_PASSES = 3;

    private final Paint mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint mTintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMilkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path mClipPath = new Path();
    private final RectF mBoundsF = new RectF();
    private final RectF mDstRect = new RectF();
    private final Rect mSrcRect = new Rect();

    private final View mBackdropView;
    private final float mRadius;
    private final float mEdgeWidth;

    @Nullable
    private Bitmap mBlurred;
    private int mBlurredWidth;
    private int mBlurredHeight;

    @Nullable
    private View mHostView;

    public FrostedGlassDrawable(@NonNull View backdropView, float radius,
                                int tintColor, int milkColor, int edgeColor, float edgeWidth) {
        mBackdropView = backdropView;
        mRadius = radius;
        mEdgeWidth = edgeWidth;

        mTintPaint.setColor(tintColor);
        mMilkPaint.setColor(milkColor);
        mEdgePaint.setStyle(Paint.Style.STROKE);
        mEdgePaint.setStrokeWidth(edgeWidth);
        mEdgePaint.setColor(edgeColor);
    }

    @Override
    public void setCallback(@Nullable Callback cb) {
        super.setCallback(cb);
        mHostView = cb instanceof View ? (View) cb : null;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        if (bounds.isEmpty()) return;

        mBoundsF.set(bounds);
        mClipPath.reset();
        mClipPath.addRoundRect(mBoundsF, mRadius, mRadius, Path.Direction.CW);

        final int saveCount = canvas.save();
        canvas.clipPath(mClipPath);

        drawBlurredBackdrop(canvas, bounds);

        canvas.drawRect(mBoundsF, mTintPaint);
        canvas.drawRect(mBoundsF, mMilkPaint);

        canvas.restoreToCount(saveCount);

        // Hairline edge, inset so it stays inside the rounded outline.
        final float inset = mEdgeWidth / 2f;
        mDstRect.set(bounds.left + inset, bounds.top + inset,
            bounds.right - inset, bounds.bottom - inset);
        final float radius = Math.max(0f, mRadius - inset);
        canvas.drawRoundRect(mDstRect, radius, radius, mEdgePaint);
    }

    private void drawBlurredBackdrop(@NonNull Canvas canvas, @NonNull Rect bounds) {
        final Bitmap blurred = getBlurredBitmap();
        if (blurred == null || mHostView == null) return;

        final int[] hostLocation = new int[2];
        final int[] backdropLocation = new int[2];
        mHostView.getLocationInWindow(hostLocation);
        mBackdropView.getLocationInWindow(backdropLocation);

        final int x = hostLocation[0] - backdropLocation[0];
        final int y = hostLocation[1] - backdropLocation[1];
        if (x < 0 || y < 0) return;

        // Map the host's on-screen rect into the downscaled bitmap, then stretch that slice over
        // the whole drawable: the bilinear upscale is what makes it look smooth rather than blocky.
        final float scaleX = (float) blurred.getWidth() / mBlurredWidth;
        final float scaleY = (float) blurred.getHeight() / mBlurredHeight;
        final int srcLeft = Math.round(x * scaleX);
        final int srcTop = Math.round(y * scaleY);
        final int srcRight = Math.round((x + bounds.width()) * scaleX);
        final int srcBottom = Math.round((y + bounds.height()) * scaleY);
        if (srcRight <= srcLeft || srcBottom <= srcTop) return;
        if (srcRight > blurred.getWidth() || srcBottom > blurred.getHeight()) return;

        mSrcRect.set(srcLeft, srcTop, srcRight, srcBottom);
        mDstRect.set(bounds);
        canvas.drawBitmap(blurred, mSrcRect, mDstRect, mBitmapPaint);
    }

    @Nullable
    private Bitmap getBlurredBitmap() {
        final int width = mBackdropView.getWidth();
        final int height = mBackdropView.getHeight();
        if (width <= 0 || height <= 0) return null;

        if (mBlurred != null && mBlurredWidth == width && mBlurredHeight == height) {
            return mBlurred;
        }

        final Drawable background = mBackdropView.getBackground();
        if (!(background instanceof BitmapDrawable)) return null;
        final Bitmap source = ((BitmapDrawable) background).getBitmap();
        if (source == null || source.isRecycled()) return null;

        final int smallWidth = Math.max(1, width / DOWNSCALE);
        final int smallHeight = Math.max(1, height / DOWNSCALE);
        final Bitmap scaled = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true);
        final int[] pixels = new int[smallWidth * smallHeight];
        scaled.getPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight);
        if (scaled != source) scaled.recycle();

        int[] blurredPixels = pixels;
        for (int i = 0; i < BLUR_PASSES; i++) {
            blurredPixels = boxBlur(blurredPixels, smallWidth, smallHeight, BLUR_RADIUS);
        }

        if (mBlurred != null) mBlurred.recycle();
        mBlurred = Bitmap.createBitmap(blurredPixels, smallWidth, smallHeight, Bitmap.Config.ARGB_8888);
        mBlurredWidth = width;
        mBlurredHeight = height;
        return mBlurred;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** Separable box blur; three passes approximate a gaussian closely enough for glass. */
    private static int[] boxBlur(@NonNull int[] source, int width, int height, int radius) {
        final int[] horizontal = new int[width * height];
        final int[] result = new int[width * height];
        final int divisor = radius * 2 + 1;

        for (int y = 0; y < height; y++) {
            final int rowStart = y * width;
            int sumR = 0, sumG = 0, sumB = 0;
            for (int i = -radius; i <= radius; i++) {
                final int pixel = source[rowStart + clamp(i, 0, width - 1)];
                sumR += (pixel >> 16) & 0xff;
                sumG += (pixel >> 8) & 0xff;
                sumB += pixel & 0xff;
            }
            for (int x = 0; x < width; x++) {
                horizontal[rowStart + x] = 0xff000000
                    | (((sumR / divisor) & 0xff) << 16)
                    | (((sumG / divisor) & 0xff) << 8)
                    | ((sumB / divisor) & 0xff);
                final int outPixel = source[rowStart + clamp(x - radius, 0, width - 1)];
                final int inPixel = source[rowStart + clamp(x + radius + 1, 0, width - 1)];
                sumR += ((inPixel >> 16) & 0xff) - ((outPixel >> 16) & 0xff);
                sumG += ((inPixel >> 8) & 0xff) - ((outPixel >> 8) & 0xff);
                sumB += (inPixel & 0xff) - (outPixel & 0xff);
            }
        }

        for (int x = 0; x < width; x++) {
            int sumR = 0, sumG = 0, sumB = 0;
            for (int i = -radius; i <= radius; i++) {
                final int pixel = horizontal[clamp(i, 0, height - 1) * width + x];
                sumR += (pixel >> 16) & 0xff;
                sumG += (pixel >> 8) & 0xff;
                sumB += pixel & 0xff;
            }
            for (int y = 0; y < height; y++) {
                result[y * width + x] = 0xff000000
                    | (((sumR / divisor) & 0xff) << 16)
                    | (((sumG / divisor) & 0xff) << 8)
                    | ((sumB / divisor) & 0xff);
                final int outPixel = horizontal[clamp(y - radius, 0, height - 1) * width + x];
                final int inPixel = horizontal[clamp(y + radius + 1, 0, height - 1) * width + x];
                sumR += ((inPixel >> 16) & 0xff) - ((outPixel >> 16) & 0xff);
                sumG += ((inPixel >> 8) & 0xff) - ((outPixel >> 8) & 0xff);
                sumB += (inPixel & 0xff) - (outPixel & 0xff);
            }
        }

        return result;
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        final Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            outline.setEmpty();
            return;
        }
        outline.setRoundRect(bounds, mRadius);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
