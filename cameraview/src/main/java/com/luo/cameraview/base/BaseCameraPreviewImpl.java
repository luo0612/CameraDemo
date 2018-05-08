package com.luo.cameraview.base;

import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;

public abstract class BaseCameraPreviewImpl implements ICameraPreview {

    protected Callback mCallback;
    private int mWidth;
    private int mHeight;

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public void dispatchSurfaceChanged() {
        if (mCallback != null) {
            mCallback.onSurfaceChanged();
        }
    }

    @Override
    public SurfaceHolder getSurfaceHolder() {
        return null;
    }

    @Override
    public Object getSurfaceTexture() {
        return null;
    }

    @Override
    public void setBufferSize(int width, int height) {

    }

    @Override
    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }
}
