package com.umut.moveeffect;

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
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

public class EditableImageView extends AppCompatImageView {

    private static final int LINE_COLOR = Color.GRAY;
    private static final int CIRCLE_COLOR = Color.WHITE;
    private static final float LINE_WIDTH = 9f;

    private final Path selectionPath = new Path();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint croppedBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PointF initialPoint = new PointF(-Constants.INITIAL_CIRCLE_RADIUS, -Constants.INITIAL_CIRCLE_RADIUS);
    private final PointF lastPoint = new PointF();
    private final PointF grabPoint = new PointF();
    private final PointF lastDragPoint = new PointF();
    private final List<PointF> offsetList = new ArrayList<>();
    private final Rect selectionRect = new Rect();

    @MarkState
    private int state;

    @Nullable
    private SelectionStateListener listener;

    @Nullable
    private Bitmap croppedAreaBitmap;

    @Nullable
    private Canvas moveBitmapCanvas;
    @Nullable
    private Bitmap moveBitmap;

    private int repCount;

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

        croppedBitmapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (croppedAreaBitmap != null && moveBitmap != null) {
            canvas.drawBitmap(moveBitmap, 0, 0, null);
        }
        canvas.drawCircle(initialPoint.x, initialPoint.y, Constants.INITIAL_CIRCLE_RADIUS, circlePaint);
        canvas.drawPath(selectionPath, linePaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        populateMoveBitmap();
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        clearDrawings();
        super.setImageBitmap(bm);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final PointF point = new PointF(event.getX(), event.getY());
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
        prepareMoveCanvas();
        invalidate();
    }

    public void updateRepCount(int nRepCount) {
        this.repCount = nRepCount;
        computeOffsetList();
        invalidate();
    }

    @Nullable
    public Bitmap getDrawnBitmap() {
        final Bitmap output = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(output);
        if (moveBitmap != null) {
            canvas.drawBitmap(((BitmapDrawable) getDrawable()).getBitmap(), 0, 0, null);
            canvas.drawBitmap(moveBitmap, 0, 0, null);
            return output;
        } else {
            return null;
        }
    }

    private void prepareMoveCanvas() {
        eraseMoveBitmap();
        if (croppedAreaBitmap != null) {
            drawMoveCanvas(croppedAreaBitmap);
        }
    }


    private void drawMoveCanvas(@NonNull Bitmap croppedAreaBitmap) {
        if (moveBitmap == null) {
            populateMoveBitmap();
        }
        if (moveBitmapCanvas == null) {
            moveBitmapCanvas = new Canvas(moveBitmap);
        }
        if (offsetList.isEmpty()) {
            return;
        }
        moveBitmapCanvas.save();
        moveBitmapCanvas.drawBitmap(croppedAreaBitmap, selectionRect.left, selectionRect.top, null);

        for (PointF p : offsetList) {
            moveBitmapCanvas.drawBitmap(croppedAreaBitmap, selectionRect.left + p.x, selectionRect.top + p.y, croppedBitmapPaint);
        }
        moveBitmapCanvas.restore();
    }

    private void populateMoveBitmap() {
        moveBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
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
        }
    }

    private void onInitialTouch(@NonNull PointF point) {
        clearDrawings();
        initialPoint.set(point);
        lastPoint.set(point);
        moveToPoint(point);
        invalidate();
    }

    private void clearDrawings() {
        if (croppedAreaBitmap != null) {
            croppedAreaBitmap.recycle();
        }
        croppedAreaBitmap = null;
        offsetList.clear();
        eraseMoveBitmap();
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
            // Computation Might be moved to on release
            if (!computeIntersect(point)) {
                lastPoint.set(point);
                addToPath(point);
            }
        }
        invalidate();
    }

    private void computeOffsetList() {
        offsetList.clear();
        final float diffStepX = (lastDragPoint.x - grabPoint.x) / repCount;
        final float diffStepY = (lastDragPoint.y - grabPoint.y) / repCount;
        for (int i = 1; i <= repCount; i++) {
            offsetList.add(new PointF(diffStepX * i, diffStepY * i));
        }
        prepareMoveCanvas();
    }

    private void eraseMoveBitmap() {
        if (moveBitmap != null) {
            moveBitmap.eraseColor(Color.TRANSPARENT);
        }
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
                    qAddToPath(nPoint, initialPoint);
                    closePath();
                    onAreaSelected();
                    this.selectionRect.set(PointUtils.getRectOfPath(selectionPath));
                    croppedAreaBitmap =
                            BitmapUtils.getCroppedBitmap(
                                    ((BitmapDrawable) getDrawable()).getBitmap(),
                                    selectionPath);
                    return true;
                }
            }
        }
        return false;
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
