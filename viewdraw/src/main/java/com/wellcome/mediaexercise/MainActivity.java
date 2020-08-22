package com.wellcome.mediaexercise;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setImageView();
        setSurfaceView();
        setMyImageView();
    }

    private void setImageView(){
        ImageView imageView = findViewById(R.id.image);
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getApplicationContext().getAssets().open("秒五.jpg"));
            imageView.setImageBitmap(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setSurfaceView(){
        SurfaceView surfaceView = findViewById(R.id.surface_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    Bitmap bitmap = BitmapFactory.decodeStream(getApplicationContext().getAssets().open("秒五.jpg"));

                    Paint paint = new Paint();
                    paint.setAntiAlias(true);
                    paint.setStyle(Paint.Style.FILL);

                    Canvas canvas = holder.lockCanvas();
                    int height = (canvas.getHeight() - bitmap.getHeight() * canvas.getWidth() / bitmap.getWidth()) / 2;
                    canvas.drawBitmap(bitmap,
                            new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                            new Rect(0, height, canvas.getWidth(), canvas.getHeight() - height),
                            paint);
                    holder.unlockCanvasAndPost(canvas);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }

    private void setMyImageView(){
        MyImageView myImageView = findViewById(R.id.my_image_view);
        myImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MyImageView)v).setBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.test));
            }
        });
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getApplicationContext().getAssets().open("秒五.jpg"));
            myImageView.setBitmap(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
