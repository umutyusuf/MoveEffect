package com.umut.moveeffect.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.umut.moveeffect.util.BitmapUtils;
import com.umut.moveeffect.util.Constants;
import com.umut.moveeffect.util.PointUtils;

public class EditableImageView extends AppCompatImageView {

    private static final int LINE_COLOR = Color.GRAY;
    private static final int CIRCLE_COLOR = Color.WHITE;
    private static final float LINE_WIDTH = 9f;

    @NonNull
    private final Path selectionPath = new Path();
    @NonNull
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull
    private final Paint croppedBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull
    private final Paint copyBitmapPath = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull
    private final PointF initialPoint = new PointF(-Constants.INITIAL_CIRCLE_RADIUS, -Constants.INITIAL_CIRCLE_RADIUS);
    @NonNull
    private final PointF lastPoint = new PointF();
    @NonNull
    private final PointF grabPoint = new PointF();
    @NonNull
    private final PointF lastDragPoint = new PointF();
    @NonNull
    private final Rect selectionRect = new Rect();
    @NonNull
    private final RectF drawRect = new RectF();

    @MarkState
    private int state;
    @Nullable
    private Bitmap croppedAreaBitmap;
    private float diffStepX;
    private float diffStepY;

    @Nullable
    private SelectionStateListener listener;
    private int repCount;
    private int markCount;

    public EditableImageView(Context context) {
        this(context, null);
    }

    public EditableImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EditableImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        state = MarkState.INITIAL;
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(LINE_COLOR);
        linePaint.setStrokeWidth(LINE_WIDTH);

        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setColor(CIRCLE_COLOR);

        croppedBitmapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        copyBitmapPath.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        copyBitmapPath.setAlpha(255);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(initialPoint.x, initialPoint.y, Constants.INITIAL_CIRCLE_RADIUS, circlePaint);
        canvas.drawPath(selectionPath, linePaint);
        drawOverlays(canvas);
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        clearDrawings();
        super.setImageBitmap(bm);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final PointF point = new PointF(event.getX(), event.getY());
        if (state != MarkState.SELECTION_DRAGGING && !isEventInOfBounds(event)) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                onActionDown(point);
                return true;
            case MotionEvent.ACTION_MOVE:
                onActionMove(point);
                return true;
            case MotionEvent.ACTION_UP:
                onRelease(point);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    public void setSelectionStateChangeListener(@Nullable SelectionStateListener listener) {
        this.listener = listener;
    }

    public void updateAlpha(int alpha) {
        croppedBitmapPaint.setAlpha(alpha);
        invalidate();
    }

    public void updateRepCount(int nRepCount) {
        this.repCount = nRepCount;
        computeOffsetList();
    }

    @Nullable
    public Bitmap getDrawnBitmap() {
        final Bitmap output = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(output);
        if (drawOverlays(canvas)) {
            return output;
        }
        return null;
    }

    private void onActionDown(@NonNull PointF point) {
        switch (state) {
            case MarkState.INITIAL:
                onInitialTouch(point);
                break;
            case MarkState.AREA_SELECTED:
            case MarkState.SELECTION_DRAGGING:
                valuatePathWithPoint(point);
                break;
            case MarkState.RELEASED:
                if (PointUtils.lazyMatch(lastPoint, point)) {
                    qAddToPath(lastPoint, point);
                    onActionMove(point);
                } else {
                    onInitialTouch(point);
                }
            default:
                onInitialTouch(point);
        }
    }

    private void onInitialTouch(@NonNull PointF point) {
        clearDrawings();
        initialPoint.set(point);
        lastPoint.set(point);
        markCount = 0;
        moveToPoint(point);
        invalidate();
    }

    private void clearDrawings() {
        if (croppedAreaBitmap != null) {
            croppedAreaBitmap.recycle();
        }
        croppedAreaBitmap = null;
        lastDragPoint.set(0, 0);
        grabPoint.set(0, 0);
        diffStepX = 0;
        diffStepY = 0;
        rewind();
        onAreaSelectReleased();
    }

    private void onRelease(@NonNull PointF point) {
        if (state == MarkState.MARKER_MOVE) {
            lastPoint.set(point);
            state = MarkState.RELEASED;
        }
    }

    private void onActionMove(@NonNull PointF point) {
        if (state == MarkState.AREA_SELECTED) {
            return;
        }
        if (state == MarkState.SELECTION_GRABBED || state == MarkState.SELECTION_DRAGGING) {
            state = MarkState.SELECTION_DRAGGING;
            lastDragPoint.set(point);
            computeOffsetList();
        } else {
            state = MarkState.MARKER_MOVE;
            markCount++;
            if (!computeIntersect(point)) {
                lastPoint.set(point);
                addToPath(point);
            }
            invalidate();
        }
    }

    private void computeOffsetList() {
        diffStepX = (lastDragPoint.x - grabPoint.x) / repCount;
        diffStepY = (lastDragPoint.y - grabPoint.y) / repCount;
        invalidate();
    }

    private void valuatePathWithPoint(@NonNull PointF point) {
        final boolean insidePath = PointUtils.checkPointInsidePath(selectionPath, point);
        if (insidePath) {
            state = MarkState.SELECTION_GRABBED;
            grabPoint.set(point);
            lastPoint.set(point);
        } else {
            onInitialTouch(point);
        }
    }

    private void addToPath(@NonNull PointF point) {
        selectionPath.lineTo(point.x, point.y);
    }

    private void qAddToPath(@NonNull PointF point, @NonNull PointF endPosition) {
        selectionPath.quadTo(point.x, point.y, endPosition.x, endPosition.y);
    }

    private void moveToPoint(@NonNull PointF point) {
        selectionPath.moveTo(point.x, point.y);
    }

    private void rewind() {
        selectionPath.rewind();
    }

    private void closePath() {
        selectionPath.close();
    }

    private boolean computeIntersect(@NonNull PointF nPoint) {
        final float lastDistanceToStart = PointUtils.getDistance(initialPoint, lastPoint);
        final float currentDistanceToStart = PointUtils.getDistance(initialPoint, nPoint);
        if (currentDistanceToStart < lastDistanceToStart) {
            // lamely detected the approach to initial point
            if (currentDistanceToStart <= Constants.SNAP_DISTANCE) {
                final Path copyPath = new Path(selectionPath);
                copyPath.quadTo(nPoint.x, nPoint.y, initialPoint.x, initialPoint.y);
                copyPath.close();
                if (PointUtils.computePathArea(copyPath) > Constants.MIN_ALLOWED_SNAP_AREA) {
                    performCrop(nPoint);
                    return true;
                }
            }
        }
        if (markCount > Constants.INTERSECT_DEVIATION && PointUtils.lazyMatch(initialPoint, nPoint)) {
            performCrop(nPoint);
            return true;
        }
        return false;
    }

    private void performCrop(@NonNull PointF nPoint) {
        qAddToPath(nPoint, initialPoint);
        closePath();
        onAreaSelected();
        this.selectionRect.set(PointUtils.getRectOfPath(selectionPath));
        croppedAreaBitmap =
                BitmapUtils.getCroppedBitmap(
                        ((BitmapDrawable) getDrawable()).getBitmap(),
                        selectionPath);
    }

    private boolean drawOverlays(Canvas canvas) {
        if (croppedAreaBitmap != null && (diffStepX != 0 || diffStepY != 0)) {
            drawRect.set(selectionRect);
            for (int i = 0; i < repCount; i++) {
                drawRect.offset(diffStepX, diffStepY);
                if (!canvas.quickReject(drawRect, Canvas.EdgeType.AA)) {
                    canvas.drawBitmap(croppedAreaBitmap, null, drawRect, croppedBitmapPaint);
                }
            }
            canvas.drawBitmap(croppedAreaBitmap, null, selectionRect, copyBitmapPath);
            return true;
        }
        return false;
    }

    private boolean isEventInOfBounds(MotionEvent motionEvent) {
        int[] iArr = new int[2];
        getLocationOnScreen(iArr);
        int i = iArr[0];
        int i2 = iArr[1];
        return !(motionEvent.getRawX() < ((float) i) || motionEvent.getRawX() > ((float) (i + getWidth()))
                || motionEvent.getRawY() < ((float) i2) || motionEvent.getRawY() > ((float) (i2 + getHeight())));
    }

    private void onAreaSelected() {
        state = MarkState.AREA_SELECTED;
        if (listener != null) {
            listener.onAreaSelect();
        }
    }

    private void onAreaSelectReleased() {
        state = MarkState.INITIAL;
        if (listener != null) {
            listener.onAreaSelectionReleased();
        }
    }
}
