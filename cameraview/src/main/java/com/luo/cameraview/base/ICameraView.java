package com.luo.cameraview.base;

import android.view.View;

import java.util.Set;

public interface ICameraView {

    interface Callback {
        /**
         * 摄像头打开的回调
         */
        void onCameraOpened();

        /**
         * 摄像头关闭的回调
         */
        void onCameraClosed();

        /**
         * 摄像头获取到数据的回调
         *
         * @param data
         */
        void onPictureTaken(byte[] data);
    }

    View getView();

    /**
     * 开启摄像头
     *
     * @return
     */
    boolean start();

    /**
     * 关闭摄像头
     */
    void stop();

    /**
     * 摄像头是否打开
     *
     * @return
     */
    boolean isCameraOpened();

    /**
     * 设置摄像头的类型
     *
     * @param facing
     */
    void setFacing(int facing);

    /**
     * 获取摄像头的类型:前置,后置,外置
     *
     * @return
     */
    int getFacing();

    /**
     * 获取所有支持的比例
     *
     * @return
     */
    Set<AspectRatio> getSupportedAspectRatios();

    /**
     * @param ratio
     * @return {@code true} if the aspect ratio was changed
     */
    boolean setAspectRation(AspectRatio ratio);

    /**
     * 获取当前的比例
     *
     * @return
     */
    AspectRatio getAspectRation();

    /**
     * 设置自动对焦
     *
     * @param autoFocus
     */
    void setAutoFocus(boolean autoFocus);

    /**
     * 获取是否是自动对焦
     *
     * @return
     */
    boolean getAutoFocus();

    /**
     * 设置闪光模式
     *
     * @param flash
     */
    void setFlash(int flash);

    /**
     * 获取闪光模式
     *
     * @return
     */
    int getFlash();

    /**
     * 拍照
     */
    void takePicture();

    /**
     * 设置显示的角度
     *
     * @param displayOrientation
     */
    void setDisplayOrientation(int displayOrientation);
}
