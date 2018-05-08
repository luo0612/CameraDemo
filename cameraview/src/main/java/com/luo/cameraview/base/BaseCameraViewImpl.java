package com.luo.cameraview.base;

import android.view.View;

public abstract class BaseCameraViewImpl implements ICameraView {
    protected final Callback mCallback;
    protected final ICameraPreview mCameraPreview;

    protected BaseCameraViewImpl(Callback callback, ICameraPreview cameraPreview) {
        mCallback = callback;
        mCameraPreview = cameraPreview;
    }

    @Override
    public View getView() {
        return mCameraPreview.getView();
    }
}
