package com.termux.app.terminal;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * fork: rounded "liquid glass" background for the extra keys bar.
 *
 * Apple's liquid glass is mostly transparent: the backdrop stays clearly visible, gets slightly
 * magnified and saturated, and the material itself is only a whisper of tint plus a bright
 * specular highlight along the top edge and a soft rim. That is what this draws.
 *
 * Android has no in-window backdrop blur (RenderEffect only blurs the view it is set on, and
 * blurring the terminal would blur its text), so the wallpaper behind the bar is blurred once into
 * a small bitmap and drawn back cropped to the bar rect, slightly zoomed for the lensing feel,
 * clipped to a rounded rectangle.
 *
 * The backdrop view must be the one painting the wallpaper as its background, so the crop lines up.
 */
public class FrostedGlassDrawable extends Drawable {

    /** Blur is computed on a downscaled copy; smaller is faster and smoother. */
    private static final int DOWNSCALE = 6;
    private static final int BLUR_RADIUS = 3;
    private static final int BLUR_PASSES = 2;

    /** Slight magnification of the backdrop, which reads as the lensing of thick glass. */
    private static final float LENS_ZOOM = 1.05f;
    /** Glass makes the colours behind it pop a little. */
    private static final float SATURATION = 1.12f;

    /** Specular highlight covers the top part of the bar only. */
    private static final float SPECULAR_HEIGHT = 0.55f;

    private final Paint mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint mTintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSpecularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path mClipPath = new Path();
    private final RectF mBoundsF = new RectF();
    private final RectF mDstRect = new RectF();
    private final RectF mRimRect = new RectF();
    private final Rect mSrcRect = new Rect();

    private final View mBackdropView;
    private final float mRadius;
    private final float mRimWidth;
    private final int mSpecularColor;
    private final int mRimTopColor;
    private final int mRimBottomColor;

    @Nullable
    private Bitmap mBlurred;
    private int mBlurredWidth;
    private int mBlurredHeight;

    public FrostedGlassDrawable(@NonNull View backdropView, float radius, float rimWidth,
                                int tintColor, int specularColor,
                                int rimTopColor, int rimBottomColor) {
        mBackdropView = backdropView;
        mRadius = radius;
        mRimWidth = rimWidth;
        mSpecularColor = specularColor;
        mRimTopColor = rimTopColor;
        mRimBottomColor = rimBottomColor;

        mTintPaint.setColor(tintColor);
        mRimPaint.setStyle(Paint.Style.STROKE);
        mRimPaint.setStrokeWidth(rimWidth);
        mBitmapPaint.setColorFilter(saturationMatrix(SATURATION));
    }

    private static ColorMatrixColorFilter saturationMatrix(float saturation) {
        final ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(saturation);
        return new ColorMatrixColorFilter(matrix);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        mSpecularPaint.setShader(new LinearGradient(
            0f, bounds.top,
            0f, bounds.top + bounds.height() * SPECULAR_HEIGHT,
            mSpecularColor, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        mRimPaint.setShader(new LinearGradient(
            0f, bounds.top,
            0f, bounds.bottom,
            mRimTopColor, mRimBottomColor, Shader.TileMode.CLAMP));
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

        drawBackdrop(canvas, bounds);
        canvas.drawRect(mBoundsF, mTintPaint);
        canvas.drawRect(mBoundsF, mSpecularPaint);

        canvas.restoreToCount(saveCount);

        final float inset = mRimWidth / 2f;
        mRimRect.set(bounds.left + inset, bounds.top + inset,
            bounds.right - inset, bounds.bottom - inset);
        final float rimRadius = Math.max(0f, mRadius - inset);
        canvas.drawRoundRect(mRimRect, rimRadius, rimRadius, mRimPaint);
    }

    private void drawBackdrop(@NonNull Canvas canvas, @NonNull Rect bounds) {
        final Bitmap blurred = getBlurredBitmap();
        // The drawable is attached to the bar view; that view's position tells us which slice of
        // the backdrop is actually behind it.
        final View host = getCallback() instanceof View ? (View) getCallback() : null;
        if (blurred == null || host == null) return;

        final int[] hostLocation = new int[2];
        final int[] backdropLocation = new int[2];
        host.getLocationInWindow(hostLocation);
        mBackdropView.getLocationInWindow(backdropLocation);

        final int x = hostLocation[0] - backdropLocation[0];
        final int y = hostLocation[1] - backdropLocation[1];
        if (x < 0 || y < 0) return;

        // Magnify around the centre of the crop: an area slightly larger than the bar is sampled
        // and squeezed into the same rect, which gives the subtle lensing of thick glass.
        final float zoom = 1f / LENS_ZOOM;
        final float centreX = x + bounds.width() / 2f;
        final float centreY = y + bounds.height() / 2f;
        final float halfWidth = bounds.width() * zoom / 2f;
        final float halfHeight = bounds.height() * zoom / 2f;

        final float scaleX = (float) blurred.getWidth() / mBlurredWidth;
        final float scaleY = (float) blurred.getHeight() / mBlurredHeight;
        final int srcLeft = Math.round((centreX - halfWidth) * scaleX);
        final int srcTop = Math.round((centreY - halfHeight) * scaleY);
        final int srcRight = Math.round((centreX + halfWidth) * scaleX);
        final int srcBottom = Math.round((centreY + halfHeight) * scaleY);
        if (srcRight <= srcLeft || srcBottom <= srcTop) return;
        if (srcLeft < 0 || srcTop < 0) return;
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
