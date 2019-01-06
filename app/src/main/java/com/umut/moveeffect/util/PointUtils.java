package com.umut.moveeffect.util;

import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.RegionIterator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public final class PointUtils {

    private PointUtils() {
    }

    public static boolean lazyMatch(@Nullable final PointF to, @Nullable final PointF test) {
        if (to == null || test == null) {
            return false;
        }
        if (to.x - Constants.INTERSECT_DEVIATION < test.x && to.x + Constants.INTERSECT_DEVIATION > test.x) {
            return to.y - Constants.INTERSECT_DEVIATION < test.y && to.y + Constants.INTERSECT_DEVIATION > test.y;
        }
        return false;
    }

    public static float getDistance(@Nullable final PointF from, @Nullable final PointF to) {
        if (to == null || from == null) {
            return 0f;
        }
        return (float) Math.sqrt(Math.pow(((double) from.x - to.x), 2) - Math.pow(((double) from.y - to.y), 2));
    }

    public static float computePathArea(@NonNull Path path) {
        Region region = getRegionFromPath(path);
        return calculateArea(region);
    }

    public static boolean checkPointInsidePath(@NonNull Path path, @NonNull PointF pointF) {
        final Region pathRegion = getRegionFromPath(path);
        return pathRegion.contains((int) pointF.x, (int) pointF.y);
    }

    @NonNull
    public static Rect getRectOfPath(@NonNull Path path) {
        final RectF rectF = new RectF();
        path.computeBounds(rectF, true);
        return new Rect((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
    }

    @NonNull
    private static Region getRegionFromPath(@NonNull Path path) {
        final RectF rectF = new RectF();
        path.computeBounds(rectF, true);
        final Rect rect = new Rect((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
        final Region region = new Region();
        region.setPath(path, new Region(rect));
        return region;
    }

    private static float calculateArea(@NonNull Region region) {

        RegionIterator regionIterator = new RegionIterator(region);

        int size = 0; // amount of Rects
        float area = 0; // units of area

        Rect tmpRect = new Rect();

        while (regionIterator.next(tmpRect)) {
            size++;
            area += tmpRect.width() * tmpRect.height();
        }
        return area / size;

    }


}
