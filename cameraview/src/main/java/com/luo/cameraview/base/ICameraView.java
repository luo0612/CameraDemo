package com.luo.cameraview.base;

import android.view.View;

import java.util.Set;

public interface ICameraView {

    interface Callback {
        void onCameraOpened();

        void onCameraClosed();

        void onPictureTaken(byte[] data);
    }

    View getView();

    boolean start();

    void stop();

    boolean isCameraOpened();

    void setFacing(int facing);

    int getFacing();

    Set<AspectRatio> getSupportedAspectRatios();

    /**
     * @param ratio
     * @return {@code true} if the aspect ratio was changed
     */
    boolean setAspectRation(AspectRatio ratio);

    AspectRatio getAspectRation();

    void setAutoFocus(boolean autoFocus);

    boolean getAutoFocus();

    void setFlash(int flash);

    int getFlash();

    void takePicture();

    void setDisplayOrientation(int displayOrientation);
}
