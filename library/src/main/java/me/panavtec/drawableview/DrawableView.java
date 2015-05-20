package me.panavtec.drawableview;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.util.ArrayList;
import me.panavtec.drawableview.draw.CanvasDrawer;
import me.panavtec.drawableview.draw.PathDrawer;
import me.panavtec.drawableview.draw.SerializablePath;
import me.panavtec.drawableview.gestures.creator.GestureCreator;
import me.panavtec.drawableview.gestures.creator.GestureCreatorListener;
import me.panavtec.drawableview.gestures.scale.GestureScaleListener;
import me.panavtec.drawableview.gestures.scale.GestureScaler;
import me.panavtec.drawableview.gestures.scale.ScalerListener;
import me.panavtec.drawableview.gestures.scroller.GestureScrollListener;
import me.panavtec.drawableview.gestures.scroller.GestureScroller;
import me.panavtec.drawableview.gestures.scroller.ScrollerListener;

public class DrawableView extends View
    implements View.OnTouchListener, ScrollerListener, GestureCreatorListener, ScalerListener {

  private final ArrayList<SerializablePath> paths = new ArrayList<>();

  private GestureScroller gestureScroller;
  private GestureScaler gestureScaler;
  private GestureCreator gestureCreator;
  private int canvasHeight;
  private int canvasWidth;
  private GestureDetector gestureDetector;
  private ScaleGestureDetector scaleGestureDetector;
  private PathDrawer pathDrawer;
  private CanvasDrawer canvasDrawer;
  private SerializablePath currentDrawingPath;

  public DrawableView(Context context) {
    super(context);
    init();
  }

  public DrawableView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
    setAttributeSet(context, attrs);
  }

  public DrawableView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
    setAttributeSet(context, attrs);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP) public DrawableView(Context context, AttributeSet attrs,
      int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init();
    setAttributeSet(context, attrs);
  }

  private void init() {
    gestureScroller = new GestureScroller(this);
    gestureDetector = new GestureDetector(getContext(), new GestureScrollListener(gestureScroller));
    gestureScaler = new GestureScaler(this);
    scaleGestureDetector =
        new ScaleGestureDetector(getContext(), new GestureScaleListener(gestureScaler));
    gestureCreator = new GestureCreator(this);
    pathDrawer = new PathDrawer();
    canvasDrawer = new CanvasDrawer();
    setOnTouchListener(this);
  }

  private void setAttributeSet(Context context, AttributeSet attributeSet){

    TypedArray a = context.getTheme().obtainStyledAttributes(
            attributeSet,
            R.styleable.DrawableView,
            0, 0);

    try {
      DisplayMetrics metrics = context.getResources().getDisplayMetrics();
      int windowWidth = metrics.widthPixels;
      int windowHeight = metrics.heightPixels;

      DrawableViewConfig config = new DrawableViewConfig();
      config.setCanvasHeight(a.getDimensionPixelSize(R.styleable.DrawableView_canvasHeight, windowHeight));
      config.setCanvasWidth(a.getDimensionPixelSize(R.styleable.DrawableView_canvasWidth, windowWidth));
      config.setStrokeColor(a.getColor(R.styleable.DrawableView_strokeColor, context.getResources().getColor(android.R.color.black)));
      config.setShowCanvasBounds(a.getBoolean(R.styleable.DrawableView_showCanvasBounds, true));
      config.setMinZoom(a.getFloat(R.styleable.DrawableView_minZoom, 1.0f));
      config.setMaxZoom(a.getFloat(R.styleable.DrawableView_maxZoom, 3.0f));
      config.setStrokeWidth(a.getFloat(R.styleable.DrawableView_strokeWidth, 20f));
      setConfig(config);

    } finally {
      a.recycle();
    }
  }

  public void setConfig(DrawableViewConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("Paint configuration cannot be null");
    }
    canvasWidth = config.getCanvasWidth();
    canvasHeight = config.getCanvasHeight();
    gestureCreator.setConfig(config);
    gestureScaler.setZooms(config.getMinZoom(), config.getMaxZoom());
    gestureScroller.setCanvasBounds(canvasWidth, canvasHeight);
    canvasDrawer.setConfig(config);
  }

  @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    gestureScroller.setViewBounds(w, h);
  }

  @Override public boolean onTouch(View v, MotionEvent event) {
    scaleGestureDetector.onTouchEvent(event);
    gestureDetector.onTouchEvent(event);
    gestureCreator.onTouchEvent(event);
    invalidate();
    return true;
  }

  public void undo() {
    if (paths.size() > 0) {
      paths.remove(paths.size() - 1);
      invalidate();
    }
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvasDrawer.onDraw(canvas);
    pathDrawer.onDraw(canvas, currentDrawingPath, paths);
  }

  public void clear() {
    paths.clear();
    invalidate();
  }

  public Bitmap obtainBitmap(Bitmap createdBitmap) {
    return pathDrawer.obtainBitmap(createdBitmap, paths);
  }

  public Bitmap obtainBitmap() {
    return obtainBitmap(Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888));
  }

  @Override protected Parcelable onSaveInstanceState() {
    DrawableViewSaveState state = new DrawableViewSaveState(super.onSaveInstanceState());
    state.setPaths(paths);
    return state;
  }

  @Override protected void onRestoreInstanceState(Parcelable state) {
    if (!(state instanceof DrawableViewSaveState)) {
      super.onRestoreInstanceState(state);
    } else {
      DrawableViewSaveState ss = (DrawableViewSaveState) state;
      super.onRestoreInstanceState(ss.getSuperState());
      paths.addAll(ss.getPaths());
    }
  }

  @Override public void onViewPortChange(RectF currentViewport) {
    gestureCreator.onViewPortChange(currentViewport);
    canvasDrawer.onViewPortChange(currentViewport);
  }

  @Override public void onCanvasChanged(RectF canvasRect) {
    gestureCreator.onCanvasChanged(canvasRect);
    canvasDrawer.onCanvasChanged(canvasRect);
  }

  @Override public void onGestureCreated(SerializablePath serializablePath) {
    paths.add(serializablePath);
  }

  @Override public void onCurrentGestureChanged(SerializablePath currentDrawingPath) {
    this.currentDrawingPath = currentDrawingPath;
  }

  @Override public void onScaleChange(float scaleFactor) {
    gestureScroller.onScaleChange(scaleFactor);
    gestureCreator.onScaleChange(scaleFactor);
    canvasDrawer.onScaleChange(scaleFactor);
  }
}
