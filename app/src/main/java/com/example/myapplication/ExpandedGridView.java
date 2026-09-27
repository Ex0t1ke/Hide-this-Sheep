package com.example.myapplication;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.GridView;

/**
 * GridView that expands to wrap all its children without internal scrolling.
 * Perfect for level select boards where all cards should fit neatly on the board.
 */
public class ExpandedGridView extends GridView {

    public ExpandedGridView(Context context) {
        super(context);
    }

    public ExpandedGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExpandedGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Calculate height spec with AT_MOST to measure all rows without clipping
        int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, expandSpec);
    }
}
