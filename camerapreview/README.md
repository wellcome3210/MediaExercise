# Camera预览
尝试使用两种Camera API分别在SurfaceView和TextureView上预览，并在预览Callback获取到NV21格式的帧数据
## 1 权限与特性
    <!--声明申请摄像头权限-->
    <uses-permission android:name="android.permission.CAMERA"/>
    <!--声明使用相机特性-->
    <uses-feature android:name="android.hardware.camera"/>
    
## 2 Camera与Camera2
Android 5.0（API级别21）引入Camera2 API，Camera API随之被标记为废弃

在API的框架上
* Camera API，Camera类作为硬件相机的代理，调用过程是同步的
* Camera2 API，客户进程和服务进程各持有对方的Binder代理，互相调用，类似使用管道跨进程通信的两个文件描述符，一端用于写入，一端用于读取；在代码实现上，客户进程接收服务进程的调用结果表现在Callback上
    1. 支持在非UI线程获取预览数据
    2. 可以获取更多的预览帧
    3. 对相机的控制更加完备
    4. 支持更多格式的预览数据
    5. 支持高速连拍

![image](https://github.com/wellcome3210/MediaExercise/image/camerapreview/camera_architecture.jpg)

更多信息参考[AOSP-开发-相机](https://source.android.com/devices/camera)

功能上，Camera2提供了更加丰富的相机配置接口，与之相对的，编程的复杂度更高

---
### 2.1 Camera2 API使用
实现参考[Google Camera2预览示例](https://github.com/googlearchive/android-Camera2Basic)

1. 获取CameraManager camera服务
2. 获取cameraId列表，检查CameraCharacteristics，选取需要使用的cameraId
3. 根据cameraId打开相机，并在Callback获取相机的描述实例CameraDevice
4. 为该相机创建CameraCaptureSession
5. 构造一个CaptureRequest请求，并提供输出缓冲Surface(可以设置多个，输出到多个)
6. 通过CameraCaptureSession发送CaptureRequest请求，开始预览
7. 帧数据到达客户进程触发Callback

简单类图示意

![image](https://github.com/wellcome3210/MediaExercise/image/camerapreview/camera2.png)

---
### 2.2 Camera API使用
1. 选择需要的摄像头 
2. 打开对应的Camera
3. 设置预览Surface
4. 设置预览方向，适配的预览尺寸，以及其他预览参数
5. 设置预览Callback
6. 开始预览

#### 2.2.1 预览方向
Camera2 API自动调整了预览方向，让我们感觉是没有问题的

关于预览画面方向，需要了解关于设备定于的几个方向，参考[Android Camera2 教程 · 第三章 · 预览](https://www.jianshu.com/p/067889611ae7)

* 设备方向，定义设备某个朝向为0°，例如手机，屏幕朝上垂直方向为0°，又称之为自然方向；设备方向是硬件设备在空间中的方向与其自然方向的顺时针夹角
* 设备局部坐标系，不变的参考坐标系
    * x 轴是当手机处于自然方向时，和手机屏幕平行且指向右边的坐标轴。
    * y 轴是当手机处于自然方向时，和手机屏幕平行且指向上方的坐标轴。
    * z 轴是当手机处于自然方向时，和手机屏幕垂直且指向屏幕外面的坐标轴。
* 显示方向，屏幕从下到上方向，由顺时针方向与y轴的夹角
* 摄像头传感器方向，指的是传感器采集到的画面方向，经过顺时针旋转该度数后才能和局部坐标系的 y 轴正方向一致

其中摄像头方向也会影响预览宽高与屏幕宽高的对应关系，预览Surface的宽高需要调整到与摄像头预览宽高对应

预览显示方向实际调整方法，可以参考API示例

    public static void setCameraDisplayOrientation(Activity activity,
            int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

#### 2.2.2 预览尺寸
本地测试设备摄像头预览方向为90°，宽大于高，与我设置的预览Surface刚好不同，需要交换宽高对应起来

另外Camera API需要手动从支持的预览尺寸里选择一组宽高，设置到Parameters，获取方法如下

    Camera.Parameter parameters = camera.getParameters();
    // 获取支持的预览尺寸
    parameters.getSupportedPreviewSizes()

## 3 预览
使用两种View预览的差异

| |SurfaceView|TextureView|
|---|---|---|
|内存|低|高|
|绘制|及时|1-3帧的延迟|
|性能|高|低|
|动画和截图|不支持|支持|

### 3.1 SurfaceView预览
拥有独立的Window，对应独立的Surface
### 3.2 TextureView预览
共享View层次结构的Window，因此也共享Surface，就像一个普通的View，可以进行动画操作；但必须在硬件加速的Window中使用

拥有一个SurfaceTexture，在示例中使用该SurfaceTexture构造Surface，
涉及到纹理应该与GL相关，回头再研究

    SurfaceTexture texture = mTextureView.getSurfaceTexture();
    // This is the output Surface we need to start preview.
    Surface surface = new Surface(texture);

### 3.3 ImageReader
作为拍照时的输出Surface，该Surface可由直接访问应用直接访问，用作获取预览帧的Surface

API说明大致如下：
在应用端，ImageReader作为一个生产者，内部维护一个Image对象池，由Surface上到达的数据，会以Image的形式被加入就绪的资源队列，通过调用acquireLatestImage()或acquireNextImage()方法获取Image对象；处理完成后需要调用release()方法，将对象还给对象池复用。使用时，如果消费速度跟不上生产速度，那么可能使Image内的数据过期。

    The ImageReader class allows direct application access to image data rendered into a Surface
    
    Several Android media API classes accept Surface objects as targets to render to, 
    including MediaPlayer, MediaCodec, CameraDevice, ImageWriter and RenderScript Allocations. 
    The image sizes and formats that can be used with each source vary, 
    and should be checked in the documentation for the specific API.
    
    The image data is encapsulated in Image objects, and multiple such objects 
    can be accessed at the same time, up to the number specified by the 
    maxImages constructor parameter. New images sent to an ImageReader 
    through its Surface are queued until accessed through the 
    acquireLatestImage() or acquireNextImage() call. Due to memory limits, 
    an image source will eventually stall or drop Images in trying to render 
    to the Surface if the ImageReader does not obtain and release Images 
    at a rate equal to the production rate.

### 3.4 预览数据提取
两种API默认格式都是YUV420比例采样的一种表示方法，差别在于平面划分、UV排布顺序，详见[YUV色彩空间](#4-yuv色彩空间)
#### 3.4.1 Camera默认格式NV21(YCbCr_420_SP)
可以理解为在byte数组中，连续存储两个平面，第一个平面全部存储Y，第二个平面VU交错存储

#### 3.4.2 Camera2默认格式YUV_420_888
U、V平面，实际上都包含uv分量，两个平面错位存储，依照pixelStride取用对应分量是没有问题的，UV实际pixelStride为2

    YUV_420_888转NV21
    data = new byte[y.length + (u.length + 1) / 2 + (v.length + 1) / 2];
    System.arraycopy(y, 0, data, 0, y.length);
    int position = y.length;
    for(int i = 0; i < u.length; i += 2){
        data[position++] = v[i];
        data[position++] = u[i];
    }

## 4 YUV色彩空间
补充色彩编码方法知识，参考[YUV wiki](https://zh.wikipedia.org/wiki/YUV)

RGB使用红绿蓝三色分量描述，与相对的，YUV使用Y(明亮度，Luminance)描述灰阶值、UV(色度、Chrominance)描述像素的颜色

### 4.1 分量理解
参考[如何理解 YUV ？](https://zhuanlan.zhihu.com/p/85620611)
一组YUV最多需要描述8个像素

X\:Y\:Z描述对4*2矩阵的采样

X:Y指第一行，Y与UV采样比
X:Z指第二行，Y与UV采样比

![image](https://github.com/wellcome3210/MediaExercise/image/camerapreview/YUV.jpg)

### 4.2 存储格式

* 紧缩格式（packedformats）：将Y、U、V值存储成MacroPixels数组，每个分量交替出现，和RGB的存放方式类似。

* 平面格式（planarformats）：将Y、U、V的三个分量分别存放在不同的平面(数组)中。

* 半平面格式：一个平面用于Y，一个平面用于UV。

### 4.3 常见格式
产生不同YUV格式的原因
* 分量比例
* UV排列顺序
* 字母出现的顺序，通常表示平面顺序；数字通常表示像素位深度(一组YUV字节数)

YUV4:4:4采样，每一个Y对应一组UV分量；YUV4:2:2采样，每两个Y共用一组UV分量；YUV4:2:0采样，每四个Y共用一组UV分量。

YV12、IYUV和I420，平面格式，UV分开放的，只是YV12则是V在U前，而IYUV和I420一样，U在V前

NV12和NV21，半平面格式，UV交叉放的，NV12则是UVUV，而NV21则是VUVU
