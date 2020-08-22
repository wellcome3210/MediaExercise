package com.wellcome.mediaexercise;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class MyImageView extends View {
    Bitmap mBitmap;
    Paint mPaint;
    Rect srcRect;
    Rect toRect;
    public MyImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
        if(srcRect == null){
            srcRect = new Rect();
        }
        srcRect.bottom = bitmap.getHeight();
        srcRect.right = bitmap.getWidth();
        if(mPaint == null) {
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.FILL);
        }
        postInvalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if(toRect == null){
            toRect = new Rect();
        }
        toRect.left = 0;
        toRect.top = 0;
        toRect.right = getMeasuredWidth();
        toRect.bottom = getMeasuredHeight();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    // alike to call SurfaceHolder{public Canvas lockCanvas(Rect dirty);} to get an specific rect of original canvas
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(mBitmap == null) return;
        Log.d("MyImageView", "onDraw: \nwidth: " + getWidth() + "\nheight: " + getHeight());
        canvas.drawBitmap(mBitmap, srcRect, toRect, mPaint);
    }
}
