/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tws.assistant.widget;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.RemoteViews.RemoteView;
import android.widget.TextView;
import android.widget.WrapperListAdapter;

import com.android.internal.util.Predicate;
import com.google.android.collect.Lists;
import com.tencent.tws.assistant.drawable.TwsScrollBarDrawable;
import com.tencent.tws.assistant.gaussblur.JNIBlur;
import com.tencent.tws.assistant.gaussblur.NativeBlurProcess;
import com.tencent.tws.assistant.utils.MathUtils;
import com.tencent.tws.sharelib.R;

/*
 * Implementation Notes:
 *
 * Some terminology:
 *
 *     index    - index of the items that are currently visible
 *     position - index of the items in the cursor
 */


/**
 * A view that shows items in a vertically scrolling list. The items
 * come from the {@link ListAdapter} associated with this view.
 *
 * <p>See the <a href="{@docRoot}guide/topics/ui/layout/listview.html">List View</a>
 * guide.</p>
 *
 * @attr ref android.R.styleable#ListView_entries
 * @attr ref android.R.styleable#ListView_divider
 * @attr ref android.R.styleable#ListView_dividerHeight
 * @attr ref android.R.styleable#ListView_headerDividersEnabled
 * @attr ref android.R.styleable#ListView_footerDividersEnabled
 */
@RemoteView
public class ListView extends AbsListView {
    /**
     * Used to indicate a no preference for a position type.
     */
    static final int NO_POSITION = -1;

    /**
     * When arrow scrolling, ListView will never scroll more than this factor
     * times the height of the list.
     */
    private static final float MAX_SCROLL_FACTOR = 0.33f;

    /**
     * When arrow scrolling, need a certain amount of pixels to preview next
     * items.  This is usually the fading edge, but if that is small enough,
     * we want to make sure we preview at least this many pixels.
     */
    private static final int MIN_SCROLL_PREVIEW_PIXELS = 2;

    /**
     * A class that represents a fixed view in a list, for example a header at the top
     * or a footer at the bottom.
     */
    public class FixedViewInfo {
        /** The view to add to the list */
        public View view;
        /** The data backing the view. This is returned from {@link ListAdapter#getItem(int)}. */
        public Object data;
        /** <code>true</code> if the fixed view should be selectable in the list */
        public boolean isSelectable;
    }

    private ArrayList<FixedViewInfo> mHeaderViewInfos = Lists.newArrayList();
    private ArrayList<FixedViewInfo> mFooterViewInfos = Lists.newArrayList();
    private ArrayList<FixedViewInfo> mTempFooterViewInfos = Lists.newArrayList();

    Drawable mDivider;
    int mDividerHeight;

    Drawable mOverScrollHeader;
    Drawable mOverScrollFooter;

    private boolean mIsCacheColorOpaque;
    private boolean mDividerIsOpaque;

    private boolean mHeaderDividersEnabled;
    private boolean mFooterDividersEnabled;

    private boolean mAreAllItemsSelectable = true;

    private boolean mItemsCanFocus = false;

    // used for temporary calculations.
    private final Rect mTempRect = new Rect();
    private Paint mDividerPaint;

    // the single allocated result per list view; kinda cheesey but avoids
    // allocating these thingies too often.
    private final ArrowScrollFocusResult mArrowScrollFocusResult = new ArrowScrollFocusResult();

    // Keeps focused children visible through resizes
    private FocusSelector mFocusSelector;

    //tws-start::add empty list indicator support 2013-05-31
    // whether show the empty view. when app supplies one of 
    // emptyListIcon, emptyListText, emptyListSecondText attr, the empty view will be displayed
    private boolean mShowEmptyList = true;
    // background resource id of the empty view
    private int mEmptyListBackground;
    // app can specify an icon at the center of the empty view
    private int mEmptyListIcon;
    // app can specify a line of text
    private int mEmptyListText;
    // app can also specify a second line of text
    private int mEmptyListSecondText;
    // first line text appearacne
    private int mEmptyListTextAppearanceId = 0;
    // second line text appearance
    private int mEmptyListSecondTextAppearanceId = 0;
    // left margin of the empty view
    private int mEmptyListMarginLeft = 0;
    // top margin of the empty view. because the height of the listview may vary due to different layout
    // the top margin may vary too. i.e. this is the max top margin
    private int mEmptyListMarginTop = 0;
    // right margin
    private int mEmptyListMarginRight = 0;
    // bottom margin, not avaiable for app. it is calculated according listview height, mEmptyListMarginTop,
    // mEmptyListMaxHeight, mEmptyListMinHeight and MIN_MARGIN
    private int mEmptyListMarginBottom = 0;
    // minimum height of the empty view. it is the minimum height to show all the elements
    private int mEmptyListMinHeight = 0;
    // app can specify the max height of the empty view.
    private int mEmptyListMaxHeight = 0;
    // minimum margin in pixel, calculated from MIN_MARGIN, in dp, via dpToPixel
    private int mEmptyListMinMargin = 0;
    // minimum margin in dp unit
    private static final int MIN_MARGIN = 12;   // unit dp
    private static float sPixelDensity = 1;
    // activated/selected list item background resource id
    private int mActivatedBackgroundIndicator = 0;
    // whether selected state background is enabled
    private boolean mSelectedStateEnabled = false;
    //tws-end::add empty list indicator support 2013-05-31
    
    //tws-start add listview blur feature::2014-10-05
  	private int mBlurBottomHeight, mBlurTopHeight;
  	private Paint mBlurPaint;
  	private Rect mBlurForBottomRect, mBlurForTopRect, mForContentRect;
  	private Bitmap mForBottomBitmap, mForTopBitmap;
  	private Canvas mForBottomCanvas, mForTopCanvas;
  	private View mTopBlurView, mBottomBlurView;
  	private JNIBlur mBlur;
  	private Bitmap mBlurBottomBgBitmap, mBlurTopBgBitmap;
  	
	private FixedViewInfo blurInfo;
	
	private boolean mFirstItemHigher, mLastItemHigher;
	private View paddingView;
	private Drawable mBlurTopDrawable, mBlurBottomDrawable;
	private boolean mEnableBlur = true;
  	//tws-end add listview blur feature::2014-10-05

	//tws-start for item animation::2014-11-11
    private static final long ANIMATION_DURATION = 200;
    private TwsOnItemRemoveListener mOnItemRemoveListener;
    private final Collection<View> mRemovedViews = new LinkedList<View>();
    private final List<Integer> mRemovedPositions = new LinkedList<Integer>();
    private int mActiveRemoveCount;
    //tws-end for item animation::2014-11-11

	public ListView(Context context) {
        this(context, null);
    }

    public ListView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.listViewStyle);
    }

    public ListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ListView, defStyle, 0);

        CharSequence[] entries = a.getTextArray(R.styleable.ListView_entries);
        if (entries != null) {
            setAdapter(new ArrayAdapter<CharSequence>(context,
                    R.layout.simple_list_item_1, entries));
        }

        final Drawable d = a.getDrawable(R.styleable.ListView_divider);
        if (d != null) {
            // If a divider is specified use its intrinsic height for divider height
            setDivider(d);
        }
        
        final Drawable osHeader = a.getDrawable(
                R.styleable.ListView_overScrollHeader);
        if (osHeader != null) {
            setOverscrollHeader(osHeader);
        }

        final Drawable osFooter = a.getDrawable(
                R.styleable.ListView_overScrollFooter);
        if (osFooter != null) {
            setOverscrollFooter(osFooter);
        }

        // Use the height specified, zero being the default
        final int dividerHeight = a.getDimensionPixelSize(
                R.styleable.ListView_dividerHeight, 0);
        if (dividerHeight != 0) {
            setDividerHeight(dividerHeight);
        }

        mHeaderDividersEnabled = a.getBoolean(R.styleable.ListView_headerDividersEnabled, true);
        mFooterDividersEnabled = a.getBoolean(R.styleable.ListView_footerDividersEnabled, true);
        mFooterDividersEnabled = false;

        //tws-start::get attrs related to empty view 2013-06-07
        mEmptyListBackground = a.getResourceId(R.styleable.ListView_emptyListBackground, 0);
        mEmptyListIcon = a.getResourceId(R.styleable.ListView_emptyListIcon, 0);
        mEmptyListText = a.getResourceId(R.styleable.ListView_emptyListText, 0);
        mEmptyListSecondText = a.getResourceId(R.styleable.ListView_emptyListSecondText, 0);

        if(mEmptyListIcon == 0 && mEmptyListText == 0 && mEmptyListSecondText == 0) {
            // if none of emptyListIcon, emptyListText or emptyListSecondText is specified, 
            // empty view will not be displayed
            mShowEmptyList = false;
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getMetrics(metrics);
            sPixelDensity = metrics.density;
            mEmptyListMinMargin = dpToPixel(MIN_MARGIN);

            mEmptyListTextAppearanceId = a.getResourceId(R.styleable.ListView_emptyListTextAppearance, 0);
            mEmptyListSecondTextAppearanceId = a.getResourceId(R.styleable.ListView_emptyListSecondTextAppearance, 0);
            mEmptyListMarginLeft = a.getDimensionPixelSize(R.styleable.ListView_emptyListMarginLeft, mEmptyListMinMargin);
            mEmptyListMarginTop = a.getDimensionPixelSize(R.styleable.ListView_emptyListMarginTop, mEmptyListMinMargin);
            mEmptyListMarginRight = a.getDimensionPixelSize(R.styleable.ListView_emptyListMarginRight, mEmptyListMinMargin);
            mEmptyListMarginBottom = a.getDimensionPixelSize(R.styleable.ListView_emptyListMarginBottom, mEmptyListMinMargin);
            mEmptyListMaxHeight = a.getDimensionPixelSize(R.styleable.ListView_emptyListMaxHeight, 0);
        }
        //tws-end::get attrs related to empty view 2013-06-07

        mSelectedStateEnabled = a.getBoolean(R.styleable.ListView_enableSelectedState, false);

        a.recycle();

        //a = context.obtainStyledAttributes(new int[] {R.attr.activatedBackgroundIndicator});
        //mActivatedBackgroundIndicator = a.getResourceId(0, 0);
        //a.recycle();

        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.activatedBackgroundIndicator, value, true);
        mActivatedBackgroundIndicator = value.resourceId;
        
        mBlurBottomHeight = 0;
		mBlurTopHeight = 0;
    }

    /**
     * @return The maximum amount a list view will scroll in response to
     *   an arrow event.
     */
    public int getMaxScrollAmount() {
        return (int) (MAX_SCROLL_FACTOR * (mBottom - mTop));
    }

    /**
     * Make sure views are touching the top or bottom edge, as appropriate for
     * our gravity
     */
    private void adjustViewsUpOrDown() {
        final int childCount = getChildCount();
        int delta;

        if (childCount > 0) {
            View child;

            if (!mStackFromBottom) {
                // Uh-oh -- we came up short. Slide all views up to make them
                // align with the top
                child = getChildAt(0);
                //tws-start add listview bounce feature::2014-08-12
                delta = child.getTop() - mListPadding.top - mFirstTopOffset;
                //tws-end add listview bounce feature::2014-08-12
                if (mFirstPosition != 0) {
                    // It's OK to have some space above the first item if it is
                    // part of the vertical spacing
                    delta -= mDividerHeight;
                }
                if (delta < 0) {
                    // We only are looking to see if we are too low, not too high
                    delta = 0;
                }
            } else {
                // we are too high, slide all views down to align with bottom
                child = getChildAt(childCount - 1);
                delta = child.getBottom() - (getHeight() - mListPadding.bottom);

                if (mFirstPosition + childCount < mItemCount) {
                    // It's OK to have some space below the last item if it is
                    // part of the vertical spacing
                    delta += mDividerHeight;
                }

                if (delta > 0) {
                    delta = 0;
                }
            }

            if (delta != 0 && !mIsTouchOffsetFlag) {
            	offsetChildrenTopAndBottom(-delta);
            }
        }
    }

    /**
     * Add a fixed view to appear at the top of the list. If this method is
     * called more than once, the views will appear in the order they were
     * added. Views added using this call can take focus if they want.
     * <p>
     * Note: When first introduced, this method could only be called before
     * setting the adapter with {@link #setAdapter(ListAdapter)}. Starting with
     * {@link android.os.Build.VERSION_CODES#KITKAT}, this method may be
     * called at any time. If the ListView's adapter does not extend
     * {@link HeaderViewListAdapter}, it will be wrapped with a supporting
     * instance of {@link WrapperListAdapter}.
     *
     * @param v The view to add.
     * @param data Data to associate with this view
     * @param isSelectable whether the item is selectable
     */
    public void addHeaderView(View v, Object data, boolean isSelectable) {
        final FixedViewInfo info = new FixedViewInfo();
        info.view = v;
        info.data = data;
        info.isSelectable = isSelectable;
        mHeaderViewInfos.add(info);
        mAreAllItemsSelectable &= isSelectable;

        // Wrap the adapter if it wasn't already wrapped.
        if (mAdapter != null) {
            if (!(mAdapter instanceof HeaderViewListAdapter)) {
                mAdapter = new HeaderViewListAdapter(mHeaderViewInfos, mFooterViewInfos, mAdapter);
            }

            // In the case of re-adding a header view, or adding one later on,
            // we need to notify the observer.
            if (mDataSetObserver != null) {
                mDataSetObserver.onChanged();
            }
        }
    }

    /**
     * Add a fixed view to appear at the top of the list. If addHeaderView is
     * called more than once, the views will appear in the order they were
     * added. Views added using this call can take focus if they want.
     * <p>
     * Note: When first introduced, this method could only be called before
     * setting the adapter with {@link #setAdapter(ListAdapter)}. Starting with
     * {@link android.os.Build.VERSION_CODES#KITKAT}, this method may be
     * called at any time. If the ListView's adapter does not extend
     * {@link HeaderViewListAdapter}, it will be wrapped with a supporting
     * instance of {@link WrapperListAdapter}.
     *
     * @param v The view to add.
     */
    public void addHeaderView(View v) {
        addHeaderView(v, null, true);
    }

    @Override
    public int getHeaderViewsCount() {
        return mHeaderViewInfos.size();
    }

    /**
     * Removes a previously-added header view.
     *
     * @param v The view to remove
     * @return true if the view was removed, false if the view was not a header
     *         view
     */
    public boolean removeHeaderView(View v) {
        if (mHeaderViewInfos.size() > 0) {
            boolean result = false;
            if (mAdapter != null && ((HeaderViewListAdapter) mAdapter).removeHeader(v)) {
                if (mDataSetObserver != null) {
                    mDataSetObserver.onChanged();
                }
                result = true;
            }
            removeFixedViewInfo(v, mHeaderViewInfos);
            return result;
        }
        return false;
    }

    private void removeFixedViewInfo(View v, ArrayList<FixedViewInfo> where) {
        int len = where.size();
        for (int i = 0; i < len; ++i) {
            FixedViewInfo info = where.get(i);
            if (info.view == v) {
                where.remove(i);
                break;
            }
        }
    }

    /**
     * Add a fixed view to appear at the bottom of the list. If addFooterView is
     * called more than once, the views will appear in the order they were
     * added. Views added using this call can take focus if they want.
     * <p>
     * Note: When first introduced, this method could only be called before
     * setting the adapter with {@link #setAdapter(ListAdapter)}. Starting with
     * {@link android.os.Build.VERSION_CODES#KITKAT}, this method may be
     * called at any time. If the ListView's adapter does not extend
     * {@link HeaderViewListAdapter}, it will be wrapped with a supporting
     * instance of {@link WrapperListAdapter}.
     *
     * @param v The view to add.
     * @param data Data to associate with this view
     * @param isSelectable true if the footer view can be selected
     */
    public void addFooterView(View v, Object data, boolean isSelectable) {
        final FixedViewInfo info = new FixedViewInfo();
        info.view = v;
        info.data = data;
        info.isSelectable = isSelectable;
    	if (mBlurBottomHeight == 0) {
    		mFooterViewInfos.add(info);
    	}
    	else {
    		mFooterViewInfos.clear();
    		if (info.view == mBottomBlurView) {
    			blurInfo = info;
    		}
    		if (info.view != mBottomBlurView) {
    			mTempFooterViewInfos.add(info);
    		}
    		if (mTempFooterViewInfos != null) {
    			mFooterViewInfos.addAll(mTempFooterViewInfos);
    		}
    		mFooterViewInfos.add(blurInfo);
    	}
        mAreAllItemsSelectable &= isSelectable;

        // Wrap the adapter if it wasn't already wrapped.
        if (mAdapter != null) {
            if (!(mAdapter instanceof HeaderViewListAdapter)) {
                mAdapter = new HeaderViewListAdapter(mHeaderViewInfos, mFooterViewInfos, mAdapter);
            }

            // In the case of re-adding a footer view, or adding one later on,
            // we need to notify the observer.
            if (mDataSetObserver != null) {
                mDataSetObserver.onChanged();
            }
        }
    }

    /**
     * Add a fixed view to appear at the bottom of the list. If addFooterView is
     * called more than once, the views will appear in the order they were
     * added. Views added using this call can take focus if they want.
     * <p>
     * Note: When first introduced, this method could only be called before
     * setting the adapter with {@link #setAdapter(ListAdapter)}. Starting with
     * {@link android.os.Build.VERSION_CODES#KITKAT}, this method may be
     * called at any time. If the ListView's adapter does not extend
     * {@link HeaderViewListAdapter}, it will be wrapped with a supporting
     * instance of {@link WrapperListAdapter}.
     *
     * @param v The view to add.
     */
    public void addFooterView(View v) {
        addFooterView(v, null, true);
    }

    @Override
    public int getFooterViewsCount() {
        return mFooterViewInfos.size();
    }

    /**
     * Removes a previously-added footer view.
     *
     * @param v The view to remove
     * @return
     * true if the view was removed, false if the view was not a footer view
     */
    public boolean removeFooterView(View v) {
        if (mFooterViewInfos.size() > 0) {
            boolean result = false;
            if (mAdapter != null && ((HeaderViewListAdapter) mAdapter).removeFooter(v)) {
                if (mDataSetObserver != null) {
                    mDataSetObserver.onChanged();
                }
                result = true;
            }
            removeFixedViewInfo(v, mFooterViewInfos);
            return result;
        }
        return false;
    }

    /**
     * Returns the adapter currently in use in this ListView. The returned adapter
     * might not be the same adapter passed to {@link #setAdapter(ListAdapter)} but
     * might be a {@link WrapperListAdapter}.
     *
     * @return The adapter currently used to display data in this ListView.
     *
     * @see #setAdapter(ListAdapter)
     */
    @Override
    public ListAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Sets up this AbsListView to use a remote views adapter which connects to a RemoteViewsService
     * through the specified intent.
     * @param intent the intent used to identify the RemoteViewsService for the adapter to connect to.
     */
    @android.view.RemotableViewMethod
    public void setRemoteViewsAdapter(Intent intent) {
        super.setRemoteViewsAdapter(intent);
    }

    /**
     * Sets the data behind this ListView.
     *
     * The adapter passed to this method may be wrapped by a {@link WrapperListAdapter},
     * depending on the ListView features currently in use. For instance, adding
     * headers and/or footers will cause the adapter to be wrapped.
     *
     * @param adapter The ListAdapter which is responsible for maintaining the
     *        data backing this list and for producing a view to represent an
     *        item in that data set.
     *
     * @see #getAdapter() 
     */
    @Override
    public void setAdapter(ListAdapter adapter) {
        if (mAdapter != null && mDataSetObserver != null) {
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
        }

        blurSetup();
        
        resetList();
        mRecycler.clear();

        if (mHeaderViewInfos.size() > 0|| mFooterViewInfos.size() > 0) {
            mAdapter = new HeaderViewListAdapter(mHeaderViewInfos, mFooterViewInfos, adapter);
        } else {
            mAdapter = adapter;
        }

        mOldSelectedPosition = INVALID_POSITION;
        mOldSelectedRowId = INVALID_ROW_ID;

        // AbsListView#setAdapter will update choice mode states.
        super.setAdapter(adapter);

        if (mAdapter != null) {
            mAreAllItemsSelectable = mAdapter.areAllItemsEnabled();
            mOldItemCount = mItemCount;
            mItemCount = mAdapter.getCount();
            checkFocus();

            mDataSetObserver = new AdapterDataSetObserver();
            mAdapter.registerDataSetObserver(mDataSetObserver);

            mRecycler.setViewTypeCount(mAdapter.getViewTypeCount());

            int position;
            if (mStackFromBottom) {
                position = lookForSelectablePosition(mItemCount - 1, false);
            } else {
                position = lookForSelectablePosition(0, true);
            }
            setSelectedPositionInt(position);
            setNextSelectedPositionInt(position);

            if (mItemCount == 0) {
                // Nothing selected
                checkSelectionChanged();
            }
        } else {
            mAreAllItemsSelectable = true;
            checkFocus();
            // Nothing selected
            checkSelectionChanged();
        }

        requestLayout();
    }

    /**
     * The list is empty. Clear everything out.
     */
    @Override
    void resetList() {
        // The parent's resetList() will remove all views from the layout so we need to
        // cleanup the state of our footers and headers
        clearRecycledState(mHeaderViewInfos);
        clearRecycledState(mFooterViewInfos);

        super.resetList();

        mLayoutMode = LAYOUT_NORMAL;
    }

    private void clearRecycledState(ArrayList<FixedViewInfo> infos) {
        if (infos != null) {
            final int count = infos.size();

            for (int i = 0; i < count; i++) {
                final View child = infos.get(i).view;
                final LayoutParams p = (LayoutParams) child.getLayoutParams();
                if (p != null) {
                    p.recycledHeaderFooter = false;
                }
            }
        }
    }

    /**
     * @return Whether the list needs to show the top fading edge
     */
    private boolean showingTopFadingEdge() {
        final int listTop = mScrollY + mListPadding.top;
        return (mFirstPosition > 0) || (getChildAt(0).getTop() > listTop);
    }

    /**
     * @return Whether the list needs to show the bottom fading edge
     */
    private boolean showingBottomFadingEdge() {
        final int childCount = getChildCount();
        final int bottomOfBottomChild = getChildAt(childCount - 1).getBottom();
        final int lastVisiblePosition = mFirstPosition + childCount - 1;

        final int listBottom = mScrollY + getHeight() - mListPadding.bottom;

        return (lastVisiblePosition < mItemCount - 1)
                         || (bottomOfBottomChild < listBottom);
    }


    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {

        int rectTopWithinChild = rect.top;

        // offset so rect is in coordinates of the this view
        rect.offset(child.getLeft(), child.getTop());
        rect.offset(-child.getScrollX(), -child.getScrollY());

        final int height = getHeight();
        int listUnfadedTop = getScrollY();
        int listUnfadedBottom = listUnfadedTop + height;
        final int fadingEdge = getVerticalFadingEdgeLength();

        if (showingTopFadingEdge()) {
            // leave room for top fading edge as long as rect isn't at very top
            if ((mSelectedPosition > 0) || (rectTopWithinChild > fadingEdge)) {
                listUnfadedTop += fadingEdge;
            }
        }

        int childCount = getChildCount();
        int bottomOfBottomChild = getChildAt(childCount - 1).getBottom();

        if (showingBottomFadingEdge()) {
            // leave room for bottom fading edge as long as rect isn't at very bottom
            if ((mSelectedPosition < mItemCount - 1)
                    || (rect.bottom < (bottomOfBottomChild - fadingEdge))) {
                listUnfadedBottom -= fadingEdge;
            }
        }

        int scrollYDelta = 0;

        if (rect.bottom > listUnfadedBottom && rect.top > listUnfadedTop) {
            // need to MOVE DOWN to get it in view: move down just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).

            if (rect.height() > height) {
                // just enough to get screen size chunk on
                scrollYDelta += (rect.top - listUnfadedTop);
            } else {
                // get entire rect at bottom of screen
                scrollYDelta += (rect.bottom - listUnfadedBottom);
            }

            // make sure we aren't scrolling beyond the end of our children
            int distanceToBottom = bottomOfBottomChild - listUnfadedBottom;
            scrollYDelta = Math.min(scrollYDelta, distanceToBottom);
        } else if (rect.top < listUnfadedTop && rect.bottom < listUnfadedBottom) {
            // need to MOVE UP to get it in view: move up just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).

            if (rect.height() > height) {
                // screen size chunk
                scrollYDelta -= (listUnfadedBottom - rect.bottom);
            } else {
                // entire rect at top
                scrollYDelta -= (listUnfadedTop - rect.top);
            }

            // make sure we aren't scrolling any further than the top our children
            int top = getChildAt(0).getTop();
            int deltaToTop = top - listUnfadedTop;
            scrollYDelta = Math.max(scrollYDelta, deltaToTop);
        }

        final boolean scroll = scrollYDelta != 0;
        if (scroll) {
            scrollListItemsBy(-scrollYDelta);
            positionSelector(INVALID_POSITION, child);
            mSelectedTop = child.getTop();
            invalidate();
        }
        return scroll;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void fillGap(boolean down) {
        final int count = getChildCount();
        if (down) {
            int paddingTop = 0;
            if ((mGroupFlags & CLIP_TO_PADDING_MASK) == CLIP_TO_PADDING_MASK) {
                paddingTop = getListPaddingTop();
            }
            //tws-start add listview bounce feature::2014-08-12
            final int startOffset = count > 0 ? getChildAt(count - 1).getBottom() + mDividerHeight :
                    paddingTop + mFirstTop + mFirstTopOffset;
            //tws-end add listview bounce feature::2014-08-12
            fillDown(mFirstPosition + count, startOffset);
            if (!isNeedBounce())
            	correctTooHigh(getChildCount());
        } else {
            int paddingBottom = 0;
            if ((mGroupFlags & CLIP_TO_PADDING_MASK) == CLIP_TO_PADDING_MASK) {
                paddingBottom = getListPaddingBottom();
            }
            //tws-start add listview bounce feature::2014-08-12
            final int startOffset = count > 0 ? getChildAt(0).getTop() - mDividerHeight :
                    getHeight() - paddingBottom - mLastBottomOffset;
            //tws-end add listview bounce feature::2014-08-12
            fillUp(mFirstPosition - 1, startOffset);
            if (!isNeedBounce())
            	correctTooLow(getChildCount());
        }
    }

    /**
     * Fills the list from pos down to the end of the list view.
     *
     * @param pos The first position to put in the list
     *
     * @param nextTop The location where the top of the item associated with pos
     *        should be drawn
     *
     * @return The view that is currently selected, if it happens to be in the
     *         range that we draw.
     */
    private View fillDown(int pos, int nextTop) {
        View selectedView = null;

        int end = (mBottom - mTop);
        if ((mGroupFlags & CLIP_TO_PADDING_MASK) == CLIP_TO_PADDING_MASK) {
            end -= mListPadding.bottom;
        }

        while (nextTop < end && pos < mItemCount) {
            // is this the selected item?
            boolean selected = pos == mSelectedPosition;
            View child = makeAndAddView(pos, nextTop, true, mListPadding.left, selected);

            nextTop = child.getBottom() + mDividerHeight;
            if (selected) {
                selectedView = child;
            }
            pos++;
        }

        setVisibleRangeHint(mFirstPosition, mFirstPosition + getChildCount() - 1);
        return selectedView;
    }

    /**
     * Fills the list from pos up to the top of the list view.
     *
     * @param pos The first position to put in the list
     *
     * @param nextBottom The location where the bottom of the item associated
     *        with pos should be drawn
     *
     * @return The view that is currently selected
     */
    private View fillUp(int pos, int nextBottom) {
        View selectedView = null;

        int end = 0;
        if ((mGroupFlags & CLIP_TO_PADDING_MASK) == CLIP_TO_PADDING_MASK) {
            end = mListPadding.top;
        }

        while (nextBottom > end && pos >= 0) {
            // is this the selected item?
            boolean selected = pos == mSelectedPosition;
            View child = makeAndAddView(pos, nextBottom, false, mListPadding.left, selected);
            nextBottom = child.getTop() - mDividerHeight;
            if (selected) {
                selectedView = child;
            }
            pos--;
        }

        mFirstPosition = pos + 1;
        setVisibleRangeHint(mFirstPosition, mFirstPosition + getChildCount() - 1);
        return selectedView;
    }

    /**
     * Fills the list from top to bottom, starting with mFirstPosition
     *
     * @param nextTop The location where the top of the first item should be
     *        drawn
     *
     * @return The view that is currently selected
     */
    private View fillFromTop(int nextTop) {
        mFirstPosition = Math.min(mFirstPosition, mSelectedPosition);
        mFirstPosition = Math.min(mFirstPosition, mItemCount - 1);
        if (mFirstPosition < 0) {
            mFirstPosition = 0;
        }
        return fillDown(mFirstPosition, nextTop);
    }


    /**
     * Put mSelectedPosition in the middle of the screen and then build up and
     * down from there. This method forces mSelectedPosition to the center.
     *
     * @param childrenTop Top of the area in which children can be drawn, as
     *        measured in pixels
     * @param childrenBottom Bottom of the area in which children can be drawn,
     *        as measured in pixels
     * @return Currently selected view
     */
    private View fillFromMiddle(int childrenTop, int childrenBottom) {
        int height = childrenBottom - childrenTop;

        int position = reconcileSelectedPosition();

        View sel = makeAndAddView(position, childrenTop, true,
                mListPadding.left, true);
        mFirstPosition = position;

        int selHeight = sel.getMeasuredHeight();
        if (selHeight <= height) {
            sel.offsetTopAndBottom((height - selHeight) / 2);
        }

        fillAboveAndBelow(sel, position);

        if (!mStackFromBottom) {
            correctTooHigh(getChildCount());
        } else {
            correctTooLow(getChildCount());
        }

        return sel;
    }

    /**
     * Once the selected view as been placed, fill up the visible area above and
     * below it.
     *
     * @param sel The selected view
     * @param position The position corresponding to sel
     */
    private void fillAboveAndBelow(View sel, int position) {
        final int dividerHeight = mDividerHeight;
        if (!mStackFromBottom) {
            fillUp(position - 1, sel.getTop() - dividerHeight);
            adjustViewsUpOrDown();
            fillDown(position + 1, sel.getBottom() + dividerHeight);
        } else {
            fillDown(position + 1, sel.getBottom() + dividerHeight);
            adjustViewsUpOrDown();
            fillUp(position - 1, sel.getTop() - dividerHeight);
        }
    }


    /**
     * Fills the grid based on positioning the new selection at a specific
     * location. The selection may be moved so that it does not intersect the
     * faded edges. The grid is then filled upwards and downwards from there.
     *
     * @param selectedTop Where the selected item should be
     * @param childrenTop Where to start drawing children
     * @param childrenBottom Last pixel where children can be drawn
     * @return The view that currently has selection
     */
    private View fillFromSelection(int selectedTop, int childrenTop, int childrenBottom) {
        int fadingEdgeLength = getVerticalFadingEdgeLength();
        final int selectedPosition = mSelectedPosition;

        View sel;

        final int topSelectionPixel = getTopSelectionPixel(childrenTop, fadingEdgeLength,
                selectedPosition);
        final int bottomSelectionPixel = getBottomSelectionPixel(childrenBottom, fadingEdgeLength,
                selectedPosition);

        sel = makeAndAddView(selectedPosition, selectedTop, true, mListPadding.left, true);


        // Some of the newly selected item extends below the bottom of the list
        if (sel.getBottom() > bottomSelectionPixel) {
            // Find space available above the selection into which we can scroll
            // upwards
            final int spaceAbove = sel.getTop() - topSelectionPixel;

            // Find space required to bring the bottom of the selected item
            // fully into view
            final int spaceBelow = sel.getBottom() - bottomSelectionPixel;
            final int offset = Math.min(spaceAbove, spaceBelow);

            // Now offset the selected item to get it into view
            sel.offsetTopAndBottom(-offset);
        } else if (sel.getTop() < topSelectionPixel) {
            // Find space required to bring the top of the selected item fully
            // into view
            final int spaceAbove = topSelectionPixel - sel.getTop();

            // Find space available below the selection into which we can scroll
            // downwards
            final int spaceBelow = bottomSelectionPixel - sel.getBottom();
            final int offset = Math.min(spaceAbove, spaceBelow);

            // Offset the selected item to get it into view
            sel.offsetTopAndBottom(offset);
        }

        // Fill in views above and below
        fillAboveAndBelow(sel, selectedPosition);

        if (!mStackFromBottom) {
            correctTooHigh(getChildCount());
        } else {
            correctTooLow(getChildCount());
        }

        return sel;
    }

    /**
     * Calculate the bottom-most pixel we can draw the selection into
     *
     * @param childrenBottom Bottom pixel were children can be drawn
     * @param fadingEdgeLength Length of the fading edge in pixels, if present
     * @param selectedPosition The position that will be selected
     * @return The bottom-most pixel we can draw the selection into
     */
    private int getBottomSelectionPixel(int childrenBottom, int fadingEdgeLength,
            int selectedPosition) {
        int bottomSelectionPixel = childrenBottom;
        if (selectedPosition != mItemCount - 1) {
            bottomSelectionPixel -= fadingEdgeLength;
        }
        return bottomSelectionPixel;
    }

    /**
     * Calculate the top-most pixel we can draw the selection into
     *
     * @param childrenTop Top pixel were children can be drawn
     * @param fadingEdgeLength Length of the fading edge in pixels, if present
     * @param selectedPosition The position that will be selected
     * @return The top-most pixel we can draw the selection into
     */
    private int getTopSelectionPixel(int childrenTop, int fadingEdgeLength, int selectedPosition) {
        // first pixel we can draw the selection into
        int topSelectionPixel = childrenTop;
        if (selectedPosition > 0) {
            topSelectionPixel += fadingEdgeLength;
        }
        return topSelectionPixel;
    }

    /**
     * Smoothly scroll to the specified adapter position. The view will
     * scroll such that the indicated position is displayed.
     * @param position Scroll to this adapter position.
     */
    @android.view.RemotableViewMethod
    public void smoothScrollToPosition(int position) {
        super.smoothScrollToPosition(position);
    }

    /**
     * Smoothly scroll to the specified adapter position offset. The view will
     * scroll such that the indicated position is displayed.
     * @param offset The amount to offset from the adapter position to scroll to.
     */
    @android.view.RemotableViewMethod
    public void smoothScrollByOffset(int offset) {
        super.smoothScrollByOffset(offset);
    }

    /**
     * Fills the list based on positioning the new selection relative to the old
     * selection. The new selection will be placed at, above, or below the
     * location of the new selection depending on how the selection is moving.
     * The selection will then be pinned to the visible part of the screen,
     * excluding the edges that are faded. The list is then filled upwards and
     * downwards from there.
     *
     * @param oldSel The old selected view. Useful for trying to put the new
     *        selection in the same place
     * @param newSel The view that is to become selected. Useful for trying to
     *        put the new selection in the same place
     * @param delta Which way we are moving
     * @param childrenTop Where to start drawing children
     * @param childrenBottom Last pixel where children can be drawn
     * @return The view that currently has selection
     */
    private View moveSelection(View oldSel, View newSel, int delta, int childrenTop,
            int childrenBottom) {
        int fadingEdgeLength = getVerticalFadingEdgeLength();
        final int selectedPosition = mSelectedPosition;

        View sel;

        final int topSelectionPixel = getTopSelectionPixel(childrenTop, fadingEdgeLength,
                selectedPosition);
        final int bottomSelectionPixel = getBottomSelectionPixel(childrenTop, fadingEdgeLength,
                selectedPosition);

        if (delta > 0) {
            /*
             * Case 1: Scrolling down.
             */

            /*
             *     Before           After
             *    |       |        |       |
             *    +-------+        +-------+
             *    |   A   |        |   A   |
             *    |   1   |   =>   +-------+
             *    +-------+        |   B   |
             *    |   B   |        |   2   |
             *    +-------+        +-------+
             *    |       |        |       |
             *
             *    Try to keep the top of the previously selected item where it was.
             *    oldSel = A
             *    sel = B
             */

            // Put oldSel (A) where it belongs
            oldSel = makeAndAddView(selectedPosition - 1, oldSel.getTop(), true,
                    mListPadding.left, false);

            final int dividerHeight = mDividerHeight;

            // Now put the new selection (B) below that
            sel = makeAndAddView(selectedPosition, oldSel.getBottom() + dividerHeight, true,
                    mListPadding.left, true);

            // Some of the newly selected item extends below the bottom of the list
            if (sel.getBottom() > bottomSelectionPixel) {

                // Find space available above the selection into which we can scroll upwards
                int spaceAbove = sel.getTop() - topSelectionPixel;

                // Find space required to bring the bottom of the selected item fully into view
                int spaceBelow = sel.getBottom() - bottomSelectionPixel;

                // Don't scroll more than half the height of the list
                int halfVerticalSpace = (childrenBottom - childrenTop) / 2;
                int offset = Math.min(spaceAbove, spaceBelow);
                offset = Math.min(offset, halfVerticalSpace);

                // We placed oldSel, so offset that item
                oldSel.offsetTopAndBottom(-offset);
                // Now offset the selected item to get it into view
                sel.offsetTopAndBottom(-offset);
            }

            // Fill in views above and below
            if (!mStackFromBottom) {
                fillUp(mSelectedPosition - 2, sel.getTop() - dividerHeight);
                adjustViewsUpOrDown();
                fillDown(mSelectedPosition + 1, sel.getBottom() + dividerHeight);
            } else {
                fillDown(mSelectedPosition + 1, sel.getBottom() + dividerHeight);
                adjustViewsUpOrDown();
                fillUp(mSelectedPosition - 2, sel.getTop() - dividerHeight);
            }
        } else if (delta < 0) {
            /*
             * Case 2: Scrolling up.
             */

            /*
             *     Before           After
             *    |       |        |       |
             *    +-------+        +-------+
             *    |   A   |        |   A   |
             *    +-------+   =>   |   1   |
             *    |   B   |        +-------+
             *    |   2   |        |   B   |
             *    +-------+        +-------+
             *    |       |        |       |
             *
             *    Try to keep the top of the item about to become selected where it was.
             *    newSel = A
             *    olSel = B
             */

            if (newSel != null) {
                // Try to position the top of newSel (A) where it was before it was selected
                sel = makeAndAddView(selectedPosition, newSel.getTop(), true, mListPadding.left,
                        true);
            } else {
                // If (A) was not on screen and so did not have a view, position
                // it above the oldSel (B)
                sel = makeAndAddView(selectedPosition, oldSel.getTop(), false, mListPadding.left,
                        true);
            }

            // Some of the newly selected item extends above the top of the list
            if (sel.getTop() < topSelectionPixel) {
                // Find space required to bring the top of the selected item fully into view
                int spaceAbove = topSelectionPixel - sel.getTop();

               // Find space available below the selection into which we can scroll downwards
                int spaceBelow = bottomSelectionPixel - sel.getBottom();

                // Don't scroll more than half the height of the list
                int halfVerticalSpace = (childrenBottom - childrenTop) / 2;
                int offset = Math.min(spaceAbove, spaceBelow);
                offset = Math.min(offset, halfVerticalSpace);

                // Offset the selected item to get it into view
                sel.offsetTopAndBottom(offset);
            }

            // Fill in views above and below
            fillAboveAndBelow(sel, selectedPosition);
        } else {

            int oldTop = oldSel.getTop();

            /*
             * Case 3: Staying still
             */
            sel = makeAndAddView(selectedPosition, oldTop, true, mListPadding.left, true);

            // We're staying still...
            if (oldTop < childrenTop) {
                // ... but the top of the old selection was off screen.
                // (This can happen if the data changes size out from under us)
                int newBottom = sel.getBottom();
                if (newBottom < childrenTop + 20) {
                    // Not enough visible -- bring it onscreen
                    sel.offsetTopAndBottom(childrenTop - sel.getTop());
                }
            }

            // Fill in views above and below
            fillAboveAndBelow(sel, selectedPosition);
        }

        return sel;
    }

    private class FocusSelector implements Runnable {
        private int mPosition;
        private int mPositionTop;
        
        public FocusSelector setup(int position, int top) {
            mPosition = position;
            mPositionTop = top;
            return this;
        }
        
        public void run() {
            setSelectionFromTop(mPosition, mPositionTop);
        }
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (getChildCount() > 0) {
            View focusedChild = getFocusedChild();
            if (focusedChild != null) {
                final int childPosition = mFirstPosition + indexOfChild(focusedChild);
                final int childBottom = focusedChild.getBottom();
                final int offset = Math.max(0, childBottom - (h - mPaddingTop));
                final int top = focusedChild.getTop() - offset;
                if (mFocusSelector == null) {
                    mFocusSelector = new FocusSelector();
                }
                post(mFocusSelector.setup(childPosition, top));
            }
        }
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
			blurInit();
		}
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Sets up mListPadding
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int childWidth = 0;
        int childHeight = 0;
        int childState = 0;

        mItemCount = mAdapter == null ? 0 : mAdapter.getCount();
        if (mItemCount > 0 && (widthMode == MeasureSpec.UNSPECIFIED
                || heightMode == MeasureSpec.UNSPECIFIED)) {
            final View child = obtainView(0, mIsScrap);

            // Lay out child directly against the parent measure spec so that
            // we can obtain exected minimum width and height.
            measureScrapChild(child, 0, widthMeasureSpec, heightSize);

            childWidth = child.getMeasuredWidth();
            childHeight = child.getMeasuredHeight();
            childState = combineMeasuredStates(childState, child.getMeasuredState());

            if (recycleOnMeasure() && mRecycler.shouldRecycleViewType(
                    ((LayoutParams) child.getLayoutParams()).viewType)) {
                mRecycler.addScrapView(child, 0);
            }
        }

        if (widthMode == MeasureSpec.UNSPECIFIED) {
            widthSize = mListPadding.left + mListPadding.right + childWidth +
                    getVerticalScrollbarWidth();
        } else {
            widthSize |= (childState & MEASURED_STATE_MASK);
        }

        //tws-start add listview bounce feature::2014-08-12
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            heightSize = mListPadding.top + mListPadding.bottom + childHeight +
                    getVerticalFadingEdgeLength() * 2 + mFirstTopOffset + mLastBottomOffset;
        }
        //tws-end add listview bounce feature::2014-08-12

        if (heightMode == MeasureSpec.AT_MOST) {
            // TODO: after first layout we should maybe start at the first visible position, not 0
            heightSize = measureHeightOfChildren(widthMeasureSpec, 0, NO_POSITION, heightSize, -1);
        }

        setMeasuredDimension(widthSize, heightSize);

        mWidthMeasureSpec = widthMeasureSpec;
    }

    private void measureScrapChild(View child, int position, int widthMeasureSpec, int heightHint) {
        LayoutParams p = (LayoutParams) child.getLayoutParams();
        if (p == null) {
            p = (AbsListView.LayoutParams) generateDefaultLayoutParams();
            child.setLayoutParams(p);
        }
        p.viewType = mAdapter.getItemViewType(position);
        p.forceAdd = true;

        final int childWidthSpec = ViewGroup.getChildMeasureSpec(widthMeasureSpec,
                mListPadding.left + mListPadding.right, p.width);
        final int lpHeight = p.height;
        final int childHeightSpec;
        if (lpHeight > 0) {
            childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY);
        } else {
            if(android.os.Build.VERSION.SDK_INT < 23) {
                childHeightSpec = 0;
            } else {
                childHeightSpec = MeasureSpec.makeMeasureSpec(heightHint, MeasureSpec.UNSPECIFIED);
            }
        }
        child.measure(childWidthSpec, childHeightSpec);

        // Since this view was measured directly aginst the parent measure
        // spec, we must measure it again before reuse.
        child.forceLayout();
    }

    /**
     * @return True to recycle the views used to measure this ListView in
     *         UNSPECIFIED/AT_MOST modes, false otherwise.
     * @hide
     */
    @ViewDebug.ExportedProperty(category = "list")
    protected boolean recycleOnMeasure() {
        return true;
    }

    /**
     * Measures the height of the given range of children (inclusive) and
     * returns the height with this ListView's padding and divider heights
     * included. If maxHeight is provided, the measuring will stop when the
     * current height reaches maxHeight.
     *
     * @param widthMeasureSpec The width measure spec to be given to a child's
     *            {@link View#measure(int, int)}.
     * @param startPosition The position of the first child to be shown.
     * @param endPosition The (inclusive) position of the last child to be
     *            shown. Specify {@link #NO_POSITION} if the last child should be
     *            the last available child from the adapter.
     * @param maxHeight The maximum height that will be returned (if all the
     *            children don't fit in this value, this value will be
     *            returned).
     * @param disallowPartialChildPosition In general, whether the returned
     *            height should only contain entire children. This is more
     *            powerful--it is the first inclusive position at which partial
     *            children will not be allowed. Example: it looks nice to have
     *            at least 3 completely visible children, and in portrait this
     *            will most likely fit; but in landscape there could be times
     *            when even 2 children can not be completely shown, so a value
     *            of 2 (remember, inclusive) would be good (assuming
     *            startPosition is 0).
     * @return The height of this ListView with the given children.
     */
    final int measureHeightOfChildren(int widthMeasureSpec, int startPosition, int endPosition,
            int maxHeight, int disallowPartialChildPosition) {
        final ListAdapter adapter = mAdapter;
        //tws-start add listview bounce feature::2014-08-12
        if (adapter == null) {
            return mListPadding.top + mListPadding.bottom + mFirstTopOffset + mLastBottomOffset;
        }
        //tws-end add listview bounce feature::2014-08-12

        // Include the padding of the list
        //tws-start add listview bounce feature::2014-08-12
        int returnedHeight = mListPadding.top + mListPadding.bottom + mFirstTopOffset + mLastBottomOffset;
        //tws-end add listview bounce feature::2014-08-12
        final int dividerHeight = ((mDividerHeight > 0) && mDivider != null) ? mDividerHeight : 0;
        // The previous height value that was less than maxHeight and contained
        // no partial children
        int prevHeightWithoutPartialChild = 0;
        int i;
        View child;

        // mItemCount - 1 since endPosition parameter is inclusive
        endPosition = (endPosition == NO_POSITION) ? adapter.getCount() - 1 : endPosition;
        final AbsListView.RecycleBin recycleBin = mRecycler;
        final boolean recyle = recycleOnMeasure();
        final boolean[] isScrap = mIsScrap;

        for (i = startPosition; i <= endPosition; ++i) {
            child = obtainView(i, isScrap);

            measureScrapChild(child, i, widthMeasureSpec, maxHeight);

            if (i > 0) {
                // Count the divider for all but one child
                returnedHeight += dividerHeight;
            }

            // Recycle the view before we possibly return from the method
            if (recyle && recycleBin.shouldRecycleViewType(
                    ((LayoutParams) child.getLayoutParams()).viewType)) {
                recycleBin.addScrapView(child, -1);
            }

            returnedHeight += child.getMeasuredHeight();

            if (returnedHeight >= maxHeight) {
                // We went over, figure out which height to return.  If returnedHeight > maxHeight,
                // then the i'th position did not fit completely.
                return (disallowPartialChildPosition >= 0) // Disallowing is enabled (> -1)
                            && (i > disallowPartialChildPosition) // We've past the min pos
                            && (prevHeightWithoutPartialChild > 0) // We have a prev height
                            && (returnedHeight != maxHeight) // i'th child did not fit completely
                        ? prevHeightWithoutPartialChild
                        : maxHeight;
            }

            if ((disallowPartialChildPosition >= 0) && (i >= disallowPartialChildPosition)) {
                prevHeightWithoutPartialChild = returnedHeight;
            }
        }

        // At this point, we went through the range of children, and they each
        // completely fit, so return the returnedHeight
        return returnedHeight;
    }

    @Override
    int findMotionRow(int y) {
        int childCount = getChildCount();
        if (childCount > 0) {
            if (!mStackFromBottom) {
                for (int i = 0; i < childCount; i++) {
                    View v = getChildAt(i);
                    if (y <= v.getBottom()) {
                        return mFirstPosition + i;
                    }
                }
            } else {
                for (int i = childCount - 1; i >= 0; i--) {
                    View v = getChildAt(i);
                    if (y >= v.getTop()) {
                        return mFirstPosition + i;
                    }
                }
            }
        }
        return INVALID_POSITION;
    }

    /**
     * Put a specific item at a specific location on the screen and then build
     * up and down from there.
     *
     * @param position The reference view to use as the starting point
     * @param top Pixel offset from the top of this view to the top of the
     *        reference view.
     *
     * @return The selected view, or null if the selected view is outside the
     *         visible area.
     */
    private View fillSpecific(int position, int top) {
        boolean tempIsSelected = position == mSelectedPosition;
        View temp = makeAndAddView(position, top, true, mListPadding.left, tempIsSelected);
        // Possibly changed again in fillUp if we add rows above this one.
        mFirstPosition = position;

        View above;
        View below;

        final int dividerHeight = mDividerHeight;
        if (!mStackFromBottom) {
            above = fillUp(position - 1, temp.getTop() - dividerHeight);
            // This will correct for the top of the first view not touching the top of the list
            adjustViewsUpOrDown();
            below = fillDown(position + 1, temp.getBottom() + dividerHeight);
            int childCount = getChildCount();
            if (childCount > 0) {
                correctTooHigh(childCount);
            }
        } else {
            below = fillDown(position + 1, temp.getBottom() + dividerHeight);
            // This will correct for the bottom of the last view not touching the bottom of the list
            adjustViewsUpOrDown();
            above = fillUp(position - 1, temp.getTop() - dividerHeight);
            int childCount = getChildCount();
            if (childCount > 0) {
                 correctTooLow(childCount);
            }
        }

        if (tempIsSelected) {
            return temp;
        } else if (above != null) {
            return above;
        } else {
            return below;
        }
    }

    /**
     * Check if we have dragged the bottom of the list too high (we have pushed the
     * top element off the top of the screen when we did not need to). Correct by sliding
     * everything back down.
     *
     * @param childCount Number of children
     */
    private void correctTooHigh(int childCount) {
        // First see if the last item is visible. If it is not, it is OK for the
        // top of the list to be pushed up.
        int lastPosition = mFirstPosition + childCount - 1;
        if (lastPosition == mItemCount - 1 && childCount > 0) {

            // Get the last child ...
            final View lastChild = getChildAt(childCount - 1);

            // ... and its bottom edge
            final int lastBottom = lastChild.getBottom();

            // This is bottom of our drawable area
            final int end = (mBottom - mTop) - mListPadding.bottom;

            // This is how far the bottom edge of the last view is from the bottom of the
            // drawable area
            int bottomOffset = end - lastBottom;
            View firstChild = getChildAt(0);
            final int firstTop = firstChild.getTop();

            // Make sure we are 1) Too high, and 2) Either there are more rows above the
            // first row or the first row is scrolled off the top of the drawable area
            if (bottomOffset > 0 && (mFirstPosition > 0 || firstTop < mListPadding.top))  {
                if (mFirstPosition == 0) {
                    // Don't pull the top too far down
                    bottomOffset = Math.min(bottomOffset, mListPadding.top - firstTop);
                }
                // Move everything down
                offsetChildrenTopAndBottom(bottomOffset);
                if (mFirstPosition > 0) {
                    // Fill the gap that was opened above mFirstPosition with more rows, if
                    // possible
                    fillUp(mFirstPosition - 1, firstChild.getTop() - mDividerHeight);
                    // Close up the remaining gap
                    adjustViewsUpOrDown();
                }

            }
        }
    }

    /**
     * Check if we have dragged the bottom of the list too low (we have pushed the
     * bottom element off the bottom of the screen when we did not need to). Correct by sliding
     * everything back up.
     *
     * @param childCount Number of children
     */
    private void correctTooLow(int childCount) {
        // First see if the first item is visible. If it is not, it is OK for the
        // bottom of the list to be pushed down.
        if (mFirstPosition == 0 && childCount > 0) {

            // Get the first child ...
            final View firstChild = getChildAt(0);

            // ... and its top edge
            final int firstTop = firstChild.getTop();

            // This is top of our drawable area
            final int start = mListPadding.top;

            // This is bottom of our drawable area
            final int end = (mBottom - mTop) - mListPadding.bottom;

            // This is how far the top edge of the first view is from the top of the
            // drawable area
            int topOffset = firstTop - start;
            View lastChild = getChildAt(childCount - 1);
            final int lastBottom = lastChild.getBottom();
            int lastPosition = mFirstPosition + childCount - 1;

            // Make sure we are 1) Too low, and 2) Either there are more rows below the
            // last row or the last row is scrolled off the bottom of the drawable area
            if (topOffset > 0) {
                if (lastPosition < mItemCount - 1 || lastBottom > end)  {
                    if (lastPosition == mItemCount - 1) {
                        // Don't pull the bottom too far up
                        topOffset = Math.min(topOffset, lastBottom - end);
                    }
                    // Move everything up
                    offsetChildrenTopAndBottom(-topOffset);
                    if (lastPosition < mItemCount - 1) {
                        // Fill the gap that was opened below the last position with more rows, if
                        // possible
                        fillDown(lastPosition + 1, lastChild.getBottom() + mDividerHeight);
                        // Close up the remaining gap
                        adjustViewsUpOrDown();
                    }
                } else if (lastPosition == mItemCount - 1) {
                    adjustViewsUpOrDown();                    
                }
            }
        }
    }

    @Override
    protected void layoutChildren() {
        final boolean blockLayoutRequests = mBlockLayoutRequests;
        if (blockLayoutRequests) {
            return;
        }

        mBlockLayoutRequests = true;

        try {
            super.layoutChildren();

            invalidate();

            if (mAdapter == null) {
                resetList();
                invokeOnItemScrollListener();
                return;
            }

            //tws-start add listview bounce feature::2014-08-12
            int childrenTop = mListPadding.top + mFirstTopOffset;
            int childrenBottom = mBottom - mTop - mListPadding.bottom - mLastBottomOffset;
            //tws-end add listview bounce feature::2014-08-12
            final int childCount = getChildCount();

            int index = 0;
            int delta = 0;

            View sel;
            View oldSel = null;
            View oldFirst = null;
            View newSel = null;

            // Remember stuff we will need down below
            switch (mLayoutMode) {
            case LAYOUT_SET_SELECTION:
                index = mNextSelectedPosition - mFirstPosition;
                if (index >= 0 && index < childCount) {
                    newSel = getChildAt(index);
                }
                break;
            case LAYOUT_FORCE_TOP:
            case LAYOUT_FORCE_BOTTOM:
            case LAYOUT_SPECIFIC:
            case LAYOUT_SYNC:
                break;
            case LAYOUT_MOVE_SELECTION:
            default:
                // Remember the previously selected view
                index = mSelectedPosition - mFirstPosition;
                if (index >= 0 && index < childCount) {
                    oldSel = getChildAt(index);
                }

                // Remember the previous first child
                oldFirst = getChildAt(0);

                if (mNextSelectedPosition >= 0) {
                    delta = mNextSelectedPosition - mSelectedPosition;
                }

                // Caution: newSel might be null
                newSel = getChildAt(index + delta);
            }


            boolean dataChanged = mDataChanged;
            if (dataChanged) {
                handleDataChanged();
            }

            // Handle the empty set by removing all views that are visible
            // and calling it a day
            if (mItemCount == 0) {
                resetList();
                invokeOnItemScrollListener();
                return;
            } else if (mItemCount != mAdapter.getCount()) {
                throw new IllegalStateException("The content of the adapter has changed but "
                        + "ListView did not receive a notification. Make sure the content of "
                        + "your adapter is not modified from a background thread, but only from "
                        + "the UI thread. Make sure your adapter calls notifyDataSetChanged() "
                        + "when its content changes. [in ListView(" + getId() + ", " + getClass()
                        + ") with Adapter(" + mAdapter.getClass() + ")]");
            }

            setSelectedPositionInt(mNextSelectedPosition);

            AccessibilityNodeInfo accessibilityFocusLayoutRestoreNode = null;
            View accessibilityFocusLayoutRestoreView = null;
            int accessibilityFocusPosition = INVALID_POSITION;

            // Remember which child, if any, had accessibility focus. This must
            // occur before recycling any views, since that will clear
            // accessibility focus.
            /*final ViewRootImpl viewRootImpl = getViewRootImpl();
            if (viewRootImpl != null) {
                final View focusHost = viewRootImpl.getAccessibilityFocusedHost();
                if (focusHost != null) {
                    final View focusChild = getAccessibilityFocusedChild(focusHost);
                    if (focusChild != null) {
                        if (!dataChanged || isDirectChildHeaderOrFooter(focusChild)
                                || focusChild.hasTransientState() || mAdapterHasStableIds) {
                            // The views won't be changing, so try to maintain
                            // focus on the current host and virtual view.
                            accessibilityFocusLayoutRestoreView = focusHost;
                            accessibilityFocusLayoutRestoreNode = viewRootImpl
                                    .getAccessibilityFocusedVirtualView();
                        }

                        // If all else fails, maintain focus at the same
                        // position.
                        accessibilityFocusPosition = getPositionForView(focusChild);
                    }
                }
            }*/

            View focusLayoutRestoreDirectChild = null;
            View focusLayoutRestoreView = null;

            // Take focus back to us temporarily to avoid the eventual call to
            // clear focus when removing the focused child below from messing
            // things up when ViewAncestor assigns focus back to someone else.
            final View focusedChild = getFocusedChild();
            if (focusedChild != null) {
                // TODO: in some cases focusedChild.getParent() == null

                // We can remember the focused view to restore after re-layout
                // if the data hasn't changed, or if the focused position is a
                // header or footer.
                if (!dataChanged || isDirectChildHeaderOrFooter(focusedChild)
                        || focusedChild.hasTransientState() || mAdapterHasStableIds) {
                    focusLayoutRestoreDirectChild = focusedChild;
                    // Remember the specific view that had focus.
                    focusLayoutRestoreView = findFocus();
                    if (focusLayoutRestoreView != null) {
                        // Tell it we are going to mess with it.
                        focusLayoutRestoreView.onStartTemporaryDetach();
                    }
                }
                requestFocus();
            }

            // Pull all children into the RecycleBin.
            // These views will be reused if possible
            final int firstPosition = mFirstPosition;
            final RecycleBin recycleBin = mRecycler;
            if (dataChanged) {
                for (int i = 0; i < childCount; i++) {
                    recycleBin.addScrapView(getChildAt(i), firstPosition+i);
                }
            } else {
                recycleBin.fillActiveViews(childCount, firstPosition);
            }

            // Clear out old views
            detachAllViewsFromParent();
            recycleBin.removeSkippedScrap();

            switch (mLayoutMode) {
            case LAYOUT_SET_SELECTION:
                if (newSel != null) {
                    sel = fillFromSelection(newSel.getTop(), childrenTop, childrenBottom);
                } else {
                    sel = fillFromMiddle(childrenTop, childrenBottom);
                }
                break;
            case LAYOUT_SYNC:
                sel = fillSpecific(mSyncPosition, mSpecificTop);
                break;
            case LAYOUT_FORCE_BOTTOM:
                sel = fillUp(mItemCount - 1, childrenBottom);
                adjustViewsUpOrDown();
                break;
            case LAYOUT_FORCE_TOP:
                mFirstPosition = 0;
                sel = fillFromTop(childrenTop);
                adjustViewsUpOrDown();
                break;
            case LAYOUT_SPECIFIC:
                sel = fillSpecific(reconcileSelectedPosition(), mSpecificTop);
                break;
            case LAYOUT_MOVE_SELECTION:
                sel = moveSelection(oldSel, newSel, delta, childrenTop, childrenBottom);
                break;
            default:
                if (childCount == 0) {
                    if (!mStackFromBottom) {
                        final int position = lookForSelectablePosition(0, true);
                        setSelectedPositionInt(position);
                        sel = fillFromTop(childrenTop);
                    } else {
                        final int position = lookForSelectablePosition(mItemCount - 1, false);
                        setSelectedPositionInt(position);
                        sel = fillUp(mItemCount - 1, childrenBottom);
                    }
                } else {
                    if (mSelectedPosition >= 0 && mSelectedPosition < mItemCount) {
                        sel = fillSpecific(mSelectedPosition,
                                oldSel == null ? childrenTop : oldSel.getTop());
                    } else if (mFirstPosition < mItemCount) {
                        sel = fillSpecific(mFirstPosition,
                                oldFirst == null ? childrenTop : oldFirst.getTop());
                    } else {
                        sel = fillSpecific(0, childrenTop);
                    }
                }
                break;
            }

            // Flush any cached views that did not get reused above
            recycleBin.scrapActiveViews();

            if (sel != null) {
                // The current selected item should get focus if items are
                // focusable.
                if (mItemsCanFocus && hasFocus() && !sel.hasFocus()) {
                    final boolean focusWasTaken = (sel == focusLayoutRestoreDirectChild &&
                            focusLayoutRestoreView != null &&
                            focusLayoutRestoreView.requestFocus()) || sel.requestFocus();
                    if (!focusWasTaken) {
                        // Selected item didn't take focus, but we still want to
                        // make sure something else outside of the selected view
                        // has focus.
                        final View focused = getFocusedChild();
                        if (focused != null) {
                            focused.clearFocus();
                        }
                        positionSelector(INVALID_POSITION, sel);
                    } else {
                        sel.setSelected(false);
                        mSelectorRect.setEmpty();
                    }
                } else {
                    positionSelector(INVALID_POSITION, sel);
                }
                mSelectedTop = sel.getTop();
            } else {
                final boolean inTouchMode = mTouchMode == TOUCH_MODE_TAP
                        || mTouchMode == TOUCH_MODE_DONE_WAITING;
                if (inTouchMode) {
                    // If the user's finger is down, select the motion position.
                    final View child = getChildAt(mMotionPosition - mFirstPosition);
                    if (child != null) {
                        positionSelector(mMotionPosition, child);
                    }
                } else if (mSelectorPosition != INVALID_POSITION) {
                    // If we had previously positioned the selector somewhere,
                    // put it back there. It might not match up with the data,
                    // but it's transitioning out so it's not a big deal.
                    final View child = getChildAt(mSelectorPosition - mFirstPosition);
                    if (child != null) {
                        positionSelector(mSelectorPosition, child);
                    }
                } else {
                    // Otherwise, clear selection.
                    mSelectedTop = 0;
                    mSelectorRect.setEmpty();
                }

                // Even if there is not selected position, we may need to
                // restore focus (i.e. something focusable in touch mode).
                if (hasFocus() && focusLayoutRestoreView != null) {
                    focusLayoutRestoreView.requestFocus();
                }
            }

            // Attempt to restore accessibility focus, if necessary.
            /*if (viewRootImpl != null) {
                final View newAccessibilityFocusedView = viewRootImpl.getAccessibilityFocusedHost();
                if (newAccessibilityFocusedView == null) {
                    if (accessibilityFocusLayoutRestoreView != null
                            && accessibilityFocusLayoutRestoreView.isAttachedToWindow()) {
                        final AccessibilityNodeProvider provider =
                                accessibilityFocusLayoutRestoreView.getAccessibilityNodeProvider();
                        if (accessibilityFocusLayoutRestoreNode != null && provider != null) {
                            final int virtualViewId = AccessibilityNodeInfo.getVirtualDescendantId(
                                    accessibilityFocusLayoutRestoreNode.getSourceNodeId());
                            provider.performAction(virtualViewId,
                                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                        } else {
                            accessibilityFocusLayoutRestoreView.requestAccessibilityFocus();
                        }
                    } else if (accessibilityFocusPosition != INVALID_POSITION) {
                        // Bound the position within the visible children.
                        final int position = MathUtils.constrain(
                                accessibilityFocusPosition - mFirstPosition, 0,
                                getChildCount() - 1);
                        final View restoreView = getChildAt(position);
                        if (restoreView != null) {
                            restoreView.requestAccessibilityFocus();
                        }
                    }
                }
            }*/

            // Tell focus view we are done mucking with it, if it is still in
            // our view hierarchy.
            if (focusLayoutRestoreView != null
                    && focusLayoutRestoreView.getWindowToken() != null) {
                focusLayoutRestoreView.onFinishTemporaryDetach();
            }
            
            mLayoutMode = LAYOUT_NORMAL;
            mDataChanged = false;
            if (mPositionScrollAfterLayout != null) {
                post(mPositionScrollAfterLayout);
                mPositionScrollAfterLayout = null;
            }
            mNeedSync = false;
            setNextSelectedPositionInt(mSelectedPosition);

            updateScrollIndicators();

            if (mItemCount > 0) {
                checkSelectionChanged();
            }

            invokeOnItemScrollListener();
        } finally {
            if (!blockLayoutRequests) {
                mBlockLayoutRequests = false;
            }
        }
    }

    /**
     * @param child a direct child of this list.
     * @return Whether child is a header or footer view.
     */
    private boolean isDirectChildHeaderOrFooter(View child) {
        final ArrayList<FixedViewInfo> headers = mHeaderViewInfos;
        final int numHeaders = headers.size();
        for (int i = 0; i < numHeaders; i++) {
            if (child == headers.get(i).view) {
                return true;
            }
        }

        final ArrayList<FixedViewInfo> footers = mFooterViewInfos;
        final int numFooters = footers.size();
        for (int i = 0; i < numFooters; i++) {
            if (child == footers.get(i).view) {
                return true;
            }
        }

        return false;
    }

    /**
     * Obtain the view and add it to our list of children. The view can be made
     * fresh, converted from an unused view, or used as is if it was in the
     * recycle bin.
     *
     * @param position Logical position in the list
     * @param y Top or bottom edge of the view to add
     * @param flow If flow is true, align top edge to y. If false, align bottom
     *        edge to y.
     * @param childrenLeft Left edge where children should be positioned
     * @param selected Is this position selected?
     * @return View that was added
     */
    private View makeAndAddView(int position, int y, boolean flow, int childrenLeft,
            boolean selected) {
        View child;


        if (!mDataChanged) {
            // Try to use an existing view for this position
            child = mRecycler.getActiveView(position);
            if (child != null) {
                // Found it -- we're using an existing child
                // This just needs to be positioned
                setupChild(child, position, y, flow, childrenLeft, selected, true);

                return child;
            }
        }

        // Make a new view for this position, or convert an unused view if possible
        child = obtainView(position, mIsScrap);

        // This needs to be positioned and measured
        setupChild(child, position, y, flow, childrenLeft, selected, mIsScrap[0]);

        return child;
    }

    /**
     * Add a view as a child and make sure it is measured (if necessary) and
     * positioned properly.
     *
     * @param child The view to add
     * @param position The position of this child
     * @param y The y position relative to which this view will be positioned
     * @param flowDown If true, align top edge to y. If false, align bottom
     *        edge to y.
     * @param childrenLeft Left edge where children should be positioned
     * @param selected Is this position selected?
     * @param recycled Has this view been pulled from the recycle bin? If so it
     *        does not need to be remeasured.
     */
    private void setupChild(View child, int position, int y, boolean flowDown, int childrenLeft,
            boolean selected, boolean recycled) {
        final boolean isSelected = selected && shouldShowSelector();
        final boolean updateChildSelected = isSelected != child.isSelected();
        final int mode = mTouchMode;
        final boolean isPressed = mode > TOUCH_MODE_DOWN && mode < TOUCH_MODE_SCROLL &&
                mMotionPosition == position;
        final boolean updateChildPressed = isPressed != child.isPressed();
        final boolean needToMeasure = !recycled || updateChildSelected || child.isLayoutRequested();

        // Respect layout params that are already in the view. Otherwise make some up...
        // noinspection unchecked
        AbsListView.LayoutParams p = (AbsListView.LayoutParams) child.getLayoutParams();
        if (p == null) {
            p = (AbsListView.LayoutParams) generateDefaultLayoutParams();
        }
        p.viewType = mAdapter.getItemViewType(position);

        if ((recycled && !p.forceAdd) || (p.recycledHeaderFooter
                && p.viewType == AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER)) {
            attachViewToParent(child, flowDown ? -1 : 0, p);
        } else {
            p.forceAdd = false;
            if (p.viewType == AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
                p.recycledHeaderFooter = true;
            }
            //tws-start::set the global activated/selected list item background 2013-06-09
            if(mSelectedStateEnabled && mActivatedBackgroundIndicator != 0) {
                child.setBackgroundResource(mActivatedBackgroundIndicator);
            }
            //tws-end 2013-06-09
            addViewInLayout(child, flowDown ? -1 : 0, p, true);
        }

        if (updateChildSelected) {
            child.setSelected(isSelected);
        }

        if (updateChildPressed) {
            child.setPressed(isPressed);
        }

        if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null) {
            if (child instanceof Checkable) {
                ((Checkable) child).setChecked(mCheckStates.get(position));
            } else if (getContext().getApplicationInfo().targetSdkVersion
                    >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                child.setActivated(mCheckStates.get(position));
            }
        }

        if (needToMeasure) {
            final int childWidthSpec = ViewGroup.getChildMeasureSpec(mWidthMeasureSpec,
                    mListPadding.left + mListPadding.right, p.width);
            final int lpHeight = p.height;
            final int childHeightSpec;
            if (lpHeight > 0) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY);
            } else {
            if(android.os.Build.VERSION.SDK_INT < 23) {
                childHeightSpec = 0;
            } else {
                childHeightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.UNSPECIFIED);
            }
            }
            child.measure(childWidthSpec, childHeightSpec);
        } else {
            cleanupLayoutState(child);
        }

        final int w = child.getMeasuredWidth();
        final int h = child.getMeasuredHeight();
        final int childTop = flowDown ? y : y - h;

        if (needToMeasure) {
            final int childRight = childrenLeft + w;
            final int childBottom = childTop + h;
            child.layout(childrenLeft, childTop, childRight, childBottom);
        } else {
            child.offsetLeftAndRight(childrenLeft - child.getLeft());
            child.offsetTopAndBottom(childTop - child.getTop());
        }

        if (mCachingStarted && !child.isDrawingCacheEnabled()) {
            child.setDrawingCacheEnabled(true);
        }

        if (recycled && (((AbsListView.LayoutParams)child.getLayoutParams()).scrappedFromPosition)
                != position) {
            child.jumpDrawablesToCurrentState();
        }
    }

    @Override
    protected boolean canAnimate() {
        return super.canAnimate() && mItemCount > 0;
    }

    /**
     * Sets the currently selected item. If in touch mode, the item will not be selected
     * but it will still be positioned appropriately. If the specified selection position
     * is less than 0, then the item at position 0 will be selected.
     *
     * @param position Index (starting at 0) of the data item to be selected.
     */
    @Override
    public void setSelection(int position) {
        setSelectionFromTop(position, 0);
    }

    /**
     * Makes the item at the supplied position selected.
     * 
     * @param position the position of the item to select
     */
    @Override
    void setSelectionInt(int position) {
        setNextSelectedPositionInt(position);
        boolean awakeScrollbars = false;

        final int selectedPosition = mSelectedPosition;

        if (selectedPosition >= 0) {
            if (position == selectedPosition - 1) {
                awakeScrollbars = true;
            } else if (position == selectedPosition + 1) {
                awakeScrollbars = true;
            }
        }

        if (mPositionScroller != null) {
            mPositionScroller.stop();
        }

        layoutChildren();

        if (awakeScrollbars) {
            awakenScrollBars();
        }
    }

    /**
     * Find a position that can be selected (i.e., is not a separator).
     *
     * @param position The starting position to look at.
     * @param lookDown Whether to look down for other positions.
     * @return The next selectable position starting at position and then searching either up or
     *         down. Returns {@link #INVALID_POSITION} if nothing can be found.
     */
    @Override
    int lookForSelectablePosition(int position, boolean lookDown) {
        final ListAdapter adapter = mAdapter;
        if (adapter == null || isInTouchMode()) {
            return INVALID_POSITION;
        }

        final int count = adapter.getCount();
        if (!mAreAllItemsSelectable) {
            if (lookDown) {
                position = Math.max(0, position);
                while (position < count && !adapter.isEnabled(position)) {
                    position++;
                }
            } else {
                position = Math.min(position, count - 1);
                while (position >= 0 && !adapter.isEnabled(position)) {
                    position--;
                }
            }
        }

        if (position < 0 || position >= count) {
            return INVALID_POSITION;
        }

        return position;
    }

    /**
     * Find a position that can be selected (i.e., is not a separator). If there
     * are no selectable positions in the specified direction from the starting
     * position, searches in the opposite direction from the starting position
     * to the current position.
     *
     * @param current the current position
     * @param position the starting position
     * @param lookDown whether to look down for other positions
     * @return the next selectable position, or {@link #INVALID_POSITION} if
     *         nothing can be found
     */
    int lookForSelectablePositionAfter(int current, int position, boolean lookDown) {
        final ListAdapter adapter = mAdapter;
        if (adapter == null || isInTouchMode()) {
            return INVALID_POSITION;
        }

        // First check after the starting position in the specified direction.
        final int after = lookForSelectablePosition(position, lookDown);
        if (after != INVALID_POSITION) {
            return after;
        }

        // Then check between the starting position and the current position.
        final int count = adapter.getCount();
        current = MathUtils.constrain(current, -1, count - 1);
        if (lookDown) {
            position = Math.min(position - 1, count - 1);
            while ((position > current) && !adapter.isEnabled(position)) {
                position--;
            }
            if (position <= current) {
                return INVALID_POSITION;
            }
        } else {
            position = Math.max(0, position + 1);
            while ((position < current) && !adapter.isEnabled(position)) {
                position++;
            }
            if (position >= current) {
                return INVALID_POSITION;
            }
        }

        return position;
    }

    /**
     * setSelectionAfterHeaderView set the selection to be the first list item
     * after the header views.
     */
    public void setSelectionAfterHeaderView() {
        final int count = mHeaderViewInfos.size();
        if (count > 0) {
            mNextSelectedPosition = 0;
            return;
        }

        if (mAdapter != null) {
            setSelection(count);
        } else {
            mNextSelectedPosition = count;
            mLayoutMode = LAYOUT_SET_SELECTION;
        }

    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Dispatch in the normal way
        boolean handled = super.dispatchKeyEvent(event);
        if (!handled) {
            // If we didn't handle it...
            View focused = getFocusedChild();
            if (focused != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                // ... and our focused child didn't handle it
                // ... give it to ourselves so we can scroll if necessary
                handled = onKeyDown(event.getKeyCode(), event);
            }
        }
        return handled;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return commonKey(keyCode, 1, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        return commonKey(keyCode, repeatCount, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return commonKey(keyCode, 1, event);
    }

    private boolean commonKey(int keyCode, int count, KeyEvent event) {
        if (mAdapter == null || !mIsAttached) {
            return false;
        }

        if (mDataChanged) {
            layoutChildren();
        }

        boolean handled = false;
        int action = event.getAction();

        if (action != KeyEvent.ACTION_UP) {
            switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (event.hasNoModifiers()) {
                    handled = resurrectSelectionIfNeeded();
                    if (!handled) {
                        while (count-- > 0) {
                            if (arrowScroll(FOCUS_UP)) {
                                handled = true;
                            } else {
                                break;
                            }
                        }
                    }
                } else if (event.hasModifiers(KeyEvent.META_ALT_ON)) {
                    handled = resurrectSelectionIfNeeded() || fullScroll(FOCUS_UP);
                }
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.hasNoModifiers()) {
                    handled = resurrectSelectionIfNeeded();
                    if (!handled) {
                        while (count-- > 0) {
                            if (arrowScroll(FOCUS_DOWN)) {
                                handled = true;
                            } else {
                                break;
                            }
                        }
                    }
                } else if (event.hasModifiers(KeyEvent.META_ALT_ON)) {
                    handled = resurrectSelectionIfNeeded() || fullScroll(FOCUS_DOWN);
                }
                break;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.hasNoModifiers()) {
                    handled = handleHorizontalFocusWithinListItem(View.FOCUS_LEFT);
                }
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.hasNoModifiers()) {
                    handled = handleHorizontalFocusWithinListItem(View.FOCUS_RIGHT);
                }
                break;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (event.hasNoModifiers()) {
                    handled = resurrectSelectionIfNeeded();
                    if (!handled
                            && event.getRepeatCount() == 0 && getChildCount() > 0) {
                        keyPressed();
                        handled = true;
                    }
                }
                break;

            case KeyEvent.KEYCODE_SPACE:
                if (mPopup == null || !mPopup.isShowing()) {
                    if (event.hasNoModifiers()) {
                        handled = resurrectSelectionIfNeeded() || pageScroll(FOCUS_DOWN);
                    } else if (event.hasModifiers(KeyEvent.META_SHIFT_ON)) {
                        handled = resurrectSelectionIfNeeded() || pageScroll(FOCUS_UP);
                    }
                    handled = true;
                }
                break;

            case KeyEvent.KEYCODE_PAGE_UP:
                if (event.hasNoModifiers()) {
                    handled = resurrectSelectionIfNeeded() || pageScroll(FOCUS_UP);
                } else if (event.hasModifiers(KeyEvent.META_ALT_ON)) {
                    handled = resurrectSelectionIfNeeded() || fullScroll(FOCUS_UP);
                }
                break;

            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (event.hasNoModifiers()) {
                    handled = resurrectSelectionIfNeeded() || pageScroll(FOCUS_DOWN);
                } else if (event.hasModifiers(KeyEvent.META_ALT_ON)) {
                    handled = resurrectSelectionIfNeeded() || fullScroll(FOCUS_DOWN);
                }
                break;

            case KeyEvent.KEYCODE_MOVE_HOME:
                if (event.hasNoModifiers()) {
                    handled = resurrectSelectionIfNeeded() || fullScroll(FOCUS_UP);
                }
                break;

            case KeyEvent.KEYCODE_MOVE_END:
                if (event.hasNoModifiers()) {
                    handled = resurrectSelectionIfNeeded() || fullScroll(FOCUS_DOWN);
                }
                break;

            case KeyEvent.KEYCODE_TAB:
                // XXX Sometimes it is useful to be able to TAB through the items in
                //     a ListView sequentially.  Unfortunately this can create an
                //     asymmetry in TAB navigation order unless the list selection
                //     always reverts to the top or bottom when receiving TAB focus from
                //     another widget.  Leaving this behavior disabled for now but
                //     perhaps it should be configurable (and more comprehensive).
                if (false) {
                    if (event.hasNoModifiers()) {
                        handled = resurrectSelectionIfNeeded() || arrowScroll(FOCUS_DOWN);
                    } else if (event.hasModifiers(KeyEvent.META_SHIFT_ON)) {
                        handled = resurrectSelectionIfNeeded() || arrowScroll(FOCUS_UP);
                    }
                }
                break;
            }
        }

        if (handled) {
            return true;
        }

        if (sendToTextFilter(keyCode, count, event)) {
            return true;
        }

        switch (action) {
            case KeyEvent.ACTION_DOWN:
                return super.onKeyDown(keyCode, event);

            case KeyEvent.ACTION_UP:
                return super.onKeyUp(keyCode, event);

            case KeyEvent.ACTION_MULTIPLE:
                return super.onKeyMultiple(keyCode, count, event);

            default: // shouldn't happen
                return false;
        }
    }

    /**
     * Scrolls up or down by the number of items currently present on screen.
     *
     * @param direction either {@link View#FOCUS_UP} or {@link View#FOCUS_DOWN}
     * @return whether selection was moved
     */
    boolean pageScroll(int direction) {
        final int nextPage;
        final boolean down;

        if (direction == FOCUS_UP) {
            nextPage = Math.max(0, mSelectedPosition - getChildCount() - 1);
            down = false;
        } else if (direction == FOCUS_DOWN) {
            nextPage = Math.min(mItemCount - 1, mSelectedPosition + getChildCount() - 1);
            down = true;
        } else {
            return false;
        }

        if (nextPage >= 0) {
            final int position = lookForSelectablePositionAfter(mSelectedPosition, nextPage, down);
            if (position >= 0) {
                mLayoutMode = LAYOUT_SPECIFIC;
                mSpecificTop = mPaddingTop + getVerticalFadingEdgeLength();

                if (down && (position > (mItemCount - getChildCount()))) {
                    mLayoutMode = LAYOUT_FORCE_BOTTOM;
                }

                if (!down && (position < getChildCount())) {
                    mLayoutMode = LAYOUT_FORCE_TOP;
                }

                setSelectionInt(position);
                invokeOnItemScrollListener();
                if (!awakenScrollBars()) {
                    invalidate();
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Go to the last or first item if possible (not worrying about panning
     * across or navigating within the internal focus of the currently selected
     * item.)
     *
     * @param direction either {@link View#FOCUS_UP} or {@link View#FOCUS_DOWN}
     * @return whether selection was moved
     */
    boolean fullScroll(int direction) {
        boolean moved = false;
        if (direction == FOCUS_UP) {
            if (mSelectedPosition != 0) {
                final int position = lookForSelectablePositionAfter(mSelectedPosition, 0, true);
                if (position >= 0) {
                    mLayoutMode = LAYOUT_FORCE_TOP;
                    setSelectionInt(position);
                    invokeOnItemScrollListener();
                }
                moved = true;
            }
        } else if (direction == FOCUS_DOWN) {
            final int lastItem = (mItemCount - 1);
            if (mSelectedPosition < lastItem) {
                final int position = lookForSelectablePositionAfter(
                        mSelectedPosition, lastItem, false);
                if (position >= 0) {
                    mLayoutMode = LAYOUT_FORCE_BOTTOM;
                    setSelectionInt(position);
                    invokeOnItemScrollListener();
                }
                moved = true;
            }
        }

        if (moved && !awakenScrollBars()) {
            awakenScrollBars();
            invalidate();
        }

        return moved;
    }

    /**
     * To avoid horizontal focus searches changing the selected item, we
     * manually focus search within the selected item (as applicable), and
     * prevent focus from jumping to something within another item.
     * @param direction one of {View.FOCUS_LEFT, View.FOCUS_RIGHT}
     * @return Whether this consumes the key event.
     */
    private boolean handleHorizontalFocusWithinListItem(int direction) {
        if (direction != View.FOCUS_LEFT && direction != View.FOCUS_RIGHT)  {
            throw new IllegalArgumentException("direction must be one of"
                    + " {View.FOCUS_LEFT, View.FOCUS_RIGHT}");
        }

        final int numChildren = getChildCount();
        if (mItemsCanFocus && numChildren > 0 && mSelectedPosition != INVALID_POSITION) {
            final View selectedView = getSelectedView();
            if (selectedView != null && selectedView.hasFocus() &&
                    selectedView instanceof ViewGroup) {

                final View currentFocus = selectedView.findFocus();
                final View nextFocus = FocusFinder.getInstance().findNextFocus(
                        (ViewGroup) selectedView, currentFocus, direction);
                if (nextFocus != null) {
                    // do the math to get interesting rect in next focus' coordinates
                    Rect focusedRect = mTempRect;
                    if (currentFocus != null) {
                        currentFocus.getFocusedRect(focusedRect);
                        offsetDescendantRectToMyCoords(currentFocus, focusedRect);
                        offsetRectIntoDescendantCoords(nextFocus, focusedRect);
                    } else {
                        focusedRect = null;
                    }
                    if (nextFocus.requestFocus(direction, focusedRect)) {
                        return true;
                    }
                }
                // we are blocking the key from being handled (by returning true)
                // if the global result is going to be some other view within this
                // list.  this is to acheive the overall goal of having
                // horizontal d-pad navigation remain in the current item.
                final View globalNextFocus = FocusFinder.getInstance().findNextFocus(
                        (ViewGroup) getRootView(), currentFocus, direction);
                if (globalNextFocus != null) {
                    return isViewAncestorOf(globalNextFocus, this);
                }
            }
        }
        return false;
    }

    /**
     * Scrolls to the next or previous item if possible.
     *
     * @param direction either {@link View#FOCUS_UP} or {@link View#FOCUS_DOWN}
     *
     * @return whether selection was moved
     */
    boolean arrowScroll(int direction) {
        try {
            mInLayout = true;
            final boolean handled = arrowScrollImpl(direction);
            if (handled) {
                playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction));
            }
            return handled;
        } finally {
            mInLayout = false;
        }
    }

    /**
     * Used by {@link #arrowScrollImpl(int)} to help determine the next selected position
     * to move to. This return a position in the direction given if the selected item
     * is fully visible.
     *
     * @param selectedView Current selected view to move from
     * @param selectedPos Current selected position to move from
     * @param direction Direction to move in
     * @return Desired selected position after moving in the given direction
     */
    private final int nextSelectedPositionForDirection(
            View selectedView, int selectedPos, int direction) {
        int nextSelected;

        if (direction == View.FOCUS_DOWN) {
            final int listBottom = getHeight() - mListPadding.bottom;
            if (selectedView != null && selectedView.getBottom() <= listBottom) {
                nextSelected = selectedPos != INVALID_POSITION && selectedPos >= mFirstPosition ?
                        selectedPos + 1 :
                        mFirstPosition;
            } else {
                return INVALID_POSITION;
            }
        } else {
            final int listTop = mListPadding.top;
            if (selectedView != null && selectedView.getTop() >= listTop) {
                final int lastPos = mFirstPosition + getChildCount() - 1;
                nextSelected = selectedPos != INVALID_POSITION && selectedPos <= lastPos ?
                        selectedPos - 1 :
                        lastPos;
            } else {
                return INVALID_POSITION;
            }
        }

        if (nextSelected < 0 || nextSelected >= mAdapter.getCount()) {
            return INVALID_POSITION;
        }
        return lookForSelectablePosition(nextSelected, direction == View.FOCUS_DOWN);
    }

    /**
     * Handle an arrow scroll going up or down.  Take into account whether items are selectable,
     * whether there are focusable items etc.
     *
     * @param direction Either {@link android.view.View#FOCUS_UP} or {@link android.view.View#FOCUS_DOWN}.
     * @return Whether any scrolling, selection or focus change occured.
     */
    private boolean arrowScrollImpl(int direction) {
        if (getChildCount() <= 0) {
            return false;
        }

        View selectedView = getSelectedView();
        int selectedPos = mSelectedPosition;

        int nextSelectedPosition = nextSelectedPositionForDirection(selectedView, selectedPos, direction);
        int amountToScroll = amountToScroll(direction, nextSelectedPosition);

        // if we are moving focus, we may OVERRIDE the default behavior
        final ArrowScrollFocusResult focusResult = mItemsCanFocus ? arrowScrollFocused(direction) : null;
        if (focusResult != null) {
            nextSelectedPosition = focusResult.getSelectedPosition();
            amountToScroll = focusResult.getAmountToScroll();
        }

        boolean needToRedraw = focusResult != null;
        if (nextSelectedPosition != INVALID_POSITION) {
            handleNewSelectionChange(selectedView, direction, nextSelectedPosition, focusResult != null);
            setSelectedPositionInt(nextSelectedPosition);
            setNextSelectedPositionInt(nextSelectedPosition);
            selectedView = getSelectedView();
            selectedPos = nextSelectedPosition;
            if (mItemsCanFocus && focusResult == null) {
                // there was no new view found to take focus, make sure we
                // don't leave focus with the old selection
                final View focused = getFocusedChild();
                if (focused != null) {
                    focused.clearFocus();
                }
            }
            needToRedraw = true;
            checkSelectionChanged();
        }

        if (amountToScroll > 0) {
            scrollListItemsBy((direction == View.FOCUS_UP) ? amountToScroll : -amountToScroll);
            needToRedraw = true;
        }

        // if we didn't find a new focusable, make sure any existing focused
        // item that was panned off screen gives up focus.
        if (mItemsCanFocus && (focusResult == null)
                && selectedView != null && selectedView.hasFocus()) {
            final View focused = selectedView.findFocus();
            if (focused != null) {
                if (!isViewAncestorOf(focused, this) || distanceToView(focused) > 0) {
                    focused.clearFocus();
                }
            }
        }

        // if  the current selection is panned off, we need to remove the selection
        if (nextSelectedPosition == INVALID_POSITION && selectedView != null
                && !isViewAncestorOf(selectedView, this)) {
            selectedView = null;
            hideSelector();

            // but we don't want to set the ressurect position (that would make subsequent
            // unhandled key events bring back the item we just scrolled off!)
            mResurrectToPosition = INVALID_POSITION;
        }

        if (needToRedraw) {
            if (selectedView != null) {
                positionSelectorLikeFocus(selectedPos, selectedView);
                mSelectedTop = selectedView.getTop();
            }
            if (!awakenScrollBars()) {
                invalidate();
            }
            invokeOnItemScrollListener();
            return true;
        }

        return false;
    }

    /**
     * When selection changes, it is possible that the previously selected or the
     * next selected item will change its size.  If so, we need to offset some folks,
     * and re-layout the items as appropriate.
     *
     * @param selectedView The currently selected view (before changing selection).
     *   should be <code>null</code> if there was no previous selection.
     * @param direction Either {@link android.view.View#FOCUS_UP} or
     *        {@link android.view.View#FOCUS_DOWN}.
     * @param newSelectedPosition The position of the next selection.
     * @param newFocusAssigned whether new focus was assigned.  This matters because
     *        when something has focus, we don't want to show selection (ugh).
     */
    private void handleNewSelectionChange(View selectedView, int direction, int newSelectedPosition,
            boolean newFocusAssigned) {
        if (newSelectedPosition == INVALID_POSITION) {
            throw new IllegalArgumentException("newSelectedPosition needs to be valid");
        }

        // whether or not we are moving down or up, we want to preserve the
        // top of whatever view is on top:
        // - moving down: the view that had selection
        // - moving up: the view that is getting selection
        View topView;
        View bottomView;
        int topViewIndex, bottomViewIndex;
        boolean topSelected = false;
        final int selectedIndex = mSelectedPosition - mFirstPosition;
        final int nextSelectedIndex = newSelectedPosition - mFirstPosition;
        if (direction == View.FOCUS_UP) {
            topViewIndex = nextSelectedIndex;
            bottomViewIndex = selectedIndex;
            topView = getChildAt(topViewIndex);
            bottomView = selectedView;
            topSelected = true;
        } else {
            topViewIndex = selectedIndex;
            bottomViewIndex = nextSelectedIndex;
            topView = selectedView;
            bottomView = getChildAt(bottomViewIndex);
        }

        final int numChildren = getChildCount();

        // start with top view: is it changing size?
        if (topView != null) {
            topView.setSelected(!newFocusAssigned && topSelected);
            measureAndAdjustDown(topView, topViewIndex, numChildren);
        }

        // is the bottom view changing size?
        if (bottomView != null) {
            bottomView.setSelected(!newFocusAssigned && !topSelected);
            measureAndAdjustDown(bottomView, bottomViewIndex, numChildren);
        }
    }

    /**
     * Re-measure a child, and if its height changes, lay it out preserving its
     * top, and adjust the children below it appropriately.
     * @param child The child
     * @param childIndex The view group index of the child.
     * @param numChildren The number of children in the view group.
     */
    private void measureAndAdjustDown(View child, int childIndex, int numChildren) {
        int oldHeight = child.getHeight();
        measureItem(child);
        if (child.getMeasuredHeight() != oldHeight) {
            // lay out the view, preserving its top
            relayoutMeasuredItem(child);

            // adjust views below appropriately
            final int heightDelta = child.getMeasuredHeight() - oldHeight;
            for (int i = childIndex + 1; i < numChildren; i++) {
                getChildAt(i).offsetTopAndBottom(heightDelta);
            }
        }
    }

    /**
     * Measure a particular list child.
     * TODO: unify with setUpChild.
     * @param child The child.
     */
    private void measureItem(View child) {
        ViewGroup.LayoutParams p = child.getLayoutParams();
        if (p == null) {
            p = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        int childWidthSpec = ViewGroup.getChildMeasureSpec(mWidthMeasureSpec,
                mListPadding.left + mListPadding.right, p.width);
        int lpHeight = p.height;
        int childHeightSpec;
        if (lpHeight > 0) {
            childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY);
        } else {
            if(android.os.Build.VERSION.SDK_INT < 23) {
                childHeightSpec = 0;
            } else {
                childHeightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.UNSPECIFIED);
            }
        }
        child.measure(childWidthSpec, childHeightSpec);
    }

    /**
     * Layout a child that has been measured, preserving its top position.
     * TODO: unify with setUpChild.
     * @param child The child.
     */
    private void relayoutMeasuredItem(View child) {
        final int w = child.getMeasuredWidth();
        final int h = child.getMeasuredHeight();
        final int childLeft = mListPadding.left;
        final int childRight = childLeft + w;
        final int childTop = child.getTop();
        final int childBottom = childTop + h;
        child.layout(childLeft, childTop, childRight, childBottom);
    }

    /**
     * @return The amount to preview next items when arrow srolling.
     */
    private int getArrowScrollPreviewLength() {
        return Math.max(MIN_SCROLL_PREVIEW_PIXELS, getVerticalFadingEdgeLength());
    }

    /**
     * Determine how much we need to scroll in order to get the next selected view
     * visible, with a fading edge showing below as applicable.  The amount is
     * capped at {@link #getMaxScrollAmount()} .
     *
     * @param direction either {@link android.view.View#FOCUS_UP} or
     *        {@link android.view.View#FOCUS_DOWN}.
     * @param nextSelectedPosition The position of the next selection, or
     *        {@link #INVALID_POSITION} if there is no next selectable position
     * @return The amount to scroll. Note: this is always positive!  Direction
     *         needs to be taken into account when actually scrolling.
     */
    private int amountToScroll(int direction, int nextSelectedPosition) {
        final int listBottom = getHeight() - mListPadding.bottom;
        final int listTop = mListPadding.top;

        int numChildren = getChildCount();

        if (direction == View.FOCUS_DOWN) {
            int indexToMakeVisible = numChildren - 1;
            if (nextSelectedPosition != INVALID_POSITION) {
                indexToMakeVisible = nextSelectedPosition - mFirstPosition;
            }
            while (numChildren <= indexToMakeVisible) {
                // Child to view is not attached yet.
                addViewBelow(getChildAt(numChildren - 1), mFirstPosition + numChildren - 1);
                numChildren++;
            }
            final int positionToMakeVisible = mFirstPosition + indexToMakeVisible;
            final View viewToMakeVisible = getChildAt(indexToMakeVisible);

            int goalBottom = listBottom;
            if (positionToMakeVisible < mItemCount - 1) {
                goalBottom -= getArrowScrollPreviewLength();
            }

            if (viewToMakeVisible.getBottom() <= goalBottom) {
                // item is fully visible.
                return 0;
            }

            if (nextSelectedPosition != INVALID_POSITION
                    && (goalBottom - viewToMakeVisible.getTop()) >= getMaxScrollAmount()) {
                // item already has enough of it visible, changing selection is good enough
                return 0;
            }

            int amountToScroll = (viewToMakeVisible.getBottom() - goalBottom);

            if ((mFirstPosition + numChildren) == mItemCount) {
                // last is last in list -> make sure we don't scroll past it
                final int max = getChildAt(numChildren - 1).getBottom() - listBottom;
                amountToScroll = Math.min(amountToScroll, max);
            }

            return Math.min(amountToScroll, getMaxScrollAmount());
        } else {
            int indexToMakeVisible = 0;
            if (nextSelectedPosition != INVALID_POSITION) {
                indexToMakeVisible = nextSelectedPosition - mFirstPosition;
            }
            while (indexToMakeVisible < 0) {
                // Child to view is not attached yet.
                addViewAbove(getChildAt(0), mFirstPosition);
                mFirstPosition--;
                indexToMakeVisible = nextSelectedPosition - mFirstPosition;
            }
            final int positionToMakeVisible = mFirstPosition + indexToMakeVisible;
            final View viewToMakeVisible = getChildAt(indexToMakeVisible);
            int goalTop = listTop;
            if (positionToMakeVisible > 0) {
                goalTop += getArrowScrollPreviewLength();
            }
            if (viewToMakeVisible.getTop() >= goalTop) {
                // item is fully visible.
                return 0;
            }

            if (nextSelectedPosition != INVALID_POSITION &&
                    (viewToMakeVisible.getBottom() - goalTop) >= getMaxScrollAmount()) {
                // item already has enough of it visible, changing selection is good enough
                return 0;
            }

            int amountToScroll = (goalTop - viewToMakeVisible.getTop());
            if (mFirstPosition == 0) {
                // first is first in list -> make sure we don't scroll past it
                final int max = listTop - getChildAt(0).getTop();
                amountToScroll = Math.min(amountToScroll,  max);
            }
            return Math.min(amountToScroll, getMaxScrollAmount());
        }
    }

    /**
     * Holds results of focus aware arrow scrolling.
     */
    static private class ArrowScrollFocusResult {
        private int mSelectedPosition;
        private int mAmountToScroll;

        /**
         * How {@link android.widget.ListView#arrowScrollFocused} returns its values.
         */
        void populate(int selectedPosition, int amountToScroll) {
            mSelectedPosition = selectedPosition;
            mAmountToScroll = amountToScroll;
        }

        public int getSelectedPosition() {
            return mSelectedPosition;
        }

        public int getAmountToScroll() {
            return mAmountToScroll;
        }
    }

    /**
     * @param direction either {@link android.view.View#FOCUS_UP} or
     *        {@link android.view.View#FOCUS_DOWN}.
     * @return The position of the next selectable position of the views that
     *         are currently visible, taking into account the fact that there might
     *         be no selection.  Returns {@link #INVALID_POSITION} if there is no
     *         selectable view on screen in the given direction.
     */
    private int lookForSelectablePositionOnScreen(int direction) {
        final int firstPosition = mFirstPosition;
        if (direction == View.FOCUS_DOWN) {
            int startPos = (mSelectedPosition != INVALID_POSITION) ?
                    mSelectedPosition + 1 :
                    firstPosition;
            if (startPos >= mAdapter.getCount()) {
                return INVALID_POSITION;
            }
            if (startPos < firstPosition) {
                startPos = firstPosition;
            }
            
            final int lastVisiblePos = getLastVisiblePosition();
            final ListAdapter adapter = getAdapter();
            for (int pos = startPos; pos <= lastVisiblePos; pos++) {
                if (adapter.isEnabled(pos)
                        && getChildAt(pos - firstPosition).getVisibility() == View.VISIBLE) {
                    return pos;
                }
            }
        } else {
            int last = firstPosition + getChildCount() - 1;
            int startPos = (mSelectedPosition != INVALID_POSITION) ?
                    mSelectedPosition - 1 :
                    firstPosition + getChildCount() - 1;
            if (startPos < 0 || startPos >= mAdapter.getCount()) {
                return INVALID_POSITION;
            }
            if (startPos > last) {
                startPos = last;
            }

            final ListAdapter adapter = getAdapter();
            for (int pos = startPos; pos >= firstPosition; pos--) {
                if (adapter.isEnabled(pos)
                        && getChildAt(pos - firstPosition).getVisibility() == View.VISIBLE) {
                    return pos;
                }
            }
        }
        return INVALID_POSITION;
    }

    /**
     * Do an arrow scroll based on focus searching.  If a new view is
     * given focus, return the selection delta and amount to scroll via
     * an {@link ArrowScrollFocusResult}, otherwise, return null.
     *
     * @param direction either {@link android.view.View#FOCUS_UP} or
     *        {@link android.view.View#FOCUS_DOWN}.
     * @return The result if focus has changed, or <code>null</code>.
     */
    private ArrowScrollFocusResult arrowScrollFocused(final int direction) {
        final View selectedView = getSelectedView();
        View newFocus;
        if (selectedView != null && selectedView.hasFocus()) {
            View oldFocus = selectedView.findFocus();
            newFocus = FocusFinder.getInstance().findNextFocus(this, oldFocus, direction);
        } else {
            if (direction == View.FOCUS_DOWN) {
                final boolean topFadingEdgeShowing = (mFirstPosition > 0);
                final int listTop = mListPadding.top +
                        (topFadingEdgeShowing ? getArrowScrollPreviewLength() : 0);
                final int ySearchPoint =
                        (selectedView != null && selectedView.getTop() > listTop) ?
                                selectedView.getTop() :
                                listTop;
                mTempRect.set(0, ySearchPoint, 0, ySearchPoint);
            } else {
                final boolean bottomFadingEdgeShowing =
                        (mFirstPosition + getChildCount() - 1) < mItemCount;
                final int listBottom = getHeight() - mListPadding.bottom -
                        (bottomFadingEdgeShowing ? getArrowScrollPreviewLength() : 0);
                final int ySearchPoint =
                        (selectedView != null && selectedView.getBottom() < listBottom) ?
                                selectedView.getBottom() :
                                listBottom;
                mTempRect.set(0, ySearchPoint, 0, ySearchPoint);
            }
            newFocus = FocusFinder.getInstance().findNextFocusFromRect(this, mTempRect, direction);
        }

        if (newFocus != null) {
            final int positionOfNewFocus = positionOfNewFocus(newFocus);

            // if the focus change is in a different new position, make sure
            // we aren't jumping over another selectable position
            if (mSelectedPosition != INVALID_POSITION && positionOfNewFocus != mSelectedPosition) {
                final int selectablePosition = lookForSelectablePositionOnScreen(direction);
                if (selectablePosition != INVALID_POSITION &&
                        ((direction == View.FOCUS_DOWN && selectablePosition < positionOfNewFocus) ||
                        (direction == View.FOCUS_UP && selectablePosition > positionOfNewFocus))) {
                    return null;
                }
            }

            int focusScroll = amountToScrollToNewFocus(direction, newFocus, positionOfNewFocus);

            final int maxScrollAmount = getMaxScrollAmount();
            if (focusScroll < maxScrollAmount) {
                // not moving too far, safe to give next view focus
                newFocus.requestFocus(direction);
                mArrowScrollFocusResult.populate(positionOfNewFocus, focusScroll);
                return mArrowScrollFocusResult;
            } else if (distanceToView(newFocus) < maxScrollAmount){
                // Case to consider:
                // too far to get entire next focusable on screen, but by going
                // max scroll amount, we are getting it at least partially in view,
                // so give it focus and scroll the max ammount.
                newFocus.requestFocus(direction);
                mArrowScrollFocusResult.populate(positionOfNewFocus, maxScrollAmount);
                return mArrowScrollFocusResult;
            }
        }
        return null;
    }

    /**
     * @param newFocus The view that would have focus.
     * @return the position that contains newFocus
     */
    private int positionOfNewFocus(View newFocus) {
        final int numChildren = getChildCount();
        for (int i = 0; i < numChildren; i++) {
            final View child = getChildAt(i);
            if (isViewAncestorOf(newFocus, child)) {
                return mFirstPosition + i;
            }
        }
        throw new IllegalArgumentException("newFocus is not a child of any of the"
                + " children of the list!");
    }

    /**
     * Return true if child is an ancestor of parent, (or equal to the parent).
     */
    private boolean isViewAncestorOf(View child, View parent) {
        if (child == parent) {
            return true;
        }

        final ViewParent theParent = child.getParent();
        return (theParent instanceof ViewGroup) && isViewAncestorOf((View) theParent, parent);
    }

    /**
     * Determine how much we need to scroll in order to get newFocus in view.
     * @param direction either {@link android.view.View#FOCUS_UP} or
     *        {@link android.view.View#FOCUS_DOWN}.
     * @param newFocus The view that would take focus.
     * @param positionOfNewFocus The position of the list item containing newFocus
     * @return The amount to scroll.  Note: this is always positive!  Direction
     *   needs to be taken into account when actually scrolling.
     */
    private int amountToScrollToNewFocus(int direction, View newFocus, int positionOfNewFocus) {
        int amountToScroll = 0;
        newFocus.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(newFocus, mTempRect);
        if (direction == View.FOCUS_UP) {
            if (mTempRect.top < mListPadding.top) {
                amountToScroll = mListPadding.top - mTempRect.top;
                if (positionOfNewFocus > 0) {
                    amountToScroll += getArrowScrollPreviewLength();
                }
            }
        } else {
            final int listBottom = getHeight() - mListPadding.bottom;
            if (mTempRect.bottom > listBottom) {
                amountToScroll = mTempRect.bottom - listBottom;
                if (positionOfNewFocus < mItemCount - 1) {
                    amountToScroll += getArrowScrollPreviewLength();
                }
            }
        }
        return amountToScroll;
    }

    /**
     * Determine the distance to the nearest edge of a view in a particular
     * direction.
     * 
     * @param descendant A descendant of this list.
     * @return The distance, or 0 if the nearest edge is already on screen.
     */
    private int distanceToView(View descendant) {
        int distance = 0;
        descendant.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(descendant, mTempRect);
        final int listBottom = mBottom - mTop - mListPadding.bottom;
        if (mTempRect.bottom < mListPadding.top) {
            distance = mListPadding.top - mTempRect.bottom;
        } else if (mTempRect.top > listBottom) {
            distance = mTempRect.top - listBottom;
        }
        return distance;
    }


    /**
     * Scroll the children by amount, adding a view at the end and removing
     * views that fall off as necessary.
     *
     * @param amount The amount (positive or negative) to scroll.
     */
    private void scrollListItemsBy(int amount) {
        offsetChildrenTopAndBottom(amount);
        //tws-start add listview bounce feature::2014-08-12
        final int listBottom = getHeight() - mListPadding.bottom - mLastBottomOffset;
        final int listTop = mListPadding.top + mFirstTopOffset;
        //tws-end add listview bounce feature::2014-08-12
        final AbsListView.RecycleBin recycleBin = mRecycler;

        if (amount < 0) {
            // shifted items up

            // may need to pan views into the bottom space
            int numChildren = getChildCount();
            View last = getChildAt(numChildren - 1);
            while (last.getBottom() < listBottom) {
                final int lastVisiblePosition = mFirstPosition + numChildren - 1;
                if (lastVisiblePosition < mItemCount - 1) {
                    last = addViewBelow(last, lastVisiblePosition);
                    numChildren++;
                } else {
                    break;
                }
            }

            // may have brought in the last child of the list that is skinnier
            // than the fading edge, thereby leaving space at the end.  need
            // to shift back
            if (last.getBottom() < listBottom) {
                offsetChildrenTopAndBottom(listBottom - last.getBottom());
            }

            // top views may be panned off screen
            View first = getChildAt(0);
            while (first.getBottom() < listTop) {
                AbsListView.LayoutParams layoutParams = (LayoutParams) first.getLayoutParams();
                if (recycleBin.shouldRecycleViewType(layoutParams.viewType)) {
                    recycleBin.addScrapView(first, mFirstPosition);
                }
                detachViewFromParent(first);
                first = getChildAt(0);
                mFirstPosition++;
            }
        } else {
            // shifted items down
            View first = getChildAt(0);

            // may need to pan views into top
            while ((first.getTop() > listTop) && (mFirstPosition > 0)) {
                first = addViewAbove(first, mFirstPosition);
                mFirstPosition--;
            }

            // may have brought the very first child of the list in too far and
            // need to shift it back
            if (first.getTop() > listTop) {
                offsetChildrenTopAndBottom(listTop - first.getTop());
            }

            int lastIndex = getChildCount() - 1;
            View last = getChildAt(lastIndex);

            // bottom view may be panned off screen
            while (last.getTop() > listBottom) {
                AbsListView.LayoutParams layoutParams = (LayoutParams) last.getLayoutParams();
                if (recycleBin.shouldRecycleViewType(layoutParams.viewType)) {
                    recycleBin.addScrapView(last, mFirstPosition+lastIndex);
                }
                detachViewFromParent(last);
                last = getChildAt(--lastIndex);
            }
        }
    }

    private View addViewAbove(View theView, int position) {
        int abovePosition = position - 1;
        View view = obtainView(abovePosition, mIsScrap);
        int edgeOfNewChild = theView.getTop() - mDividerHeight;
        setupChild(view, abovePosition, edgeOfNewChild, false, mListPadding.left,
                false, mIsScrap[0]);
        return view;
    }

    private View addViewBelow(View theView, int position) {
        int belowPosition = position + 1;
        View view = obtainView(belowPosition, mIsScrap);
        int edgeOfNewChild = theView.getBottom() + mDividerHeight;
        setupChild(view, belowPosition, edgeOfNewChild, true, mListPadding.left,
                false, mIsScrap[0]);
        return view;
    }

    /**
     * Indicates that the views created by the ListAdapter can contain focusable
     * items.
     *
     * @param itemsCanFocus true if items can get focus, false otherwise
     */
    public void setItemsCanFocus(boolean itemsCanFocus) {
        mItemsCanFocus = itemsCanFocus;
        if (!itemsCanFocus) {
            setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
    }

    /**
     * @return Whether the views created by the ListAdapter can contain focusable
     * items.
     */
    public boolean getItemsCanFocus() {
        return mItemsCanFocus;
    }

    @Override
    public boolean isOpaque() {
        boolean retValue = (mCachingActive && mIsCacheColorOpaque && mDividerIsOpaque &&
                hasOpaqueScrollbars()) || super.isOpaque();
        if (retValue) {
            // only return true if the list items cover the entire area of the view
        	//tws-start add listview bounce feature::2014-08-12
            final int listTop = mListPadding != null ? mListPadding.top + mFirstTopOffset : mPaddingTop;
            //tws-end add listview bounce feature::2014-08-12
            View first = getChildAt(0);
            if (first == null || first.getTop() > listTop) {
                return false;
            }
            //tws-start add listview bounce feature::2014-08-12
            final int listBottom = getHeight() -
                    (mListPadding != null ? mListPadding.bottom + mLastBottomOffset: mPaddingBottom);
            //tws-end add listview bounce feature::2014-08-12
            View last = getChildAt(getChildCount() - 1);
            if (last == null || last.getBottom() < listBottom) {
                return false;
            }
        }
        return retValue;
    }

    @Override
    public void setCacheColorHint(int color) {
        final boolean opaque = (color >>> 24) == 0xFF;
        mIsCacheColorOpaque = opaque;
        if (opaque) {
            if (mDividerPaint == null) {
                mDividerPaint = new Paint();
            }
            mDividerPaint.setColor(color);
        }
        super.setCacheColorHint(color);
    }

    void drawOverscrollHeader(Canvas canvas, Drawable drawable, Rect bounds) {
        final int height = drawable.getMinimumHeight();

        canvas.save();
        canvas.clipRect(bounds);

        final int span = bounds.bottom - bounds.top;
        if (span < height) {
            bounds.top = bounds.bottom - height;
        }

        drawable.setBounds(bounds);
        drawable.draw(canvas);

        canvas.restore();
    }

    void drawOverscrollFooter(Canvas canvas, Drawable drawable, Rect bounds) {
        final int height = drawable.getMinimumHeight();

        canvas.save();
        canvas.clipRect(bounds);

        final int span = bounds.bottom - bounds.top;
        if (span < height) {
            bounds.bottom = bounds.top + height;
        }

        drawable.setBounds(bounds);
        drawable.draw(canvas);

        canvas.restore();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mCachingStarted) {
            mCachingActive = true;
        }

        // Draw the dividers
        final int dividerHeight = mDividerHeight;
        final Drawable overscrollHeader = mOverScrollHeader;
        final Drawable overscrollFooter = mOverScrollFooter;
        final boolean drawOverscrollHeader = overscrollHeader != null;
        final boolean drawOverscrollFooter = overscrollFooter != null;
        final boolean drawDividers = dividerHeight > 0 && mDivider != null;

        if (drawDividers || drawOverscrollHeader || drawOverscrollFooter) {
            // Only modify the top and bottom in the loop, we set the left and right here
            final Rect bounds = mTempRect;
            bounds.left = mPaddingLeft;
            bounds.right = mRight - mLeft - mPaddingRight;

            //tws-start add listview bounce feature::2014-08-12
            final int mBottom = getBottom();
            final int mTop = getTop();
            final int mScrollY = getScrollY();
            //tws-end add listview bounce feature::2014-08-12
            final int count = getChildCount();
            final int headerCount = mHeaderViewInfos.size();
            final int itemCount = mItemCount;
            final int footerLimit = (itemCount - mFooterViewInfos.size());
            final boolean headerDividers = mHeaderDividersEnabled;
            final boolean footerDividers = mFooterDividersEnabled;
            final int first = mFirstPosition;
            final boolean areAllItemsSelectable = mAreAllItemsSelectable;
            final ListAdapter adapter = mAdapter;
            // If the list is opaque *and* the background is not, we want to
            // fill a rect where the dividers would be for non-selectable items
            // If the list is opaque and the background is also opaque, we don't
            // need to draw anything since the background will do it for us
            final boolean fillForMissingDividers = isOpaque() && !super.isOpaque();

            if (fillForMissingDividers && mDividerPaint == null && mIsCacheColorOpaque) {
                mDividerPaint = new Paint();
                mDividerPaint.setColor(getCacheColorHint());
            }
            final Paint paint = mDividerPaint;

            int effectivePaddingTop = 0;
            int effectivePaddingBottom = 0;
            if ((mGroupFlags & CLIP_TO_PADDING_MASK) == CLIP_TO_PADDING_MASK) {
                effectivePaddingTop = mListPadding.top;
                effectivePaddingBottom = mListPadding.bottom;
            }

            final int listBottom = mBottom - mTop - effectivePaddingBottom + mScrollY;
            if (!mStackFromBottom) {
                int bottom = 0;
                
                // Draw top divider or header for overscroll
                final int scrollY = mScrollY;
                if (count > 0 && scrollY < 0) {
                    final int firstTop = getChildAt(0).getTop();
                    if (drawOverscrollHeader) {
                        bounds.bottom = 0;
                        bounds.top = scrollY;
                        drawOverscrollHeader(canvas, overscrollHeader, bounds);
                    //} else if (drawDividers) {
                    //    bounds.bottom = 0;
                    //    bounds.top = -dividerHeight;
                    //    drawDivider(canvas, bounds, -1);
                    } else if(drawDividers && firstTop > 0){
                    	bounds.top = firstTop - dividerHeight;
                    	bounds.bottom = firstTop;
                    	drawDivider(canvas, bounds, 0);
                	}
                }

                for (int i = 0; i < count; i++) {
                    final int itemIndex = (first + i);
                    final boolean isHeader = (itemIndex < headerCount);
                    final boolean isFooter = (itemIndex >= footerLimit);
                    if ((headerDividers || !isHeader) && (footerDividers || !isFooter)) {
                        final View child = getChildAt(i);
                        bottom = child.getBottom();
                        final boolean isLastItem = (i == (count - 1));

                        if (drawDividers && (bottom < listBottom)
                                && !(drawOverscrollFooter && isLastItem)) {
                            final int nextIndex = (itemIndex + 1);
                            // Draw dividers between enabled items, headers
                            // and/or footers when enabled and requested, and
                            // after the last enabled item.
                            if (adapter.isEnabled(itemIndex) && (headerDividers || !isHeader
                                    && (nextIndex >= headerCount)) && (isLastItem
                                    || adapter.isEnabled(nextIndex) && (footerDividers || !isFooter
                                            && (nextIndex < footerLimit)))) {
                                bounds.top = bottom;
                                bounds.bottom = bottom + dividerHeight;
                                drawDivider(canvas, bounds, i);
                            } else if (fillForMissingDividers) {
                                bounds.top = bottom;
                                bounds.bottom = bottom + dividerHeight;
                                canvas.drawRect(bounds, paint);
                            }
                        }
                    }
                }

                final int overFooterBottom = mBottom + mScrollY;
                if (drawOverscrollFooter && first + count == itemCount &&
                        overFooterBottom > bottom) {
                    bounds.top = bottom;
                    bounds.bottom = overFooterBottom;
                    drawOverscrollFooter(canvas, overscrollFooter, bounds);
                }
            } else {
                int top;

                final int scrollY = mScrollY;

                if (count > 0 && drawOverscrollHeader) {
                    bounds.top = scrollY;
                    bounds.bottom = getChildAt(0).getTop();
                    drawOverscrollHeader(canvas, overscrollHeader, bounds);
                }

                final int start = drawOverscrollHeader ? 1 : 0;
                for (int i = start; i < count; i++) {
                    final int itemIndex = (first + i);
                    final boolean isHeader = (itemIndex < headerCount);
                    final boolean isFooter = (itemIndex >= footerLimit);
                    if ((headerDividers || !isHeader) && (footerDividers || !isFooter)) {
                        final View child = getChildAt(i);
                        top = child.getTop();
                        if (drawDividers && (top > effectivePaddingTop)) {
                            final boolean isFirstItem = (i == start);
                            final int previousIndex = (itemIndex - 1);
                            // Draw dividers between enabled items, headers
                            // and/or footers when enabled and requested, and
                            // before the first enabled item.
                            if (adapter.isEnabled(itemIndex) && (headerDividers || !isHeader
                                    && (previousIndex >= headerCount)) && (isFirstItem ||
                                    adapter.isEnabled(previousIndex) && (footerDividers || !isFooter
                                            && (previousIndex < footerLimit)))) {
                                bounds.top = top - dividerHeight;
                                bounds.bottom = top;
                                // Give the method the child ABOVE the divider,
                                // so we subtract one from our child position.
                                // Give -1 when there is no child above the
                                // divider.
                                drawDivider(canvas, bounds, i - 1);
                            } else if (fillForMissingDividers) {
                                bounds.top = top - dividerHeight;
                                bounds.bottom = top;
                                canvas.drawRect(bounds, paint);
                            }
                        }
                    }
                }
                
                if (count > 0 && scrollY > 0) {
                    if (drawOverscrollFooter) {
                        final int absListBottom = mBottom;
                        bounds.top = absListBottom;
                        bounds.bottom = absListBottom + scrollY;
                        drawOverscrollFooter(canvas, overscrollFooter, bounds);
                    } else if (drawDividers) {
                        bounds.top = listBottom;
                        bounds.bottom = listBottom + dividerHeight;
                        drawDivider(canvas, bounds, -1);
                    }
                }
            }
        }

        // Draw the indicators (these should be drawn above the dividers) and children
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean more = super.drawChild(canvas, child, drawingTime);
        if (mCachingActive && child.mCachingFailed) {
            mCachingActive = false;
        }
        return more;
    }

    /**
     * Draws a divider for the given child in the given bounds.
     *
     * @param canvas The canvas to draw to.
     * @param bounds The bounds of the divider.
     * @param childIndex The index of child (of the View) above the divider.
     *            This will be -1 if there is no child above the divider to be
     *            drawn.
     */
    void drawDivider(Canvas canvas, Rect bounds, int childIndex) {
        // This widget draws the same divider for all children
        final Drawable divider = mDivider;

        divider.setBounds(bounds);
        divider.draw(canvas);
    }

    /**
     * Returns the drawable that will be drawn between each item in the list.
     *
     * @return the current drawable drawn between list elements
     */
    public Drawable getDivider() {
        return mDivider;
    }

    /**
     * Sets the drawable that will be drawn between each item in the list. If the drawable does
     * not have an intrinsic height, you should also call {@link #setDividerHeight(int)}
     *
     * @param divider The drawable to use.
     */
    public void setDivider(Drawable divider) {
        if (divider != null) {
            mDividerHeight = divider.getIntrinsicHeight();
        } else {
            mDividerHeight = 0;
        }
        mDivider = divider;
        mDividerIsOpaque = divider == null || divider.getOpacity() == PixelFormat.OPAQUE;
        requestLayout();
        invalidate();
    }

    /**
     * @return Returns the height of the divider that will be drawn between each item in the list.
     */
    public int getDividerHeight() {
        return mDividerHeight;
    }
    
    /**
     * Sets the height of the divider that will be drawn between each item in the list. Calling
     * this will override the intrinsic height as set by {@link #setDivider(Drawable)}
     *
     * @param height The new height of the divider in pixels.
     */
    public void setDividerHeight(int height) {
        mDividerHeight = height;
        requestLayout();
        invalidate();
    }

    /**
     * Enables or disables the drawing of the divider for header views.
     *
     * @param headerDividersEnabled True to draw the headers, false otherwise.
     *
     * @see #setFooterDividersEnabled(boolean)
     * @see #areHeaderDividersEnabled()
     * @see #addHeaderView(android.view.View)
     */
    public void setHeaderDividersEnabled(boolean headerDividersEnabled) {
        mHeaderDividersEnabled = headerDividersEnabled;
        invalidate();
    }

    /**
     * @return Whether the drawing of the divider for header views is enabled
     *
     * @see #setHeaderDividersEnabled(boolean)
     */
    public boolean areHeaderDividersEnabled() {
        return mHeaderDividersEnabled;
    }

    /**
     * Enables or disables the drawing of the divider for footer views.
     *
     * @param footerDividersEnabled True to draw the footers, false otherwise.
     *
     * @see #setHeaderDividersEnabled(boolean)
     * @see #areFooterDividersEnabled()
     * @see #addFooterView(android.view.View)
     */
    public void setFooterDividersEnabled(boolean footerDividersEnabled) {
        mFooterDividersEnabled = footerDividersEnabled;
        invalidate();
    }

    /**
     * @return Whether the drawing of the divider for footer views is enabled
     *
     * @see #setFooterDividersEnabled(boolean)
     */
    public boolean areFooterDividersEnabled() {
        return mFooterDividersEnabled;
    }
    
    /**
     * Sets the drawable that will be drawn above all other list content.
     * This area can become visible when the user overscrolls the list.
     *
     * @param header The drawable to use
     */
    public void setOverscrollHeader(Drawable header) {
        mOverScrollHeader = header;
        if (mScrollY < 0) {
            invalidate();
        }
    }

    /**
     * @return The drawable that will be drawn above all other list content
     */
    public Drawable getOverscrollHeader() {
        return mOverScrollHeader;
    }

    /**
     * Sets the drawable that will be drawn below all other list content.
     * This area can become visible when the user overscrolls the list,
     * or when the list's content does not fully fill the container area.
     *
     * @param footer The drawable to use
     */
    public void setOverscrollFooter(Drawable footer) {
        mOverScrollFooter = footer;
        invalidate();
    }

    /**
     * @return The drawable that will be drawn below all other list content
     */
    public Drawable getOverscrollFooter() {
        return mOverScrollFooter;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        final ListAdapter adapter = mAdapter;
        int closetChildIndex = -1;
        int closestChildTop = 0;
        if (adapter != null && gainFocus && previouslyFocusedRect != null) {
            previouslyFocusedRect.offset(mScrollX, mScrollY);

            // Don't cache the result of getChildCount or mFirstPosition here,
            // it could change in layoutChildren.
            if (adapter.getCount() < getChildCount() + mFirstPosition) {
                mLayoutMode = LAYOUT_NORMAL;
                layoutChildren();
            }

            // figure out which item should be selected based on previously
            // focused rect
            Rect otherRect = mTempRect;
            int minDistance = Integer.MAX_VALUE;
            final int childCount = getChildCount();
            final int firstPosition = mFirstPosition;

            for (int i = 0; i < childCount; i++) {
                // only consider selectable views
                if (!adapter.isEnabled(firstPosition + i)) {
                    continue;
                }

                View other = getChildAt(i);
                other.getDrawingRect(otherRect);
                offsetDescendantRectToMyCoords(other, otherRect);
                int distance = getDistance(previouslyFocusedRect, otherRect, direction);

                if (distance < minDistance) {
                    minDistance = distance;
                    closetChildIndex = i;
                    closestChildTop = other.getTop();
                }
            }
        }

        if (closetChildIndex >= 0) {
            setSelectionFromTop(closetChildIndex + mFirstPosition, closestChildTop);
        } else {
            requestLayout();
        }
    }


    /*
     * (non-Javadoc)
     *
     * Children specified in XML are assumed to be header views. After we have
     * parsed them move them out of the children list and into mHeaderViews.
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        int count = getChildCount();
        if (count > 0) {
            for (int i = 0; i < count; ++i) {
                addHeaderView(getChildAt(i));
            }
            removeAllViews();
        }
    }
	
    /* (non-Javadoc)
     * @see android.view.View#findViewById(int)
     * First look in our children, then in any header and footer views that may be scrolled off.
     */
    @Override
    protected View findViewTraversal(int id) {
        View v;
        v = super.findViewTraversal(id);
        if (v == null) {
            v = findViewInHeadersOrFooters(mHeaderViewInfos, id);
            if (v != null) {
                return v;
            }
            v = findViewInHeadersOrFooters(mFooterViewInfos, id);
            if (v != null) {
                return v;
            }
        }
        return v;
    }

    /* (non-Javadoc)
     *
     * Look in the passed in list of headers or footers for the view.
     */
    View findViewInHeadersOrFooters(ArrayList<FixedViewInfo> where, int id) {
        if (where != null) {
            int len = where.size();
            View v;

            for (int i = 0; i < len; i++) {
                v = where.get(i).view;

                if (!v.isRootNamespace()) {
                    v = v.findViewById(id);

                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    /* (non-Javadoc)
     * @see android.view.View#findViewWithTag(Object)
     * First look in our children, then in any header and footer views that may be scrolled off.
     */
    @Override
    protected View findViewWithTagTraversal(Object tag) {
        View v;
        v = super.findViewWithTagTraversal(tag);
        if (v == null) {
            v = findViewWithTagInHeadersOrFooters(mHeaderViewInfos, tag);
            if (v != null) {
                return v;
            }

            v = findViewWithTagInHeadersOrFooters(mFooterViewInfos, tag);
            if (v != null) {
                return v;
            }
        }
        return v;
    }

    /* (non-Javadoc)
     *
     * Look in the passed in list of headers or footers for the view with the tag.
     */
    View findViewWithTagInHeadersOrFooters(ArrayList<FixedViewInfo> where, Object tag) {
        if (where != null) {
            int len = where.size();
            View v;

            for (int i = 0; i < len; i++) {
                v = where.get(i).view;

                if (!v.isRootNamespace()) {
                    v = v.findViewWithTag(tag);

                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    /**
     * @hide
     * @see android.view.View#findViewByPredicate(Predicate)
     * First look in our children, then in any header and footer views that may be scrolled off.
     */
    @Override
    protected View findViewByPredicateTraversal(Predicate<View> predicate, View childToSkip) {
        View v;
        v = super.findViewByPredicateTraversal(predicate, childToSkip);
        if (v == null) {
            v = findViewByPredicateInHeadersOrFooters(mHeaderViewInfos, predicate, childToSkip);
            if (v != null) {
                return v;
            }

            v = findViewByPredicateInHeadersOrFooters(mFooterViewInfos, predicate, childToSkip);
            if (v != null) {
                return v;
            }
        }
        return v;
    }

    /* (non-Javadoc)
     *
     * Look in the passed in list of headers or footers for the first view that matches
     * the predicate.
     */
    View findViewByPredicateInHeadersOrFooters(ArrayList<FixedViewInfo> where,
            Predicate<View> predicate, View childToSkip) {
        if (where != null) {
            int len = where.size();
            View v;

            for (int i = 0; i < len; i++) {
                v = where.get(i).view;

                if (v != childToSkip && !v.isRootNamespace()) {
                    v = v.findViewByPredicate(predicate);

                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the set of checked items ids. The result is only valid if the
     * choice mode has not been set to {@link #CHOICE_MODE_NONE}.
     * 
     * @return A new array which contains the id of each checked item in the
     *         list.
     *         
     * @deprecated Use {@link #getCheckedItemIds()} instead.
     */
    @Deprecated
    public long[] getCheckItemIds() {
        // Use new behavior that correctly handles stable ID mapping.
        if (mAdapter != null && mAdapter.hasStableIds()) {
            return getCheckedItemIds();
        }

        // Old behavior was buggy, but would sort of work for adapters without stable IDs.
        // Fall back to it to support legacy apps.
        if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null && mAdapter != null) {
            final SparseBooleanArray states = mCheckStates;
            final int count = states.size();
            final long[] ids = new long[count];
            final ListAdapter adapter = mAdapter;

            int checkedCount = 0;
            for (int i = 0; i < count; i++) {
                if (states.valueAt(i)) {
                    ids[checkedCount++] = adapter.getItemId(states.keyAt(i));
                }
            }

            // Trim array if needed. mCheckStates may contain false values
            // resulting in checkedCount being smaller than count.
            if (checkedCount == count) {
                return ids;
            } else {
                final long[] result = new long[checkedCount];
                System.arraycopy(ids, 0, result, 0, checkedCount);

                return result;
            }
        }
        return new long[0];
    }

    @Override
    int getHeightForPosition(int position) {
        final int height = super.getHeightForPosition(position);
        if (shouldAdjustHeightForDivider(position)) {
            return height + mDividerHeight;
        }
        return height;
    }

    private boolean shouldAdjustHeightForDivider(int itemIndex) {
        final int dividerHeight = mDividerHeight;
        final Drawable overscrollHeader = mOverScrollHeader;
        final Drawable overscrollFooter = mOverScrollFooter;
        final boolean drawOverscrollHeader = overscrollHeader != null;
        final boolean drawOverscrollFooter = overscrollFooter != null;
        final boolean drawDividers = dividerHeight > 0 && mDivider != null;

        if (drawDividers) {
            final boolean fillForMissingDividers = isOpaque() && !super.isOpaque();
            final int itemCount = mItemCount;
            final int headerCount = mHeaderViewInfos.size();
            final int footerLimit = (itemCount - mFooterViewInfos.size());
            final boolean isHeader = (itemIndex < headerCount);
            final boolean isFooter = (itemIndex >= footerLimit);
            final boolean headerDividers = mHeaderDividersEnabled;
            final boolean footerDividers = mFooterDividersEnabled;
            if ((headerDividers || !isHeader) && (footerDividers || !isFooter)) {
                final ListAdapter adapter = mAdapter;
                if (!mStackFromBottom) {
                    final boolean isLastItem = (itemIndex == (itemCount - 1));
                    if (!drawOverscrollFooter || !isLastItem) {
                        final int nextIndex = itemIndex + 1;
                        // Draw dividers between enabled items, headers
                        // and/or footers when enabled and requested, and
                        // after the last enabled item.
                        if (adapter.isEnabled(itemIndex) && (headerDividers || !isHeader
                                && (nextIndex >= headerCount)) && (isLastItem
                                || adapter.isEnabled(nextIndex) && (footerDividers || !isFooter
                                                && (nextIndex < footerLimit)))) {
                            return true;
                        } else if (fillForMissingDividers) {
                            return true;
                        }
                    }
                } else {
                    final int start = drawOverscrollHeader ? 1 : 0;
                    final boolean isFirstItem = (itemIndex == start);
                    if (!isFirstItem) {
                        final int previousIndex = (itemIndex - 1);
                        // Draw dividers between enabled items, headers
                        // and/or footers when enabled and requested, and
                        // before the first enabled item.
                        if (adapter.isEnabled(itemIndex) && (headerDividers || !isHeader
                                && (previousIndex >= headerCount)) && (isFirstItem ||
                                adapter.isEnabled(previousIndex) && (footerDividers || !isFooter
                                        && (previousIndex < footerLimit)))) {
                            return true;
                        } else if (fillForMissingDividers) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /*tws-start::added 2012-09-18*/
    final int twsMeasureHeightOfChildren(int widthMeasureSpec, int startPosition, int endPosition,
            final int maxHeight, int disallowPartialChildPosition) {

        final ListAdapter adapter = mAdapter;
        if (adapter == null) {
            return mListPadding.top + mListPadding.bottom;
        }

        // Include the padding of the list
        int returnedHeight = mListPadding.top + mListPadding.bottom;
        final int dividerHeight = ((mDividerHeight > 0) && mDivider != null) ? mDividerHeight : 0;
        // The previous height value that was less than maxHeight and contained
        // no partial children
        int i;
        View child;

        // mItemCount - 1 since endPosition parameter is inclusive
        endPosition = (endPosition == NO_POSITION) ? adapter.getCount() - 1 : endPosition;
        final AbsListView.RecycleBin recycleBin = mRecycler;
        final boolean recyle = recycleOnMeasure();
        final boolean[] isScrap = mIsScrap;

        for (i = startPosition; i <= endPosition; ++i) {
            child = obtainView(i, isScrap);
            //Log.d("listview","measureHeightOfChildren measureHeightOfChildren measureHeightOfChildren");
            measureScrapChild(child, i, widthMeasureSpec, 0);

            /*if (i > 0) {
                // Count the divider for all but one child
                returnedHeight += dividerHeight;
            }*/

            // Recycle the view before we possibly return from the method
            if (recyle && recycleBin.shouldRecycleViewType(
                    ((LayoutParams) child.getLayoutParams()).viewType)) {
                recycleBin.addScrapView(child, -1);
            }

            returnedHeight += child.getMeasuredHeight();

            
        }

        // At this point, we went through the range of children, and they each
        // completely fit, so return the returnedHeight
        return returnedHeight;
    }
    /*tws-end::added 2012-09-18*/

    //tws-start::add an empty view to the listview's parent 2013-06-03
    //when listview is empty, the empty view will be shown
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if(mShowEmptyList) {
            // if the empty view should be displayed, add the empty view
            addEmptyView();
        }
    }

    private void addEmptyView() {
        LayoutInflater inflater = (LayoutInflater)
            mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View emptyView = inflater.inflate(R.layout.empty_list, (ViewGroup)getParent(), false);

        // R.id.empty_list_host_1 is the top framelayout, R.id.empty_list_host_2 is the sub framelayout
        // R.id.empty_list_host_3 is the sub linearlayout of empty_list_host_2, and it's the parent of
        // emptyListIcon, emptyListText and emptyListSecondText
        EmptyViewOnLayoutChangeListener listener = new EmptyViewOnLayoutChangeListener();
        emptyView.addOnLayoutChangeListener(listener);
        emptyView.findViewById(R.id.empty_list_host_3).addOnLayoutChangeListener(listener);

        View emptyListHost = emptyView.findViewById(R.id.empty_list_host_2);
        emptyListHost.setBackgroundResource(mEmptyListBackground);

        ImageView icon = (ImageView)emptyView.findViewById(R.id.empty_list_icon);
        if(mEmptyListIcon != 0) {
            icon.setImageResource(mEmptyListIcon);
        } else {
            icon.setVisibility(View.GONE);
        }

        TextView text = (TextView)emptyView.findViewById(R.id.empty_list_text);
        if(mEmptyListText != 0) {
            text.setText(mContext.getString(mEmptyListText));
            if(mEmptyListTextAppearanceId != 0) {
                TypedArray appearance =
                        mContext.obtainStyledAttributes(mEmptyListTextAppearanceId, R.styleable.TextAppearance);
                int color = appearance.getColor(R.styleable.TextAppearance_android_textColor, -1);
                if(color != -1) {
                    text.setTextColor(color);
                }
                float size = appearance.getDimension(R.styleable.TextAppearance_android_textSize, 0.0f);
                // size returned by getDimension is in pixel unit
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
                appearance.recycle();
            }
        } else {
            text.setVisibility(View.GONE);
        }

        text = (TextView)emptyView.findViewById(R.id.empty_list_text_second);
        if(mEmptyListSecondText != 0) {
            text.setText(mContext.getString(mEmptyListSecondText));
            if(mEmptyListSecondTextAppearanceId != 0) {
                TypedArray appearance =
                        mContext.obtainStyledAttributes(mEmptyListSecondTextAppearanceId, R.styleable.TextAppearance);
                int color = appearance.getColor(R.styleable.TextAppearance_android_textColor, -1);
                if(color != -1) {
                    text.setTextColor(color);
                }
                float size = appearance.getDimension(R.styleable.TextAppearance_android_textSize, 0.0f);
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
                appearance.recycle();
            }
        } else {
            text.setVisibility(View.GONE);
        }

        // initialize empty view margins
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)emptyListHost.getLayoutParams();
        lp.setMargins(mEmptyListMarginLeft, mEmptyListMarginTop, mEmptyListMarginRight, mEmptyListMarginBottom);
        emptyListHost.setLayoutParams(lp);

        // must call addView directly
        ((ViewGroup)getParent()).addView(emptyView);
        setEmptyView(emptyView);
    }

    private class EmptyViewOnLayoutChangeListener implements View.OnLayoutChangeListener {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, 
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if(left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) {
                // not changed, ignore
                return;
            }

            if(v.getId() == R.id.empty_list_host_1) {
                // top layout is changed
                int totalHeight = bottom - top;

                View emptyListHost = ((ViewGroup)v).getChildAt(0);
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)emptyListHost.getLayoutParams();
                if(mEmptyListMaxHeight == 0) {
                    mEmptyListMaxHeight = v.getHeight() - lp.topMargin - lp.bottomMargin;
                }

                if(mEmptyListMarginTop + mEmptyListMaxHeight + mEmptyListMinMargin <= totalHeight) {
                    lp.topMargin = mEmptyListMarginTop;
                    lp.bottomMargin = totalHeight - lp.topMargin - mEmptyListMaxHeight;
                    emptyListHost.setLayoutParams(lp);
                    // v.requestLayout();
                    // cannot call requestLayout inside onLayoutChange, call it asychronously
                    mHandler.obtainMessage(0, v).sendToTarget();
                } else if(mEmptyListMarginTop + mEmptyListMinHeight + mEmptyListMinMargin <= totalHeight) {
                    // do nothing, the framework height will be shrink automatically
                } else {
                    lp.bottomMargin = mEmptyListMinMargin;
                    lp.topMargin = totalHeight - mEmptyListMinHeight - mEmptyListMinMargin;
                    emptyListHost.setLayoutParams(lp);
                    // v.requestLayout();
                    mHandler.obtainMessage(0, v).sendToTarget();
                }
            } else {
                // bottom layout is changed, it is called before top layout change
                // calculte the min height here
                if(mEmptyListMinHeight == 0) {
                    View parent = (View)v.getParent();
                    // min height should include parent's vertical margin
                    mEmptyListMinHeight = v.getHeight() + parent.getPaddingTop() + parent.getPaddingBottom();
                }
            }
        }
    }

    // message handler to handle requestLayout
    Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            ((View)msg.obj).requestLayout();
        }
    };

    private static int dpToPixel(int dp) {
        return Math.round(sPixelDensity * dp);
    }

    //tws-end::add an empty view to the listview's parent 2013-06-03

    //tws-start::add selected/checked/activated state background support 2013-06-13
    public void setSelectedStateEnabled(boolean enable) {
        mSelectedStateEnabled = enable;
    }

    public boolean getSelectedStateEnabled() {
        return mSelectedStateEnabled;
    }
    public static interface OnScrollStateListener{
        public static final int EDGE = 0;
        public static final int DOWN = 1;
        public static final int UP = 2;
        void onChange(int state);
    }
    //tws-end add listview bounce feature::2014-08-12

    // tws-start add listview blur feature::2014-10-05
    private Bitmap topBitmap, bottomBitmap;

    @Override
    public void draw(Canvas canvas) {
        mEnableBlur = false;
        if (mEnableBlur) {
            if (mBlurBottomHeight > 0 || mBlurTopHeight > 0) {

                if (mBlurBottomHeight > 0 && mForBottomCanvas != null) {
                    mForBottomCanvas.save();
                    mForBottomCanvas.translate(0, -mBlurForBottomRect.top);
                    if (mBlurBottomBgBitmap != null && !mBlurBottomBgBitmap.isRecycled()) {
                        mForBottomCanvas
                                .drawBitmap(mBlurBottomBgBitmap, null, mBlurForBottomRect, mBlurPaint);
                    } else {
                        mForBottomCanvas.drawColor(mContext.getResources().getColor(
                                R.color.tws_windowbackground_color));
                    }
                    super.draw(mForBottomCanvas);
                    mForBottomCanvas.restore();
                }

                if (mBlurTopHeight > 0 && mForTopCanvas != null) {
                    mForTopCanvas.save();
                    if (mBlurTopBgBitmap != null && !mBlurTopBgBitmap.isRecycled()) {
                        mForTopCanvas.drawBitmap(mBlurTopBgBitmap, null, mBlurForTopRect, mBlurPaint);
                    } else {
                        mForTopCanvas.drawColor(mContext.getResources().getColor(
                                R.color.tws_windowbackground_color));
                    }
                    super.draw(mForTopCanvas);
                    mForTopCanvas.restore();
                }

                canvas.save();
                if (mForContentRect != null) {
                    canvas.clipRect(mForContentRect);
                }
                super.draw(canvas);
                canvas.restore();

                if (mBlurBottomHeight > 0 && mForBottomBitmap != null && !mForBottomBitmap.isRecycled()) {
                    if (NativeBlurProcess.noBlurSo) {
                        bottomBitmap = mForBottomBitmap;
                    } else {
//					long start = System.nanoTime();
                        bottomBitmap = mBlur.blur(mForBottomBitmap, true);
//					long delta = System.nanoTime() - start;
//			        Log.v("blur", "mBlur.blur bottom take " + delta/1e6f + " ms");
                    }
                    if (bottomBitmap != null && !bottomBitmap.isRecycled()) {
                        canvas.drawBitmap(bottomBitmap, null, mBlurForBottomRect, mBlurPaint);
                    }
//				mForBottomCanvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
                }
                if (mBlurTopHeight > 0 && mForTopBitmap != null && !mForTopBitmap.isRecycled()) {
                    if (NativeBlurProcess.noBlurSo) {
                        topBitmap = mForTopBitmap;
                    } else {
//					long start = System.nanoTime();
                        topBitmap = mBlur.blur(mForTopBitmap, true);
//					long delta = System.nanoTime() - start;
//			        Log.v("blur", "mBlur.blur top take " + delta/1e6f + " ms");	
                    }
                    if (topBitmap != null && !topBitmap.isRecycled()) {
                        canvas.drawBitmap(topBitmap, null, mBlurForTopRect, mBlurPaint);
                    }
//				mForTopCanvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
                }
            } else {
                super.draw(canvas);
            }
        } else {
            super.draw(canvas);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mBlurRecycleFlag) {
            blurRecycle();
        }
    }

    private void blurSetup() {

        if (mBlurBottomHeight > 0) {
            mBottomBlurView = new View(mContext);
            LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mBlurBottomHeight);
            mBottomBlurView.setLayoutParams(params);
            if (mBlurBottomDrawable != null) {
                if (android.os.Build.VERSION.SDK_INT > 15) {
                    mBottomBlurView.setBackground(mBlurBottomDrawable);
                } else {
                    mBottomBlurView.setBackgroundDrawable(mBlurBottomDrawable);
                }
            }
            addFooterView(mBottomBlurView, null, false);
        }

        if (mBlurTopHeight > 0) {
            if (mTopBlurView != null)
                return;
            mTopBlurView = new View(mContext);
            LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mBlurTopHeight);
            mTopBlurView.setLayoutParams(params);
            if (mBlurTopDrawable != null) {
                if (android.os.Build.VERSION.SDK_INT > 15) {
                    mTopBlurView.setBackground(mBlurTopDrawable);
                } else {
                    mTopBlurView.setBackgroundDrawable(mBlurTopDrawable);
                }
            }
            addHeaderView(mTopBlurView, null, false);
        }
    }

    private void blurInit() {
        if (mBlurBottomHeight > 0 || mBlurTopHeight > 0) {
            if (mBlurPaint == null) {
                mBlurPaint = new Paint();
                mBlurPaint.setAntiAlias(true);
            }

            if (mBlurForTopRect == null) {
                mBlurForTopRect = new Rect();
            }
            mBlurForTopRect.left = 0;
            mBlurForTopRect.right = getWidth();
            mBlurForTopRect.top = 0;
            mBlurForTopRect.bottom = mBlurTopHeight;

            if (mBlurForBottomRect == null) {
                mBlurForBottomRect = new Rect();
            }
            mBlurForBottomRect.left = 0;
            mBlurForBottomRect.right = getWidth();
            mBlurForBottomRect.bottom = getHeight();
            mBlurForBottomRect.top = mBlurForBottomRect.bottom - mBlurBottomHeight;

            if (mForContentRect == null) {
                mForContentRect = new Rect();
            }
            mForContentRect.left = 0;
            mForContentRect.right = getWidth();
            mForContentRect.bottom = mBlurForBottomRect.top;
            mForContentRect.top = mBlurForTopRect.bottom;

            blurRecycle();

            if (mBlurBottomHeight > 0) {
                int blurRectW = mBlurForBottomRect.right - mBlurForBottomRect.left;
                int blurRectH = mBlurBottomHeight;
                if (NativeBlurProcess.noBlurSo) {
                    mForBottomBitmap = Bitmap.createBitmap(blurRectW > 0 ? blurRectW : 1,
                            blurRectH > 0 ? blurRectH : 1, Config.ARGB_8888);
                    mForBottomCanvas = new Canvas(mForBottomBitmap);
                } else {
                    mForBottomBitmap = Bitmap.createBitmap((blurRectW / 10) > 0 ? (blurRectW / 10) : 1,
                            (blurRectH / 10) > 0 ? (blurRectH / 10) : 1, Config.ARGB_8888);
                    mForBottomCanvas = new Canvas(mForBottomBitmap);
                    mForBottomCanvas.scale(0.1f, 0.1f);
                }
            }

            if (mBlurTopHeight > 0) {
                int blurRectW = mBlurForTopRect.right - mBlurForTopRect.left;
                int blurRectH = mBlurTopHeight;
                if (NativeBlurProcess.noBlurSo) {
                    mForTopBitmap = Bitmap.createBitmap(blurRectW > 0 ? blurRectW : 1,
                            blurRectH > 0 ? blurRectH : 1, Config.ARGB_8888);
                    mForTopCanvas = new Canvas(mForTopBitmap);
                } else {
                    mForTopBitmap = Bitmap.createBitmap((blurRectW / 10) > 0 ? (blurRectW / 10) : 1,
                            (blurRectH / 10) > 0 ? (blurRectH / 10) : 1, Config.ARGB_8888);
                    mForTopCanvas = new Canvas(mForTopBitmap);
                    mForTopCanvas.scale(0.1f, 0.1f);
                }
            }

            if (mBlur == null) {
                mBlur = new JNIBlur(mContext);
            }

            // tws-start recalculate scroll params::2015-1-9
//			try {
//				Class clazz = Class.forName("android.view.View");
//				Method method = clazz.getMethod("twsSetParameters", int.class, int.class);
//				method.invoke(this, mBlurTopHeight, mBlurBottomHeight);
//			} catch (Exception e) {
//				Log.v("scrollbar", "twsSetParameters not found");
//			}
            twsSetParameters(mBlurTopHeight, mBlurBottomHeight);
            // tws-end recalculate scroll params::2015-1-9
        } else {
            // tws-start recalculate scroll params::2015-1-9
//			try {
//				Class clazz = Class.forName("android.view.View");
//				Method method = clazz.getMethod("twsSetParameters", int.class, int.class);
//				method.invoke(this, 0, 0);
//			} catch (Exception e) {
//				Log.v("scrollbar", "twsSetParameters not found");
//			}
        	twsSetParameters(0, 0);
            // tws-end recalculate scroll params::2015-1-9
        }
    }

    public void blurRecycle() {

        if (mForBottomBitmap != null && !mForBottomBitmap.isRecycled()) {
            mForBottomBitmap.recycle();
        }

        if (mForTopBitmap != null && !mForTopBitmap.isRecycled()) {
            mForTopBitmap.recycle();
        }
    }

    private Bitmap blurScale(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.postScale(0.2f, 0.2f);
        Bitmap resizeBmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix,
                true);
        return resizeBmp;
    }

    public boolean setFooterBlank(boolean flag) {
        if (flag)
            this.mBlurBottomHeight = (int) mContext.getResources().getDimension(
                    R.dimen.tws_actionbar_split_height);
        else
            this.mBlurBottomHeight = 0;
        return flag;

    }

    public boolean setHeaderBlank(boolean flag) {
        if (flag)
            this.mBlurTopHeight = (int) mContext.getResources().getDimension(
                    R.dimen.tws_action_bar_height);
        else
            this.mBlurTopHeight = 0;
        return flag;
    }

    // use to show under actionbar blur in height this.mBlurTopHeight
	public boolean setHeaderBlankWithStatusbar(boolean flag) {
		if (flag)
			this.mBlurTopHeight = (int) mContext.getResources().getDimension(R.dimen.tws_action_bar_height) - (int) mContext.getResources().getDimension(R.dimen.actionbar_shadow_padding);
		else
			this.mBlurTopHeight = 0;
		
		return flag;
	}

    public boolean setHeaderBlankWithStatusbar(boolean flag, int height) {
        if (flag)
            this.mBlurTopHeight = height;
        else
            this.mBlurTopHeight = 0;

        twsSetParameters(mBlurTopHeight, mBlurBottomHeight);
        // tws-end recalculate scroll params::2015-1-9
        return flag;
    }

    // tws-end set ActionBar Margin::2014-10-15

    public boolean setFirstItemHigher(boolean flag) {
        return flag;
    }

    @Deprecated
    public boolean setLastItemHigher(boolean flag) {
        return flag;
    }

    public boolean enableTopBlur(boolean flag) {
        if (flag) {
            setHeaderBlankWithStatusbar(false);
            if (mBlurTopHeight > 0) {
                mTopBlurView = new View(mContext);
                LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mBlurTopHeight);
                mTopBlurView.setLayoutParams(params);
                if (mBlurTopDrawable != null) {
                    if (android.os.Build.VERSION.SDK_INT > 15) {
                        mTopBlurView.setBackground(mBlurTopDrawable);
                    } else {
                        mTopBlurView.setBackgroundDrawable(mBlurTopDrawable);
                    }
                }
                addHeaderView(mTopBlurView, null, false);
            }
            blurInit();
        } else {
            setHeaderBlankWithStatusbar(false);
            blurInit();
            if (mTopBlurView != null) {
                removeHeaderView(mTopBlurView);
            }
        }
        return flag;
    }

    public boolean enableBottomBlur(boolean flag) {
        if (flag) {
            setFooterBlank(false);
            if (mBlurBottomHeight > 0) {
                mBottomBlurView = new View(mContext);
                LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mBlurBottomHeight);
                mBottomBlurView.setLayoutParams(params);
                if (mBlurBottomDrawable != null) {
                    if (android.os.Build.VERSION.SDK_INT > 15) {
                        mBottomBlurView.setBackground(mBlurBottomDrawable);
                    } else {
                        mBottomBlurView.setBackgroundDrawable(mBlurBottomDrawable);
                    }
                }
                addFooterView(mBottomBlurView, null, false);
            }
            blurInit();
        } else {
            setFooterBlank(false);
            blurInit();
            if (mBottomBlurView != null) {
                removeFooterView(mBottomBlurView);
            }
        }
        return flag;
    }

    public void changeTopBlurDrawable(Drawable drawable) {
        this.mBlurTopDrawable = drawable;
    }

    public void changeBottomBlurDrawable(Drawable drawable) {
        this.mBlurBottomDrawable = drawable;
    }

    // tws-end add listview blur feature::2014-10-05

    // tws-start for item animation::2014-11-11
    public void removeViewWithAnimator(final View view, final int position) {
        if (view == null || mRemovedPositions.contains(position)) {
            return;
        }

        mRemovedViews.add(view);
        mRemovedPositions.add(position);
        mActiveRemoveCount++;
        ValueAnimator animator = ValueAnimator.ofInt(view.getHeight(), 1).setDuration(ANIMATION_DURATION);
        animator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                layoutParams.height = (Integer) animation.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }
        });
        animator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator arg0) {
            }

            @Override
            public void onAnimationRepeat(Animator arg0) {
            }

            @Override
            public void onAnimationEnd(Animator arg0) {
                finalizeRemove();
            }

            @Override
            public void onAnimationCancel(Animator arg0) {
            }
        });
        animator.start();
    }

    private void finalizeRemove() {
        mActiveRemoveCount--;
        if (mActiveRemoveCount == 0) {
            for (View view : mRemovedViews) {
                view.setAlpha(1.0f);
                view.setTranslationX(0);
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                layoutParams.height = 0;
                view.setLayoutParams(layoutParams);
            }

            if (!mRemovedPositions.isEmpty()) {
                Collections.sort(mRemovedPositions, Collections.reverseOrder());
                int[] dismissPositions = new int[mRemovedPositions.size()];
                int i = 0;
                for (Integer dismissedPosition : mRemovedPositions) {
                    dismissPositions[i] = dismissedPosition;
                    i++;
                }

                if (mOnItemRemoveListener != null) {
                    mOnItemRemoveListener.onItemRemove(dismissPositions);
                }
            }

            mRemovedViews.clear();
            mRemovedPositions.clear();
        }
    }

    public void setOnItemRemoveListener(TwsOnItemRemoveListener onItemRemoveListener) {
        mOnItemRemoveListener = onItemRemoveListener;
    }

    public interface TwsOnItemRemoveListener {
        public void onItemRemove(int[] positions);
    }

    // tws-end for item animation::2014-11-11

    //tws-start for item animation::2014-11-11
    /**
     * @param lastPosition
     * @param position 
     * @param positionOffset
     * @param index
     */
    public void twsUpdateItemViewTranslation(int lastPosition, int position, float positionOffset, int index) {
        int animationPosition = lastPosition > position ? position : position + 1;
        View child;
        if (animationPosition == index) {
            float fraction = animationPosition - position - positionOffset;
            if (Math.abs(fraction) > 1.0f || getWidth() <= 0 || getChildCount() == 0) {
                return;
            }

            final int minChildCount = getChildCount();

            final int bottomCount = mBlurBottomHeight == 0 ? 0 : 1;
            for (int i = 0; i < getChildCount(); i++) {
                float maxTranslationX = (i + 1 + bottomCount) / (float) minChildCount * getWidth();
                child = getChildAt(i);
                float translationX = maxTranslationX - (1 - Math.abs(fraction)) * getWidth();
                translationX = Math.max(0, translationX);

                child.setTranslationX(fraction > 0 ? translationX : -translationX);
            }
        } else {
            for (int i = 0; i < getChildCount(); i++) {
                child = getChildAt(i);
                child.setTranslationX(0);
            }
        }
    }
    //tws-end for item animation::2014-11-11
    public void setBlurBottomBgBitmap(Bitmap bitmap) {
        mBlurBottomBgBitmap = bitmap;
    }

    public Bitmap getBlurBottomBgBitmap() {
        return mBlurBottomBgBitmap;
    }

    public void setBlurTopBgBitmap(Bitmap bitmap) {
        mBlurTopBgBitmap = bitmap;
    }

    public Bitmap getBlurTopBgBitmap() {
        return mBlurTopBgBitmap;
    }

    public int getBlurTopHeight() {
        return mBlurTopHeight;
    }

    public int getBlurBottomHeight() {
        return mBlurBottomHeight;
    }

    public void setEnableBlur(boolean enable) {
        this.mEnableBlur = enable;
    }

    // tws-start for framework xposed rebuild::2015-05-22
    private void twsInitTwsScrollBarDrawable(boolean initialize) {
        try {
            Class<?> viewClz = Class.forName("android.view.View");
            Field scrollCacheField = viewClz.getDeclaredField("mScrollCache");
            scrollCacheField.setAccessible(true);
            Object scrollCache = scrollCacheField.get(this);
            Class<?> scrollCacheClz = scrollCacheField.getType();

            if (initialize && scrollCache == null) {
                String methodName = "initScrollCache";
                Method method = viewClz.getDeclaredMethod(methodName);
                method.setAccessible(true);
                method.invoke(this);

                scrollCache = scrollCacheField.get(this);
            }

            if (scrollCache != null) {
                Field scrollBarField = scrollCacheClz.getDeclaredField("scrollBar");
                scrollBarField.setAccessible(true);
                Object scrollBar = scrollBarField.get(scrollCache);
                if (scrollBar == null) {
                    scrollBarField.set(scrollCache, new TwsScrollBarDrawable());
                }
            }
        } catch (Exception e) {
            Log.e("tws.widget.ListView", "twsInitTwsScrollBarDrawable|exp:" + e.getMessage());
        }
    }

    @Override
    protected void initializeScrollbars(TypedArray a) {
        Log.d("tws.widget.ListView", "initializeScrollbars");

        twsInitTwsScrollBarDrawable(true);

        super.initializeScrollbars(a);
    }

    @Override
    protected boolean awakenScrollBars(int startDelay, boolean invalidate) {
        twsInitTwsScrollBarDrawable(false);

        return super.awakenScrollBars(startDelay, invalidate);
    }

    private void twsSetParameters(int start, int end) {
        try {
            Log.d("tws.widget.ListView", "twsSetParameters|start=" + start + ",end=" + end);

            Class<?> viewClz = Class.forName("android.view.View");
            Field scrollCacheField = viewClz.getDeclaredField("mScrollCache");
            scrollCacheField.setAccessible(true);
            Object scrollCache = scrollCacheField.get(this);
            Class<?> scrollCacheClz = scrollCacheField.getType();

            if (scrollCache != null) {
                Field scrollBarField = scrollCacheClz.getDeclaredField("scrollBar");
                scrollBarField.setAccessible(true);
                Object scrollBar = scrollBarField.get(scrollCache);
                if (scrollBar != null) {
                    if (scrollBar instanceof TwsScrollBarDrawable) {
                        TwsScrollBarDrawable drawable = (TwsScrollBarDrawable) scrollBar;
                        drawable.twsSetParameters(getHeight(), start, end);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("tws.widget.ListView", "twsSetParameters exp:" + e.getMessage());
        }
    }
    // tws-end for framework xposed rebuild::2015-05-22
}