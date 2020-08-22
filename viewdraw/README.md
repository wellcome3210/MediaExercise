# 使用View绘制图片练习
## 1. 使用ImageView

    简单为ImageView设置Bitmap即可

## 2. 使用SurfaceView

SurfaceView使用独立于窗口的Surface用于绘制，通过SurfaceHolder操作该Surface

但由于使用独立于窗口的Surface其初始化完成的时机并非onCreate，需要设置Callback，在surfaceCreated实现想要的操作
        
    SurfaceHolder surfaceHolder = surfaceView.getHolder();
    surfaceHolder.addCallback(new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            // 获取buffer
            Canvas canvas = holder.lockCanvas();
            // 绘制动作
            canvas.drawBitmap(......)
            // 提交buffer
            holder.unlockCanvasAndPost(canvas);
        }
    }

## 3. 使用自定义View进行绘制
之前没有绘制自定义View经验，这里稍微绕了点远路

    // 关注几个绘制流程的函数
    // View测量完成被调用，这里可以获取最新的测量尺寸
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    // View布局完成被调用，这里可以获得最新的布局位置
    protected void onLayout(boolean changed, int left, int top, int right, int bottom)
    // View内容绘制的回调，这里需要填充内部的绘制逻辑，这里的canvas由SurfaceHolder传递
    protected void onDraw(Canvas canvas)
    // 参考SurfaceHolder.lockCanvas(Rect dirty)文档
    // 此处的canvas为布局后得到的局部canvas，不需要再考虑相对坐标的问题
    /**
     * Just like {@link #lockCanvas()} but allows specification of a dirty rectangle.
     * Every
     * pixel within that rectangle must be written; however pixels outside
     * the dirty rectangle will be preserved by the next call to lockCanvas().
     *
     * @see android.view.SurfaceHolder#lockCanvas
     *
     * @param dirty Area of the Surface that will be modified.
     * @return Canvas Use to draw into the surface.
     */
    public Canvas lockCanvas(Rect dirty);
        
# 总结 
简单绘制一张图片，至少触及几个知识点
1. 理解SurfaceView与其他View的差异
2. 掌握View绘制流程
3. 底层显示buffer的渲染流程

涉及到视频开发，越过常规View的API进行绘图是基本的操作