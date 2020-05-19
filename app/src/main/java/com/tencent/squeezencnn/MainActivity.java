// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2017 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.tencent.squeezencnn;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback
{
    private static final int SELECT_IMAGE = 1;
    private Timer timer;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
    private RenderScript rs;
    private Type.Builder yuvType, rgbaType;
    private Allocation in, out;
    private Camera camera;
    private int mWidth, mHeight;
    private Bitmap yourSelectedImage = null;
    private Bitmap backgroundImage = null;
    private Bitmap backgroundImage_resized = null;
    private SqueezeNcnn squeezencnn = new SqueezeNcnn();
    private ImageView imageView;
    private Resources resources;
    public int[] my_list;
    private GestureDetector gestureDetector;
    private static final int COUNT = 1;
    private int count1 = 0;
    private int count2 = 0;
    private int now = 0;
    private final int cameraId = 1; // 0是后置摄像头,1是前置摄像头
    private final int IMAGE_NUM = 5;


    //打开照相机
    public void CameraOpen() {
        try {
            //打开摄像机
            camera = Camera.open(cameraId);
            Camera.Size size = camera.getParameters().getPreviewSize();
            mWidth = size.width;
            mHeight = size.height;
            camera.setDisplayOrientation(90);
            //绑定Surface并开启预览
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        }
        catch (IOException e) {
            camera.release();
            camera = null;
            Toast.makeText(MainActivity.this, "surface created failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if(bytes != null)
        {
            count1++;
//            Log.i("count1", String.valueOf(count1++));
            yourSelectedImage = yuvToBitmap(bytes, mWidth, mHeight);
            Matrix matrix = new Matrix();
            matrix.setRotate(270);
            yourSelectedImage = Bitmap.createScaledBitmap(yourSelectedImage, 360, 240, false);
            if (backgroundImage_resized == null) {
                backgroundImage_resized = Bitmap.createScaledBitmap(backgroundImage, 240, 360, false);
            }
            if(yourSelectedImage != null)
            {
                Bitmap newBM = Bitmap.createBitmap(yourSelectedImage, 0, 0, 360, 240, matrix, false);
                squeezencnn.Detect(newBM, backgroundImage_resized, false);
                imageView.setImageBitmap(newBM);
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        //检查权限
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        }
        else {
            CameraOpen();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(this);
            camera.setDisplayOrientation(90);
            camera.startPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    class MyGestureListenter extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1.getX() - e2.getX() > 50) {
                now = (now + 1) % IMAGE_NUM;
                backgroundImage = BitmapFactory.decodeResource(resources, my_list[now]);
                backgroundImage_resized = null;
            }
            else if (e2.getX() - e1.getX() > 50) {
                now = (now + IMAGE_NUM - 1) % IMAGE_NUM;
                backgroundImage = BitmapFactory.decodeResource(resources, my_list[now]);
                backgroundImage_resized = null;
            }
            return super.onFling(e1, e2, velocityX, velocityY);
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        resources = getResources();
        backgroundImage = BitmapFactory.decodeResource(resources, R.drawable.p0);

        rs = RenderScript.create(this);
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));

        my_list = new int[IMAGE_NUM];
        my_list[0] = R.drawable.p0;
        my_list[1] = R.drawable.p1;
        my_list[2] = R.drawable.p2;
        my_list[3] = R.drawable.p3;
        my_list[4] = R.drawable.p4;

        Log.i("myDebug", "my_list ok!");

        gestureDetector = new GestureDetector(new MyGestureListenter());

        imageView = (ImageView) findViewById(R.id.imageView);
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return false;
            }
        });

        Log.i("myDebug", "imageView ok!");

        boolean ret_init = squeezencnn.Init(getAssets());
        if (!ret_init)
        {
            Log.e("MainActivity", "squeezencnn Init failed");
        }

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                handler.sendEmptyMessage(COUNT);
            }
        }, 0, 1000);
        Log.i("myDebug", "Timer ok!");
    }

    public Bitmap yuvToBitmap(byte[] yuv, int width, int height){
        if (yuvType == null){
            yuvType = new Type.Builder(rs, Element.U8(rs)).setX(yuv.length);
            in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);
            rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height);
            out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
        }
        in.copyFrom(yuv);
        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);
        Bitmap rgbout = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        out.copyTo(rgbout);
        return rgbout;
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
//        return super.onTouchEvent(event);
        return gestureDetector.onTouchEvent(event);
    }


    private Handler handler = new Handler() {
        int num = 0;

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case COUNT:
                    if (num == 5) {
                        Log.i("5s, count", String.valueOf(count1));
                    }
                    if (num == 10) {
                        Log.i("10s, count", String.valueOf(count1));
                    }
                    if (num == 20) {
                        Log.i("20s, count", String.valueOf(count1));
                    }
                    if (num == 30) {
                        Log.i("30s, count", String.valueOf(count1));
                    }
                    num++;
                    break;
                default:
                    break;
            }
        }
    };
}

