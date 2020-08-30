package com.wellcome.camerapreview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import java.util.Arrays;
import java.util.List;

@SuppressLint("MissingPermission")
public class Camera2Helper extends CameraHelperBase{
    private static final String TAG = Camera2Helper.class.getSimpleName();
    private Context mContext;
    private Surface mPreviewSurface;

    private CameraManager mCameraManager;
    private Handler mWorkHandler;
    private String mCameraId;
    private CameraDevice mCameraDevice;

    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private ImageReader mImageReader;
    private CaptureRequest mPreviewRequest;

    private boolean mNeedPreview;


    public Camera2Helper(Context context, CameraListener cameraListener) {
        mContext = context;
        mCameraListener = cameraListener;
        mPreviewSize = new Point();
        init();
    }

    public void setPreviewSurface(Surface previewSurface) {
        mPreviewSurface = previewSurface;
    }

    public void setPreviewSize(Point previewSize) {
        mPreviewSize.x = previewSize.x;
        mPreviewSize.y = previewSize.y;
    }

    public boolean isPreviewing(){
        return mCaptureSession != null;
    }

    private void init(){
        if(mCameraManager == null) {
            mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        }
        if(mCameraManager == null){
            Log.e(TAG, "setupCamera: not support for camera2 api");
            return;
        }
        try {
            String[] list = mCameraManager.getCameraIdList();
            Log.i(TAG, "setupCamera: camera list: " + Arrays.toString(list));
            for (String cameraId : list) {
                CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    mCameraId = cameraId;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (mCameraId == null) {
            Log.e(TAG, "setupCamera: no available back camera");
            return;
        }
    }

    @Override
    public void stopPreview(){
        if(mCaptureSession != null){
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }

    @Override
    public void startPreview(){
        mNeedPreview = true;
        setupCamera();
    }

    @Override
    public void closeCamera() {
        if(mCaptureSession != null){
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if(mCameraDevice != null){
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    /**
     * Call after calling {@link #setPreviewSize(Point)} and {@link #setPreviewSurface(Surface)}
     */
    @Override
    public void setupCamera() {
        // 与相机服务通信，返回结果均在Callback，创建一个独立的HandlerThread来处理回调
        if(mWorkHandler == null) {
            HandlerThread handlerThread = new HandlerThread("camera");
            handlerThread.start();
            mWorkHandler = new Handler(handlerThread.getLooper());
        }
        if(mPreviewSurface == null){
            Log.w(TAG, "setupCamera: no surface for camera to preview");
            return;
        }
        if(mCameraDevice == null) {
            try {
                // 打开相机设备
                mCameraManager.openCamera(mCameraId, mCameraOpenCallback, mWorkHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }else if(mNeedPreview){
            if(mCaptureSession != null){
                // 重新建立session，旧的session会自动回收
                mCaptureSession.close();
                mCaptureSession = null;
            }
            mNeedPreview = false;
            createPreviewSession();
        }
    }

    private void createPreviewSession(){
        if(mCameraDevice == null) return;

        if(mImageReader == null){
            // 创建ImageReader，提供输出Surface
            mImageReader = ImageReader.newInstance(mPreviewSize.x, mPreviewSize.y,
                    ImageFormat.YUV_420_888, 2);
        }
        mImageReader.setOnImageAvailableListener(mImageAvailableListener, mWorkHandler);

        try {
            // 创建预览请求的Builder
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 设置预览请求的output Surface
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());
            mPreviewRequestBuilder.addTarget(mPreviewSurface);

            mCameraDevice.createCaptureSession(
                    // 此处放置Session可能使用到的Surface列表，可通过Request修改output Surface
                    getSurfaceList()
                    , mSessionCallback, mWorkHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private List<Surface> getSurfaceList(){
        return Arrays.asList(mPreviewSurface, mImageReader.getSurface());
    }

    ImageReader.OnImageAvailableListener mImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        byte[] y, u, v;
        byte[] data;
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if(image.getFormat() == ImageFormat.YUV_420_888){
                Image.Plane[] planes = image.getPlanes();
                // 重复使用同一批byte数组，减少gc频率
                if (y == null) {
                    y = new byte[planes[0].getBuffer().limit() - planes[0].getBuffer().position()];
                    u = new byte[planes[1].getBuffer().limit() - planes[1].getBuffer().position()];
                    v = new byte[planes[2].getBuffer().limit() - planes[2].getBuffer().position()];
                    Log.i(TAG, "onImageAvailable: y limit = " + planes[0].getBuffer().limit() + ", position " + planes[0].getBuffer().position());
                    Log.i(TAG, "onImageAvailable: u limit = " + planes[1].getBuffer().limit() + ", position " + planes[1].getBuffer().position());
                    Log.i(TAG, "onImageAvailable: v limit = " + planes[2].getBuffer().limit() + ", position " + planes[2].getBuffer().position());
                }
                if (image.getPlanes()[0].getBuffer().remaining() == y.length) {
                    planes[0].getBuffer().get(y);
                    planes[1].getBuffer().get(u);
                    planes[2].getBuffer().get(v);
                    setData();
                    mCameraListener.onPreviewFrame(data, image.getFormat());
                }
            }
            image.close();
        }

        private void setData(){
            if(data == null) {
                data = new byte[y.length + (u.length + 1) / 2 + (v.length + 1) / 2];
                System.arraycopy(y, 0, data, 0, y.length);
            }
            int position = y.length;
            for(int i = 0; i < u.length; i += 2){
                data[position++] = v[i];
                data[position++] = u[i];
            }
        }
    };

    private CameraDevice.StateCallback mCameraOpenCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.i(TAG, "onOpened: camera opened");
            mCameraDevice = camera;

            if(mNeedPreview) {
                mNeedPreview = false;
                createPreviewSession();
            }
        }

        @Override
        public void onClosed(CameraDevice camera) {
            super.onClosed(camera);
            if(mCameraListener != null){
                mCameraListener.onCameraClosed();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
            if(mCameraListener != null){
                mCameraListener.onCameraClosed();
            }
            Log.i(TAG, "onDisconnected: camera disconnected");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.i(TAG, "onError: camera error, error: " + error);
            camera.close();
            mCameraDevice = null;
            if(mCameraListener != null){
                mCameraListener.onCameraClosed();
            }
        }
    };

    private CameraCaptureSession.StateCallback mSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            if (null == mCameraDevice) {
                return;
            }

            // 记录已经建立的session
            mCaptureSession = session;
            try {
                // 为预览请求设置对焦模式
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                mPreviewRequest = mPreviewRequestBuilder.build();
                // 发送预览请求
                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                        null, mWorkHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };
}
