package com.wellcome.camerapreview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import java.io.IOException;

import androidx.annotation.NonNull;

public class CameraHelper extends CameraHelperBase {
    private static final String TAG = CameraHelper.class.getSimpleName();
    private Context mContext;

    private int mBackCameraId = -1;
    private Camera mCamera;

    private SurfaceHolder mSurfaceHolder;
    private SurfaceTexture mSurfaceTexture;

    private boolean mIsPreviewing;

    public CameraHelper(Context context, CameraListener cameraListener) {
        mContext = context;
        mCameraListener = cameraListener;
        init();
    }

    public void setSurfaceHolder(SurfaceHolder surfaceHolder) {
        mSurfaceTexture = null;
        mSurfaceHolder = surfaceHolder;
    }

    public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
        mSurfaceHolder = null;
        mSurfaceTexture = surfaceTexture;
    }


    public void init(){
        for(int i = 0; i < Camera.getNumberOfCameras(); i++){
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(i, cameraInfo);
            if(cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK){
                mBackCameraId = i;
            }
            Log.i(TAG, "init: facing " + cameraInfo.facing + ", orientation " + cameraInfo.orientation);
        }
    }

    /**
     * Call after calling {@link #setupCamera()}
     */
    @Override
    public void startPreview(){
        mIsPreviewing = true;
        mCamera.startPreview();
    }

    @Override
    public void closeCamera() {
        mCamera.release();
        mCamera = null;
        if(mCameraListener != null){
            mCameraListener.onCameraClosed();
        }
        mIsPreviewing = false;
    }


    /**
     * Call after calling {@link #setSurfaceHolder(SurfaceHolder)} or {@link #setSurfaceTexture(SurfaceTexture)}
     */
    @Override
    public void setupCamera() {
        if(mCamera == null){
            if(mBackCameraId == -1){
                Log.w(TAG, "setupCamera: There is no back camera");
                return;
            }
            mCamera = Camera.open(mBackCameraId);
        }
        if(mCamera == null){
            Log.e(TAG, "setupCamera: open camera error");
        }
        if(mSurfaceHolder == null && mSurfaceTexture == null){
            Log.w(TAG, "setupCamera: no surface to setup camera");
            return;
        }
        try {
            setSurface();
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "setupCamera: set preview surface error");
            return;
        }
        setCameraDisplayOrientation();
        Camera.Parameters parameters = mCamera.getParameters();
        selectAndSetPreviewSize(parameters);
        // 设置自动对焦
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        parameters.setPreviewFormat(ImageFormat.NV21);
        mCamera.setPreviewCallback(mPreviewCallback);
        mCamera.setParameters(parameters);
    }

    public Camera.Parameters selectAndSetPreviewSize(Camera.Parameters parameters){
        int width = mPreviewSize.x;
        int height = mPreviewSize.y;
        Camera.CameraInfo info =
                new Camera.CameraInfo();
        Camera.getCameraInfo(mBackCameraId, info);
        // 对照摄像头方向调整，调整预览宽高
        if(info.orientation == 90 || info.orientation == 270){
            width = mPreviewSize.y;
            height = mPreviewSize.x;
        }
        Camera.Size bestSize = null;
        double beatRatioDiff = Double.MAX_VALUE;
        double ratio = width * 1.0 / height;
        for(Camera.Size size:parameters.getSupportedPreviewSizes()){
            double ratioDiff = Math.abs(size.width * 1.0 / size.height - ratio);
            // 选择与Surface宽高比适配最佳的预览尺寸
            if(beatRatioDiff > ratioDiff){
                beatRatioDiff = ratioDiff;
                if(bestSize == null){
                    bestSize = size;
                }else /*if(bestSize.height < size.height)*/{
                    bestSize = size;
                }
            }
        }
        Log.i(TAG, "selectAndSetPreviewSize: width = " + bestSize.width + ", height = " + bestSize.height);
        parameters.setPreviewSize(bestSize.width, bestSize.height);
        return parameters;
    }

    public void setCameraDisplayOrientation() {
        Camera.CameraInfo info =
                new Camera.CameraInfo();
        Camera.getCameraInfo(mBackCameraId, info);
        int rotation = ((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay()
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
        mCamera.setDisplayOrientation(result);
    }

    private void setSurface() throws IOException {
        if(mSurfaceTexture != null){
            mCamera.setPreviewTexture(mSurfaceTexture);
        }else{
            mCamera.setPreviewDisplay(mSurfaceHolder);
        }
    }

    @Override
    public void stopPreview(){
        if(mCamera != null) {
            mCamera.stopPreview();
        }
        mIsPreviewing = false;
    }

    @Override
    public boolean isPreviewing() {
        return mIsPreviewing;
    }

    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if(mCameraListener != null){
                mCameraListener.onPreviewFrame(null, 0);
            }
        }
    };
}
