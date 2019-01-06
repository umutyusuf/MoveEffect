package com.umut.moveeffect;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.umut.moveeffect.MarkState.AREA_SELECTED;
import static com.umut.moveeffect.MarkState.INITIAL;
import static com.umut.moveeffect.MarkState.MARKER_MOVE;
import static com.umut.moveeffect.MarkState.RELEASED;
import static com.umut.moveeffect.MarkState.SELECTION_DRAGGING;
import static com.umut.moveeffect.MarkState.SELECTION_GRABBED;


@IntDef({INITIAL, MARKER_MOVE, RELEASED, AREA_SELECTED, SELECTION_GRABBED, SELECTION_DRAGGING})
@Retention(RetentionPolicy.SOURCE)
public @interface MarkState {
    int INITIAL = 0;
    int MARKER_MOVE = 1;
    int RELEASED = 2;
    int AREA_SELECTED = 3;
    int SELECTION_GRABBED = 4;
    int SELECTION_DRAGGING = 5;
}
