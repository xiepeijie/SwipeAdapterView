package me.payge.swipeadapterview;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.os.Build;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.FrameLayout;

import java.util.ArrayList;

/**
 * @author payge
 * 通过ViewGragHelper实现拖拽滑动
 */
public class SwipeAdapterView extends AdapterView {

    private ArrayList<View> cache = new ArrayList<>();

    //缩放层叠效果
    private int yOffsetStep; // view叠加垂直偏移量的步长
    private static final float SCALE_STEP = 0.08f; // view叠加缩放的步长
    //缩放层叠效果

    private int MAX_VISIBLE = 4; // 值建议最小为4
    private int MIN_ADAPTER_STACK = 6;
    private float ROTATION_DEGREES = 2f;
    private int LAST_VIEW_IN_STACK = 0;

    private Adapter mAdapter;
    private onFlingListener mFlingListener;
    private AdapterDataSetObserver mDataSetObserver;
    private boolean mInLayout = false;
    private View mActiveCard = null;
    private OnItemClickListener mOnItemClickListener;

    // 支持左右滑
    public boolean isNeedSwipe = true;

    private int initTop;
    private int initLeft;

    private ViewDragHelper viewDragHelper;
    private GestureDetectorCompat detector;
    private int widthMeasureSpec;
    private int heightMeasureSpec;

    public SwipeAdapterView(Context context) {
        this(context, null);
    }

    public SwipeAdapterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeAdapterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SwipeAdapterView, defStyle, 0);
        MAX_VISIBLE = a.getInt(R.styleable.SwipeAdapterView_max_visible, MAX_VISIBLE);
        MIN_ADAPTER_STACK = a.getInt(R.styleable.SwipeAdapterView_min_adapter_stack, MIN_ADAPTER_STACK);
        ROTATION_DEGREES = a.getFloat(R.styleable.SwipeAdapterView_rotation_degrees, ROTATION_DEGREES);
        yOffsetStep = a.getDimensionPixelOffset(R.styleable.SwipeAdapterView_y_offset_step, 0);
        a.recycle();

        viewDragHelper = ViewDragHelper.create(this, 4f, callback);
        detector = new GestureDetectorCompat(context, new ScrollDetector());
    }

    public void setIsNeedSwipe(boolean isNeedSwipe) {
        this.isNeedSwipe = isNeedSwipe;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        this.widthMeasureSpec = widthMeasureSpec;
        this.heightMeasureSpec = heightMeasureSpec;
    }

    @Override
    public View getSelectedView() {
        return mActiveCard;
    }

    @Override
    public void setSelection(int position) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (viewDragHelper.continueSettling(false)) {
            ViewCompat.postInvalidateOnAnimation(this);
        } else {
            if (isSwipeRun) {
                isSwipeRun = false;
                Log.e("tag", "computeScroll");
                //adjustChildrenOfUnderTopView(1f);
                if (mFlingListener != null) {
                    if (mActiveCard.getLeft() > 0) {
                        mFlingListener.onRightCardExit(mAdapter.getItem(0));
                    } else {
                        mFlingListener.onLeftCardExit(mAdapter.getItem(0));
                    }
                    mFlingListener.removeFirstObjectInAdapter();
                }
                mActiveCard = null;
            } else {
                //adjustChildrenOfUnderTopView(0);
            }
            cLeft = 0;
            cTop = 0;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean b = viewDragHelper.shouldInterceptTouchEvent(ev);
        if (ev.getActionMasked()==MotionEvent.ACTION_DOWN) {
            viewDragHelper.processTouchEvent(ev);
        }
        return b && detector.onTouchEvent(ev) && isNeedSwipe;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        viewDragHelper.processTouchEvent(event);
        return isNeedSwipe;
    }

    @Override
    public void requestLayout() {
        if (!mInLayout) {
            super.requestLayout();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // if we don't have an adapter, we don't need to do anything
        if (mAdapter == null) {
            return;
        }

        mInLayout = true;
        final int adapterCount = mAdapter.getCount();
        if (adapterCount == 0) {
//            removeAllViewsInLayout();
            removeViewToCache(0);
        } else {
            View topCard = getChildAt(LAST_VIEW_IN_STACK);
            if(mActiveCard != null && topCard != null && topCard == mActiveCard) {
//                removeViewsInLayout(0, LAST_VIEW_IN_STACK);
                removeViewToCache(1);
                layoutChildren(1, adapterCount);
            }else{
                // Reset the UI and set top view listener
//                removeAllViewsInLayout();
                removeViewToCache(0);
                layoutChildren(0, adapterCount);
                setTopView();
            }
        }
        mInLayout = false;

        if (initTop == 0 && initLeft == 0 && mActiveCard != null) {
            initTop = mActiveCard.getTop();
            initLeft = mActiveCard.getLeft();
        }

        if(adapterCount < MIN_ADAPTER_STACK) {
        	if(mFlingListener != null){
        		mFlingListener.onAdapterAboutToEmpty(adapterCount);
        	}
        }
    }

    private void removeViewToCache(int saveCount) {
        View child;
        for (int i = 0; i < getChildCount() - saveCount; ) {
            child = getChildAt(i);
            removeViewInLayout(child);
            cache.add(child);
        }
    }

    private void layoutChildren(int startingIndex, int adapterCount){
        while (startingIndex < Math.min(adapterCount, MAX_VISIBLE) ) {
            View cacheView = null;
            if (cache.size() > 0) {
                cacheView = cache.get(0);
                cache.remove(cacheView);
            }
            View newUnderChild = mAdapter.getView(startingIndex, cacheView, this);
            if (newUnderChild.getVisibility() != GONE) {
                makeAndAddView(newUnderChild, startingIndex);
                LAST_VIEW_IN_STACK = startingIndex;
            }
            startingIndex++;
        }
    }

    private void makeAndAddView(View child, int index) {

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
        addViewInLayout(child, 0, lp, true);

        final boolean needToMeasure = child.isLayoutRequested();
        if (needToMeasure) {
            int childWidthSpec = getChildMeasureSpec(widthMeasureSpec,
                    getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin,
                    lp.width);
            int childHeightSpec = getChildMeasureSpec(heightMeasureSpec,
                    getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin,
                    lp.height);
            child.measure(childWidthSpec, childHeightSpec);
        } else {
            cleanupLayoutState(child);
        }

        int w = child.getMeasuredWidth();
        int h = child.getMeasuredHeight();

        int gravity = lp.gravity;
        if (gravity == -1) {
            gravity = Gravity.TOP | Gravity.START;
        }

        int layoutDirection = 0;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN)
            layoutDirection = getLayoutDirection();
        final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
        final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

        int childLeft;
        int childTop;
        switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
            case Gravity.CENTER_HORIZONTAL:
                childLeft = (getWidth() + getPaddingLeft() - getPaddingRight()  - w) / 2 +
                        lp.leftMargin - lp.rightMargin;
                break;
            case Gravity.END:
                childLeft = getWidth() + getPaddingRight() - w - lp.rightMargin;
                break;
            case Gravity.START:
            default:
                childLeft = getPaddingLeft() + lp.leftMargin;
                break;
        }
        switch (verticalGravity) {
            case Gravity.CENTER_VERTICAL:
                childTop = (getHeight() + getPaddingTop() - getPaddingBottom()  - h) / 2 +
                        lp.topMargin - lp.bottomMargin;
                break;
            case Gravity.BOTTOM:
                childTop = getHeight() - getPaddingBottom() - h - lp.bottomMargin;
                break;
            case Gravity.TOP:
            default:
                childTop = getPaddingTop() + lp.topMargin;
                break;
        }
        child.layout(childLeft, childTop, childLeft + w, childTop + h);
        // 缩放层叠效果
        adjustChildView(child, index);
    }

    private void adjustChildView(View child, int index) {
        if (index > -1 && index < MAX_VISIBLE) {
            int multiple;
            if (index > 2) multiple = 2;
            else multiple = index;
            child.offsetTopAndBottom(yOffsetStep * multiple);
            child.setScaleX(1 - SCALE_STEP * multiple);
            child.setScaleY(1 - SCALE_STEP * multiple);
        }
    }

    private void adjustChildrenOfUnderTopView(float scrollRate) {
        int count = getChildCount();
        if (count > 1) {
            int i;
            int multiple;
            if (count == 2) {
                i = LAST_VIEW_IN_STACK - 1;
                multiple = 1;
            } else {
                i = LAST_VIEW_IN_STACK - 2;
                multiple = 2;
            }
            float rate = Math.abs(scrollRate);
            for (; i < LAST_VIEW_IN_STACK; i++, multiple--) {
                View underTopView = getChildAt(i);
                int offset = (int) (yOffsetStep * (multiple - rate));
                underTopView.offsetTopAndBottom(offset - underTopView.getTop() + initTop);
                underTopView.setScaleX(1 - SCALE_STEP * multiple + SCALE_STEP * rate);
                underTopView.setScaleY(1 - SCALE_STEP * multiple + SCALE_STEP * rate);
            }
        }
    }

    /**
    *  Set the top view and add the fling listener
    */
    private void setTopView() {
        if(getChildCount() > 0){
            mActiveCard = getChildAt(LAST_VIEW_IN_STACK);
            if(mActiveCard != null) {
                mActiveCard.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mOnItemClickListener.onItemClicked(v, mAdapter.getItem(0));
                    }
                });
            }
        }
    }

    public void setMaxVisible(int MAX_VISIBLE){
        this.MAX_VISIBLE = MAX_VISIBLE;
    }

    public void setMinStackInAdapter(int MIN_ADAPTER_STACK){
        this.MIN_ADAPTER_STACK = MIN_ADAPTER_STACK;
    }

    @Override
    public Adapter getAdapter() {
        return mAdapter;
    }


    @Override
    public void setAdapter(Adapter adapter) {
        if (mAdapter != null && mDataSetObserver != null) {
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
            mDataSetObserver = null;
        }

        mAdapter = adapter;

        if (mAdapter != null  && mDataSetObserver == null) {
            mDataSetObserver = new AdapterDataSetObserver();
            mAdapter.registerDataSetObserver(mDataSetObserver);
        }
    }

    public void setFlingListener(onFlingListener onFlingListener) {
        this.mFlingListener = onFlingListener;
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener){
        this.mOnItemClickListener = onItemClickListener;
    }


    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new FrameLayout.LayoutParams(getContext(), attrs);
    }


    private class AdapterDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            requestLayout();
        }

        @Override
        public void onInvalidated() {
            requestLayout();
        }

    }


    public interface OnItemClickListener {
        void onItemClicked(View v, Object dataObject);
    }

    public interface onFlingListener {
        void removeFirstObjectInAdapter();
        void onLeftCardExit(Object dataObject);
        void onRightCardExit(Object dataObject);
        void onAdapterAboutToEmpty(int itemsInAdapter);
        void onScroll(float progress, float scrollXProgress);
    }

    private class ScrollDetector extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
            return Math.abs(dy) + Math.abs(dx) > 4;
        }
    }

    boolean isSwipeRun;
    int cLeft, cTop;

    private ViewDragHelper.Callback callback = new ViewDragHelper.Callback() {

//        int disX, disY;

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            cLeft = child.getLeft();
            cTop = child.getTop();
            return child == mActiveCard;
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return left;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return top;
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            int offsetX = left - cLeft;
            int offsetY = top - cTop;
            float progress = 1f * (Math.abs(offsetX) + Math.abs(offsetY)) / 400;
            float scrollX = 1f * Math.abs(offsetX) / 400;
            progress = Math.min(progress, 1f);
            scrollX = Math.min(scrollX, 1f);
            //Log.e("tag", "progress = " + progress);
            adjustChildrenOfUnderTopView(progress);
            mFlingListener.onScroll(progress, scrollX);
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            int disX = releasedChild.getLeft() - cLeft;
            int disY = releasedChild.getTop() - cTop;
            int endTop = releasedChild.getTop();
            int offsetTop = getHeight() / 6;
            if (disY > offsetTop) {
                endTop += 2 * offsetTop;
            } else if (disY < -offsetTop){
                endTop -= 2 * offsetTop;
            }
            if (disX > getWidth() / 4) {
                isSwipeRun = true;
                viewDragHelper.smoothSlideViewTo(releasedChild, getWidth() + 200, endTop);
                invalidate();
            } else if (disX < -(getWidth() / 4)){
                isSwipeRun = true;
                viewDragHelper.smoothSlideViewTo(releasedChild, -getWidth(), endTop);
                invalidate();
            } else {
                viewDragHelper.smoothSlideViewTo(releasedChild, initLeft, initTop);
                invalidate();
            }
        }
    };

}
