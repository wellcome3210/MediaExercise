package com.wellcome.camerapreview;


import android.graphics.Point;

public abstract class CameraHelperBase {
    protected CameraListener mCameraListener;
    protected Point mPreviewSize = new Point();

    public void setCameraListener(CameraListener cameraListener) {
        mCameraListener = cameraListener;
    }

    public void setPreviewSize(Point previewSize) {
        mPreviewSize.x = previewSize.x;
        mPreviewSize.y = previewSize.y;
    }

    public interface CameraListener {
        void onPreviewFrame(byte[] data, int imageFormat);
        void onCameraOpened();
        void onCameraClosed();
    }

    public abstract void stopPreview();

    public abstract boolean isPreviewing();

    public abstract void startPreview();

    public abstract void closeCamera();

    public abstract void setupCamera();
}
