package com.luo.cameraview;

import com.luo.cameraview.base.AspectRatio;

/**
 * 定义常量
 */
public interface Constants {
    /**
     * 后置摄像头
     */
    int FACING_BACK = 0;

    /**
     * 前置摄像头
     */
    int FACING_FRONT = 1;

    /**
     * 默认比例
     */
    AspectRatio DEFAULT_ASPECT_RATION = AspectRatio.of(4, 3);

    /**
     * 闪光灯关闭
     */
    int FLASH_OFF = 0;
    /**
     * 闪光灯打开
     */
    int FLASH_ON = 1;
    /**
     * 开启闪光灯
     */
    int FLASH_TORCH = 2;
    /**
     * 闪光灯自动
     */
    int FLASH_AUTO = 3;
    /**
     * 防红眼模式
     */
    int FLASH_REDEYE = 4;
}
