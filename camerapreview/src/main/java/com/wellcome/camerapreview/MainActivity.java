package com.wellcome.camerapreview;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private SurfaceView mSurfaceView;
    private TextureView mTextureView;
    private boolean mTextureReady;
    private Surface mTextureSurface;
    private boolean mSurfaceViewReady;
    private TextView mTextView;
    private Camera2Helper mCamera2Helper;

    private CameraHelper mCameraHelper;

    private Point mPreviewSize;

    boolean mIsSurfaceViewSurface;

    private Handler mHandler;

    private boolean mIsCamera2;
    private boolean mIsSwitchingCamera;

    private boolean mIsPreviewing;

    class MyHandler extends Handler{
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what){
                case 0:{
                    Toast.makeText(getApplicationContext(), (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = findViewById(R.id.surface_view);
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mSurfaceViewReady = true;
                if(mIsSurfaceViewSurface){
                    mPreviewSize.x = mSurfaceView.getWidth();
                    mPreviewSize.y = mSurfaceView.getHeight();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mSurfaceViewReady = false;
            }
        });
        mTextureView = findViewById(R.id.texture_view);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mTextureReady = true;
                mTextureSurface = new Surface(surface);
                if(!mIsSurfaceViewSurface){
                    mPreviewSize.x = mSurfaceView.getWidth();
                    mPreviewSize.y = mSurfaceView.getHeight();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                mTextureReady = false;
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
        mTextView = findViewById(R.id.textview);
        setTextViewText();

        mHandler = new MyHandler();

        mPreviewSize = new Point();
        findViewById(R.id.start_preview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isSurfaceAvailable()){
                    mHandler.obtainMessage(0, "no surface is ready!").sendToTarget();
                    return;
                }
                if(mIsCamera2) {
                    if(mCamera2Helper == null){
                        mCamera2Helper = new Camera2Helper(getApplicationContext(), mCameraListener);
                    }
                    if(mCamera2Helper.isPreviewing()) {
                        mIsPreviewing = false;
                        mCamera2Helper.stopPreview();
                    }else {
                        mIsPreviewing = true;
                        mCamera2Helper.setPreviewSurface(selectSurface());
                        mCamera2Helper.setPreviewSize(mPreviewSize);
                        mCamera2Helper.startPreview();
                    }
                }else{
                    if(mCameraHelper == null){
                        mCameraHelper = new CameraHelper(getApplicationContext(), mCameraListener);
                    }
                    if(mCameraHelper.isPreviewing()){
                        mIsPreviewing = false;
                        mCameraHelper.stopPreview();
                    }else{
                        mIsPreviewing = true;
                        setSurfaceForCamera();
                        mCameraHelper.setPreviewSize(mPreviewSize);
                        mCameraHelper.setupCamera();
                        mCameraHelper.startPreview();
                    }
                    setTextViewText();
                }
            }
        });

        findViewById(R.id.switch_surface).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsSurfaceViewSurface = !mIsSurfaceViewSurface;
                if(mIsCamera2) {
                    if(mCamera2Helper == null){
                        switchSurfaceNoCamera();
                    }else {
                        if(mCamera2Helper.isPreviewing()) {
                            mCamera2Helper.stopPreview();
                            mIsPreviewing = false;
                        }
                        Surface surface = selectSurface();
                        if(surface == null){
                            mHandler.obtainMessage(0, "no surface ready for preview!").sendToTarget();
                            return;
                        }
                        mCamera2Helper.setPreviewSurface(surface);
                        mCamera2Helper.setPreviewSize(mPreviewSize);
                    }
                }else{
                    if(mCameraHelper == null){
                        switchSurfaceNoCamera();
                    }else {
                        if(mCameraHelper.isPreviewing()) {
                            mCameraHelper.stopPreview();
                            mIsPreviewing = false;
                        }
                        if(!setSurfaceForCamera()){
                            mHandler.obtainMessage(0, "no surface ready for preview!").sendToTarget();
                        }
                        mCameraHelper.setPreviewSize(mPreviewSize);
                        mCameraHelper.setupCamera();
                    }
                }
                setTextViewText();
            }
        });

        findViewById(R.id.switch_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mIsCamera2){
                    mIsSwitchingCamera = true;
                    if(mCamera2Helper != null){
                        mCamera2Helper.stopPreview();
                        mCamera2Helper.closeCamera();
                    }
                }else {
                    mIsSwitchingCamera = true;
                    if(mCameraHelper != null) {
                        mCameraHelper.stopPreview();
                        mCameraHelper.closeCamera();
                    }
                }
                mIsCamera2 = !mIsCamera2;
                setTextViewText();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!checkPermission()) {
                requestPermission();
            }
        }
    }

    private boolean setSurfaceForCamera(){
        if(mIsSurfaceViewSurface && mSurfaceViewReady){
            mCameraHelper.setSurfaceHolder(mSurfaceView.getHolder());
            return true;
        }else if(!mIsSurfaceViewSurface && mTextureReady){
            mCameraHelper.setSurfaceTexture(mTextureView.getSurfaceTexture());
            return true;
        }
        return false;
    }

    private void setTextViewText(){
        mTextView.setText("Camera " + (mIsCamera2 ? 2 : 1) + ", "
                + (mIsPreviewing ? "previewing" : "not previewing") + ", "
                + (mIsSurfaceViewSurface ? "SurfaceView" : "TextureView"));
    }

    private void switchSurfaceNoCamera(){
        if(mIsSurfaceViewSurface){
            mPreviewSize.x = mSurfaceView.getWidth();
            mPreviewSize.y = mSurfaceView.getHeight();
        }else{
            mPreviewSize.x = mTextureView.getWidth();
            mPreviewSize.y = mTextureView.getHeight();
        }
        mIsSurfaceViewSurface = !mIsSurfaceViewSurface;
    }

    private void cameraStartPreview(){
        if(mCameraHelper == null){
            if(!isSurfaceAvailable()){
                mHandler.obtainMessage(0, "no surface is ready!").sendToTarget();
            }
            selectSurface();
            mCameraHelper = new CameraHelper(getApplicationContext(), mCameraListener);
            if(mIsSurfaceViewSurface && mSurfaceViewReady){
                mCameraHelper.setSurfaceHolder(mSurfaceView.getHolder());
            }else{
                mCameraHelper.setSurfaceTexture(mTextureView.getSurfaceTexture());
            }
            mCameraHelper.setPreviewSize(mPreviewSize);
            mCameraHelper.startPreview();
        }else {
            if(!mCameraHelper.isPreviewing()) {
                mCameraHelper.startPreview();
            }
        }
    }

    private boolean isSurfaceAvailable(){
        return (mIsSurfaceViewSurface && mSurfaceViewReady) || (!mIsSurfaceViewSurface &&mTextureReady);
    }

    private void camera2SwitchSurface(){
        mIsSurfaceViewSurface = !mIsSurfaceViewSurface;
        Surface surface = selectSurface();
        if(surface == null){
            mHandler.obtainMessage(0, "another surface is not ready!").sendToTarget();
            mIsSurfaceViewSurface = !mIsSurfaceViewSurface;
        }
        if(mCamera2Helper != null){
            mCamera2Helper.setPreviewSurface(surface);
            mCamera2Helper.setPreviewSize(mPreviewSize);
            mCamera2Helper.startPreview();
        }
    }

    private CameraHelperBase.CameraListener mCameraListener = new CameraHelperBase.CameraListener() {
        @Override
        public void onPreviewFrame(byte[] data, int imageFormat) {
        }

        @Override
        public void onCameraOpened() {

        }

        @Override
        public void onCameraClosed() {
        }
    };

    private Surface selectSurface(){
        if(!mTextureReady && !mSurfaceViewReady){
            Toast.makeText(getApplicationContext(), "no surface is ready!", Toast.LENGTH_SHORT).show();
            return null;
        }
        Surface surface = null;
        if(mIsSurfaceViewSurface && mSurfaceViewReady){
            surface = mSurfaceView.getHolder().getSurface();
            mPreviewSize.x = mSurfaceView.getWidth();
            mPreviewSize.y = mSurfaceView.getHeight();
        }
        else
            if(!mIsSurfaceViewSurface && mTextureReady){
            if(mTextureSurface == null){
                mTextureSurface = new Surface(mTextureView.getSurfaceTexture());
            }
            mPreviewSize.x = mTextureView.getWidth();
            mPreviewSize.y = mTextureView.getHeight();
            surface = mTextureSurface;
        }
        return surface;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean checkPermission(){
        if(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED){
            return false;
        }else{
            return true;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestPermission(){
        requestPermissions(new String[]{Manifest.permission.CAMERA}, 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 0){
            for(int i = 0; i < permissions.length; i++){
                if(grantResults[i] == PackageManager.PERMISSION_GRANTED){
                    Log.i(TAG, "onRequestPermissionsResult: " + permissions[i] + "has been granted");
                }else{
                    Log.i(TAG, "onRequestPermissionsResult: " + permissions[i] + "has been denied");
                }
            }
        }
    }
}
