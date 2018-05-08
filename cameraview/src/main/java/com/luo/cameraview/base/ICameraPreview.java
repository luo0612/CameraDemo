package com.luo.cameraview.base;

import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;

public interface ICameraPreview {
    interface Callback {
        void onSurfaceChanged();
    }

    void setCallback(Callback callback);

    Surface getSurface();

    View getView();

    Class getOutputClass();

    /**
     * @param displayOrientation
     */
    void setDisplayOrientation(int displayOrientation);

    boolean isReady();

    void dispatchSurfaceChanged();

    SurfaceHolder getSurfaceHolder();

    Object getSurfaceTexture();

    void setBufferSize(int width, int height);

    void setSize(int width, int height);

    int getWidth();

    int getHeight();


}
