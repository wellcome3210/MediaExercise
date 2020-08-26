package com.wellcome.audioexercise;


import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private Handler mHandler;

    class MyHandler extends Handler{
        public MyHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what){
                case 0:{
                    Toast.makeText(getApplicationContext(), (String)msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new MyHandler(getMainLooper());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(!checkPermission()){
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 0);
            }
        }

        Button button = findViewById(R.id.start);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecord();
            }
        });
        button = findViewById(R.id.stop);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecord();
            }
        });
        button = findViewById(R.id.play);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playWav();
            }
        });
    }

    private void playWav(){
        if(!isRecording && !isPlaying){
            isPlaying = true;
            File file = new File(getFileName());
            if(!file.exists()){
                mHandler.obtainMessage(0, "file not exist").sendToTarget();
                return;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        FileInputStream fis = new FileInputStream(getFileName());
                        byte[] header = new byte[44];
                        fis.read(header, 0, header.length);
                        PcmWavUtil.WavHeader wavHeader = PcmWavUtil.transformToWavHeader(header);
                        AudioTrack audioTrack = new AudioTrack(
                                new AudioAttributes.Builder()
                                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .build(),
                                new AudioFormat.Builder()
                                        .setEncoding(bits2AudioFormat(wavHeader.bitsPerSample))
                                        .setChannelMask(wavHeader.channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO)
                                        .setSampleRate(wavHeader.samplesPerSec)
                                        .build(),
                                wavHeader.bytesPerSec,
                                AudioTrack.MODE_STREAM,
                                AudioManager.AUDIO_SESSION_ID_GENERATE);
                        mHandler.obtainMessage(0, "wav file playing start").sendToTarget();
                        audioTrack.play();
                        int ret;
                        byte[] buf = new byte[wavHeader.bytesPerSec];
                        while((ret = fis.read(buf, 0, buf.length)) != -1){
                            audioTrack.write(buf, 0,ret);
                        }
                        audioTrack.stop();
                        fis.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }finally {
                        mHandler.obtainMessage(0, "wav file playing finished").sendToTarget();
                        isPlaying = false;
                    }
                }
            }).start();
        }
    }

    private String getFileName(){
        return getApplicationContext().getFilesDir().getAbsolutePath() + File.separator + "test.wav";
    }

    AudioRecord mAudioRecord;
    boolean isRecording;
    boolean isPlaying;

    private void startRecord(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(!checkPermission()){
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 0);
                return;
            }
        }
        if(isRecording) return;
        int bufferSize = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if(mAudioRecord == null) {
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    8000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (MainActivity.this){
                    isRecording = true;
                }
                mHandler.obtainMessage(0, "recording start").sendToTarget();
                mAudioRecord.startRecording();
                FileOutputStream fos;
                try {
                    File file = new File(getApplicationContext().getFilesDir().getAbsolutePath() + File.separator + "test.pcm");
                    fos = new FileOutputStream(file);
                    byte[] buf = new byte[1024];
                    while(isRecording) {
                        // 需要等待IO
                        int size = mAudioRecord.read(buf, 0, buf.length);
                        if(size > 0) {
                            fos.write(buf, 0, size);
                        }
                    }
                    mAudioRecord.stop();
                    fos.flush();
                    fos.close();
                    // 文件另存为，为pcm加上wav头
                    String newFile = PcmWavUtil.pcm2Wav(mAudioRecord.getChannelCount(), mAudioRecord.getSampleRate(), audioFormat2Bits(mAudioRecord.getAudioFormat()), file);
                    Log.i(TAG, "run: save pcm to wav with new file " + newFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }finally {
                    mHandler.obtainMessage(0, "recording finished").sendToTarget();
                    isRecording = false;
                }
            }
        }).start();
    }

    private int audioFormat2Bits(int audioFormat){
        switch (audioFormat){
            case AudioFormat.ENCODING_PCM_8BIT:
                return 8;
            case AudioFormat.ENCODING_PCM_16BIT:
                return 16;
            default:
                return 8;
        }
    }

    private int bits2AudioFormat(int bits){
        switch (bits){
            case 8:
                return AudioFormat.ENCODING_PCM_8BIT;
            case 16:
                return AudioFormat.ENCODING_PCM_16BIT;
            default:
                return AudioFormat.ENCODING_PCM_8BIT;
        }
    }

    private void stopRecord(){
        isRecording = false;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean checkPermission(){
        List<String> permissions = new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED){
            permissions.add(Manifest.permission.RECORD_AUDIO);
            return false;
        }else{
            return true;
        }
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
