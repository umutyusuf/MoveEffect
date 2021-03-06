package com.umut.moveeffect.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public final class BitmapUtils {
    private BitmapUtils() {

    }

    public static Bitmap getCroppedBitmap(@NonNull Bitmap src, @NonNull Path path) {
        final Bitmap output = Bitmap.createBitmap(src.getWidth(),
                src.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(output);

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0XFF000000);

        canvas.save();
        canvas.drawPath(path, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(src, 0, 0, paint);
        canvas.restore();

        final Rect rect = PointUtils.getRectOfPath(path);
        final Bitmap croppedBmp = Bitmap.createBitmap(output, rect.left, rect.top, rect.width(), rect.height());
        if (croppedBmp != output && !output.isRecycled()) {
            output.recycle();
        }
        return croppedBmp;
    }

    public static Bitmap resize(@Nullable Bitmap image, float maxWidth, float maxHeight) {
        if (maxHeight > 0 && maxWidth > 0 && image != null) {
            int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = maxWidth / maxHeight;

            float finalWidth = maxWidth;
            float finalHeight = maxHeight;
            if (ratioMax > ratioBitmap) {
                finalWidth = (int) (maxHeight * ratioBitmap);
            } else {
                finalHeight = (int) (maxWidth / ratioBitmap);
            }
            final Bitmap resizedBitmap = Bitmap.createScaledBitmap(image, (int) finalWidth, (int) finalHeight, false);
            if (resizedBitmap != image) {
                image.recycle();
            }
            return resizedBitmap;
        } else {
            return image;
        }
    }


}
