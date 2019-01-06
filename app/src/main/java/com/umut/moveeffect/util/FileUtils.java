package com.umut.moveeffect.util;

import android.graphics.Bitmap;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class FileUtils {
    private static final String DIRECTORY_NAME = "MoveEffect";

    private FileUtils() {

    }

    @Nullable
    public static String saveBitmap(@Nullable Bitmap bmp) {
        if (bmp == null) {
            return null;
        }
        if (!isExternalStorageWritable()) {
            return null;
        }

        final String fileName = "m_" + System.currentTimeMillis() + ".jpg";
        final File albumFile = getPublicAlbumStorageDir(DIRECTORY_NAME, fileName);
        if (albumFile == null) {
            return null;
        }
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(albumFile);
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
            bmp.recycle();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return albumFile.getAbsolutePath();
    }

    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private static File getPublicAlbumStorageDir(@NonNull String albumName, @NonNull String fileName) {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), albumName);
        if (!file.exists() && !file.mkdirs()) {
            return null;
        }
        return new File(file, fileName);
    }
}
