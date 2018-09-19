package com.cry.zero_camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.cry.zero_camera.camera.CameraApi14;
import com.cry.zero_camera.camera.CameraSize;
import com.cry.zero_camera.camera.ICamera;

import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 创建一个OpenGL环境。在这里创建Camera 和 Camera输出的SurfaceView
 */
public class CameraCaptureFilterView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private static final String TAG = "CameraView";
    //    private IAiyaCamera mCameraApi;
    private ICamera mCameraApi;
    //    private int mCameraIdDefault = Camera.CameraInfo.CAMERA_FACING_BACK;
    private int mCameraIdDefault = 0;
    private CameraFilterRender mCameraRender;
    private int width;
    private int height;

    public CameraCaptureFilterView(Context context) {
        super(context);
        initEGL();
        initCameraApi(context);
    }

    private void initEGL() {
        //open gl step 1
        setEGLContextClientVersion(2);
        setRenderer(this);
        //只有刷新之后，才会去重绘
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    private void initCameraApi(Context context) {
//        mCameraApi = new KitkatCamera();
        mCameraApi = new CameraApi14();
        mCameraRender = new CameraFilterRender(context.getResources());
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        mCameraRender.onSurfaceCreated(gl, config);

        mCameraApi.open(mCameraIdDefault);
        mCameraRender.setCameraId(mCameraIdDefault);


        CameraSize previewSize = mCameraApi.getPreviewSize();
        int previewSizeWidth = previewSize.getWidth();
        int previewSizeHeight = previewSize.getHeight();

//      Point previewSize = mCameraApi.getPreviewSize();
//        int previewSizeWidth = previewSize.x;
//        int previewSizeHeight = previewSize.y;

        mCameraRender.setPreviewSize(previewSizeWidth, previewSizeHeight);

        mCameraApi.setPreviewTexture(mCameraRender.getSurfaceTexture());
        //默认使用的GLThread.每次刷新的时候，都强制要求是刷新这个GLSurfaceView
        mCameraRender.getSurfaceTexture().setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                requestRender();
            }
        });
        mCameraApi.preview();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mCameraRender.onSurfaceChanged(gl, width, height);
        //设置ViewPort是必须要做的
        GLES20.glViewport(0, 0, width, height);
        this.width = width;
        this.height = height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        mCameraRender.onDrawFrame(gl);
    }

    @Override
    public void onPause() {
        super.onPause();
        mCameraApi.close();

        queueEvent(new Runnable() {
            @Override public void run() {
                // Tell the renderer that it's about to be paused so it can clean up.
                mCameraRender.notifyPausing();
            }
        });
        Log.d(TAG, "onPause complete");
    }

    int takePhotoFromGL = 0;

    public void takePhoto(ICamera.TakePhotoCallback callback) {
        if (takePhotoFromGL != 1) {
            if (mCameraApi != null) {
                float[] mtx = new float[16];
                mCameraRender.getSurfaceTexture().getTransformMatrix(mtx);
                mCameraApi.takePhoto(callback);
            }
        } else {
            //直接使用OpenGL的方式
            queueEvent(() -> {
                //发送到GLThread中进行
                //这里传递的长宽。其实就是openGL surface的长款，一定要注意了！！
                ByteBuffer rgbaBuf = ByteBuffer.allocateDirect(width * height * 4);
                rgbaBuf.position(0);
                long start = System.nanoTime();
                GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
                        rgbaBuf);
                long end = System.nanoTime();
                Log.d(TAG, "gl glReadPixels cost = " + (end - start));
                callback.onTakePhoto(rgbaBuf.array(), width, height);
            });
        }

    }

    public void changeRecordingState(boolean mRecordingEnabled) {
        queueEvent(() -> {
            // notify the renderer that we want to change the encoder's state
            mCameraRender.changeRecordingState(mRecordingEnabled);
        });
    }
}