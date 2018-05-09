package com.luo.cameraview.camera2;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import com.luo.cameraview.Constants;
import com.luo.cameraview.base.AspectRatio;
import com.luo.cameraview.base.BaseCameraViewImpl;
import com.luo.cameraview.base.ICameraPreview;
import com.luo.cameraview.base.Size;
import com.luo.cameraview.base.SizeMap;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.SortedSet;

/**
 * 拍照流程:
 * 1.用CameraManager的openCamera(String cameraId,CameraDevice.StateCallback callback,Handler handler)
 * 方法打开指定摄像头.
 * 该方法的第一个参数代表要打开的摄像头ID, 第二个参数用于监听摄像头的状态, 第三个参数代表执行callback的Handler,
 * 如果希望程序直接在当前相册中执行callback, 则可将Handler参数设为null
 * 2.当摄像头打开之后, 程序即可获取CameraDevice
 * 即根据摄像头的ID获取指定摄像头设备, 然后调用CameraDevice的
 * createCaptureSession(List<Surface> outputs,CameraCaptureSession.StateCallback callback, Handler handler)
 * 方法来创建CameraCaptureSession.
 * 该方法的第一个参数是一个List集合, 封装了所有需要从改摄像头获取图片的Surface,
 * 第二个参数用于监听CameraCaptureSession的创建过程,
 * 第三个参数代表执行callback的Handler, 如果程序直接在当前线程中执行callback, 则将Handler参数设置为null
 * 3.不管是预览还是拍照, 程序都调用CameraDevice的createCaptureRequest(int templateType)方法创建
 * CaptureRequest.Builder, 该方法支持TEMPLATE_PREVIEW(预览), TEMPLATE_RECORD(拍摄视频),
 * TEMPLATE_STILL_CAPTURE(拍照)等参数
 * 4.通过上面第三步所调用方法返回的CaptureRequest.Builder设置拍照的各种参数, 比如对焦模式,曝光模式等
 * 5.调用CaptureRequest.Builder的build()方法即可拿到CaptureRequest对象,
 * 接下来程序通过CameraCaptureSession的setRepeatingRequest()方法开始预览, 或调用capture()方法进行拍照
 */
@TargetApi(21)
public class Camera2 extends BaseCameraViewImpl {

    private static final String TAG = "Camera2";

    private static final SparseIntArray INTERNAL_FACINGS = new SparseIntArray();

    /**
     * 保存前置摄像头和后置摄像头的特性
     */
    static {
        INTERNAL_FACINGS.put(Constants.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK);
        INTERNAL_FACINGS.put(Constants.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT);
    }

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * 摄像头管理器.<br>
     * 是全新的系统管理器, 专门用于检测系统摄像头, 打开系统摄像头<br>
     * 通过调用CameraManager的getCameraCharacteristics(String cameraId)方法,
     * 即可获取指定摄像头的相关特性
     */
    private final CameraManager mCameraManager;

    private String mCameraId;
    /**
     * 摄像头特性.
     * 该对象通过CameraManager获取, 用于描述特定摄像头所支持的各种特性.
     * 类似原来的CameraInfo
     */
    private CameraCharacteristics mCameraCharacteristics;
    /**
     * 代表系统摄像头.
     * 类似原来的Camera.
     * 每个CameraDevice自己会负责建立CameraCaptureSession以及建立CaptureRequest
     */
    private CameraDevice mCamera;
    /**
     * 该类是非常重要的API.
     * 当程序需要预览,拍照时, 都需要先通过该类的实例创建Session.
     * 不管预览还是拍照, 也都是由该对象的方法进行控制.
     * 其中控制预览的方法为setRepeatingRequest();
     * 控制拍照的方法为capture();
     * <p>
     * 为监听CameraCaptureSession的创建过程, 以及监听CameraCapture的拍照过程,
     * Camera v2 API为CameraCaptureSession提供StateCallback和CaptureCallback等内部类
     */
    private CameraCaptureSession mCaptureSession;
    /**
     * CameraRequest何CameraRequest.Builder:
     * 当程序调用setRepeatingRequest()方法进行预览时,
     * 或调用capture()方法进行拍照时,都需要传入CameraRequest参数.
     * CameraRequest代表一次捕获请求, 用于描述捕获图片的各种参数设置.
     * 比如对焦模式,曝光模式...等, 总之程序对照片所做的各种控制,
     * 都是通过CameraRequest参数进行设置.
     * CameraRequestBuilder则负责生成CameraRequest对象
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private ImageReader mImageReader;
    private final SizeMap mPreviewSizes = new SizeMap();
    private final SizeMap mPictureSizes = new SizeMap();
    private int mFacing;
    private AspectRatio mAspectRatio = Constants.DEFAULT_ASPECT_RATION;
    private boolean mAutoFocus;
    private int mFlash;
    private int mDisplayOrientation;

    private final CameraDevice.StateCallback mCameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCamera = camera;// 获取到摄像头设备
            mCallback.onCameraOpened();//回调摄像头已经打开
            startCaptureSession();//开始进行预览
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            super.onClosed(camera);
            mCallback.onCameraClosed();//回调摄像头关闭

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCamera = null;//摄像头断开
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "onError " + camera.getId() + " (" + error + ")");
            mCamera = null;//摄像头出错
        }
    };


    private final CameraCaptureSession.StateCallback mCameraCaptureSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            //  This method is called when the camera device has finished configuring itself, and the
            //  session can start processing capture requests.
            if (mCamera == null) {
                return;
            }

            mCaptureSession = session;
            updateAutoFocus();//更新自动对焦
            updateFlash();//更新闪光模式

            try {
                //进行预览
                mCaptureSession.setRepeatingRequest(
                        mPreviewRequestBuilder.build(), mPictureCaptureCallback, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                Log.e(TAG, "Failed to start camera preview because it couldn't access camera", e);
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "Failed to configure capture session");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            super.onClosed(session);
            if (mCaptureSession != null && mCaptureSession.equals(session)) {
                mCaptureSession = null;
            }
        }
    };

    PictureCaptureCallback mPictureCaptureCallback = new Camera2.PictureCaptureCallback() {
        @Override
        public void onPrecaptureRequired() {
            //trigger:触发
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            setState(STATE_PRECAPTURE);
            try {
                mCaptureSession.capture(mPreviewRequestBuilder.build(), this, null);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onReady() {
            //捕获静态图片
            captureStillPicture();
        }
    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            // java1.7特性，叫做try-with-resource，
            // 实现了AutoCloseable接口的实例可以放在try(...)中在离开try块时将自动调用close()方法。
            // 该方法调用可以看做在finally块中，所以资源的释放一定会执行，不过能不能成功释放还是得看close方法是否正常返回。
            // 所有实现Closeable的类声明都可以写在里面,最常见于流操作,socket操作,新版的httpclient也可以;
            // 需要注意的是,try()的括号中可以写多行声明,每个声明的变量类型都必须是Closeable的子类,用分号隔开
            /*
            try(
                InputStream is = new FileInputStream("...");
                OutputStream os = new FileOutputStream("...");
            ){
                //...
            }catch (IOException e) {
                //...
            }
             */
            //从ImageReader的队列获取下一个图像
            try (Image image = reader.acquireNextImage()) {
                //Image.getPlanes(): 获取图片的像素平面数组
                //像素平面数组的数量是由图片格式决定的,
                //如果图片的格式是{@link android.graphics.ImageFormat#PRIVATE PRIVATE},
                //则获取的数据为空, 因为不能访问到图片的像素数据, 可以通过该方法校验图片格式
                //此处就是用于校验图片格式
                Image.Plane[] planes = image.getPlanes();
                if (planes.length > 0) {
                    ByteBuffer buffer = planes[0].getBuffer();
                    //ByteBuffer.remaining()，此方法最给力，返回剩余的可用长度，此长度为实际读取的数据长度，最大自然是底层数组的长度
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    //回调图片的数据
                    mCallback.onPictureTaken(data);
                }
            }
        }
    };

    private void captureStillPicture() {
        try {
            CaptureRequest.Builder captureRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            //Add a surface to the list of targets for this request
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            //AF:auto focus 自动聚焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE));
            switch (mFlash) {
                case Constants.FLASH_OFF:
                    //AE:auto exposure 自动曝光
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    //FLASH_MODE:闪光模式
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    break;
                case Constants.FLASH_ON:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    break;
                case Constants.FLASH_TORCH:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);//开启手电筒
                    break;
                case Constants.FLASH_AUTO:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    break;
                case Constants.FLASH_REDEYE:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    break;
            }
            Integer sensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, (sensorOrientation + mDisplayOrientation * (mFacing == Constants.FACING_FRONT ? 1 : -1)) % 360);

            mCaptureSession.stopRepeating();//停止预览
            //进行捕获图片
            mCaptureSession.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    unlockFocus();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocus() {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        try {
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mPictureCaptureCallback, null);
            updateAutoFocus();
            updateFlash();
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPictureCaptureCallback, null);
            mPictureCaptureCallback.setState(PictureCaptureCallback.STATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 更新闪光模式
     */
    private void updateFlash() {
        switch (mFlash) {
            case Constants.FLASH_OFF:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_ON:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_TORCH:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                break;
            case Constants.FLASH_AUTO:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_REDEYE:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
        }
    }

    /**
     * 更新自动对焦
     */
    private void updateAutoFocus() {
        if (mAutoFocus) {
            int[] modes = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            if (modes == null || modes.length == 0 ||
                    (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
                mAutoFocus = false;
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            } else {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }
        } else {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        }
    }


    /**
     * 开始进行预览
     */
    private void startCaptureSession() {
        if (!isCameraOpened() || !mCameraPreview.isReady() || mImageReader == null) {
            return;
        }
        Size previewSize = chooseOptimalSize();
        mCameraPreview.setBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface surface = mCameraPreview.getSurface();
        try {
            mPreviewRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCamera.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mCameraCaptureSessionCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to start camera session");
        }

    }

    private Size chooseOptimalSize() {
        int surfaceLonger, surfaceShorter;
        int surfaceWidth = mCameraPreview.getWidth();
        int surfaceHeight = mCameraPreview.getHeight();
        if (surfaceWidth < surfaceHeight) {
            surfaceLonger = surfaceHeight;
            surfaceShorter = surfaceWidth;
        } else {
            surfaceLonger = surfaceWidth;
            surfaceShorter = surfaceHeight;
        }

        SortedSet<Size> candidates = mPreviewSizes.sizes(mAspectRatio);
        for (Size size : candidates) {
            if (size.getWidth() >= surfaceLonger && size.getHeight() >= surfaceShorter) {
                return size;
            }
        }
        return candidates.last();
    }

    protected Camera2(Callback callback, ICameraPreview cameraPreview, Context context) {
        super(callback, cameraPreview);
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mCameraPreview.setCallback(new ICameraPreview.Callback() {
            @Override
            public void onSurfaceChanged() {
                startCaptureSession();
            }
        });
    }

    @Override
    public boolean start() {
        //选择摄像头
        if (!chooseCameraIdByFacing()) {
            return false;
        }
        //收集摄像头信息
        collectCameraInfo();
        //准备ImageReader
        prepareImageReader();
        //开启摄像头
        startOpeningCamera();
        return true;
    }

    /**
     * 1.打开摄像头, 在CameraDevice.StateCallback回调中获取到CameraDevice对象, 并调用预览
     */
    @SuppressLint("MissingPermission")
    private void startOpeningCamera() {
        try {
            mCameraManager.openCamera(mCameraId, mCameraDeviceCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 3.准备ImageReader
     */
    private void prepareImageReader() {
        if (mImageReader != null) {
            mImageReader.close();
        }
        //获取支持该比例的最大宽高
        Size largest = mPictureSizes.sizes(mAspectRatio).last();
        //获取ImageReader
        mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 2);
        //注册当ImageReader获取到新图像时的监听
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);
    }

    /**
     * 2. 手机摄像头相关信息
     */
    private void collectCameraInfo() {
        //CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP:
        //Format;Size;Hardware Level;Notes
        // 该相机设备支持的可用流配置Format;还包括最小帧持续时间和每个格式/大小组合的延迟时间。
        StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            throw new IllegalStateException("Failed to get configuration map:" + mCameraId);
        }

        mPreviewSizes.clear();
        for (android.util.Size size : map.getOutputSizes(mCameraPreview.getOutputClass())) {
            int width = size.getWidth();
            int height = size.getHeight();
            if (width <= MAX_PREVIEW_WIDTH && height <= MAX_PREVIEW_HEIGHT) {
                mPreviewSizes.add(new Size(width, height));
            }

            mPictureSizes.clear();
            //手机图片的信息
            collectPictureSizes(mPictureSizes, map);

            //保证图片比例和预览的比例一致
            for (AspectRatio ratio : mPreviewSizes.ratios()) {
                if (!mPictureSizes.ratios().contains(ratio)) {
                    mPreviewSizes.remove(ratio);
                }
            }

            //如果预览的比例不支持当前设置的比例, 自动获取支持比例中的第一个
            if (!mPreviewSizes.ratios().contains(mAspectRatio)) {
                mAspectRatio = mPreviewSizes.ratios().iterator().next();
            }
        }

    }

    /**
     * 2.1. 手机图片的信息
     *
     * @param pictureSizes
     * @param map
     */
    private void collectPictureSizes(SizeMap pictureSizes, StreamConfigurationMap map) {
        for (android.util.Size size : map.getOutputSizes(ImageFormat.JPEG)) {
            mPictureSizes.add(new Size(size.getWidth(), size.getHeight()));
        }
    }

    /**
     * 1.通过Facing选择CameraId, 并获得CameraCharacteristics摄像头特性对象,
     * 获取到摄像头ID, 获取到摄像头的类型mFacing:前置,后置,外置
     *
     * @return
     */
    private boolean chooseCameraIdByFacing() {
        int internalFacing = INTERNAL_FACINGS.get(mFacing);
        try {
            String[] ids = mCameraManager.getCameraIdList();
            if (ids.length == 0) {
                throw new RuntimeException("No camera available");
            }
            for (String id : ids) {
                //通过摄像头的ID获取对应摄像头的特性
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                //CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY: 向后兼容模式
                if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    continue;
                }

                //CameraCharacteristics.LENS_FACING: 相对于屏幕的摄像头
                Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (internal == null) {
                    throw new NullPointerException("Unexpected state: LENS_FACING null");
                }

                if (internal == internalFacing) {
                    mCameraId = id;
                    mCameraCharacteristics = characteristics;
                    return true;
                }
            }

            // 没有找到设定的摄像头, 直接获取第一个摄像头ID
            mCameraId = ids[0];
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            Integer level = mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                return false;
            }

            Integer internal = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (internal == null) {
                throw new NullPointerException("Unexpected state: LENS_FACING null");
            }

            //设置mFacing, 通过获取到摄像头
            for (int i = 0, count = INTERNAL_FACINGS.size(); i < count; i++) {
                if (INTERNAL_FACINGS.valueAt(i) == internal) {
                    mFacing = INTERNAL_FACINGS.keyAt(i);
                    return true;
                }
            }

            mFacing = Constants.FACING_BACK;
            return true;
        } catch (CameraAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to get a list of camera devices", e);
        }
    }

    @Override
    public void stop() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }

        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }

        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    @Override
    public boolean isCameraOpened() {
        return mCamera != null;
    }

    @Override
    public void setFacing(int facing) {
        if (mFacing == facing) {
            return;
        }
        mFacing = facing;
        if (isCameraOpened()) {
            //关闭摄像头,然后重新打开, 用于切换摄像头
            stop();
            start();
        }
    }

    @Override
    public int getFacing() {
        return mFacing;
    }

    @Override
    public Set<AspectRatio> getSupportedAspectRatios() {
        return mPreviewSizes.ratios();
    }

    @Override
    public boolean setAspectRation(AspectRatio ratio) {
        if (ratio == null || ratio.equals(mAspectRatio) ||
                !mPreviewSizes.ratios().contains(ratio)) {
            //TODO : Better error handling
            return false;
        }
        //设置比例
        mAspectRatio = ratio;
        prepareImageReader();
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
            //重新开始预览
            startCaptureSession();
        }
        return true;
    }

    @Override
    public AspectRatio getAspectRation() {
        return mAspectRatio;
    }

    @Override
    public void setAutoFocus(boolean autoFocus) {
        if (mAutoFocus = autoFocus) {
            return;
        }
        mAutoFocus = autoFocus;
        if (mPreviewRequestBuilder != null) {
            updateAutoFocus();
            if (mCaptureSession != null) {
                try {
                    //开始预览
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPictureCaptureCallback, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                    mAutoFocus = !mAutoFocus;
                }
            }
        }
    }

    @Override
    public boolean getAutoFocus() {
        return mAutoFocus;
    }

    @Override
    public void setFlash(int flash) {
        if (mFlash == flash) {
            return;
        }

        int saved = mFlash;
        mFlash = flash;

        if (mPreviewRequestBuilder != null) {
            updateFlash();
            if (mCaptureSession != null) {
                try {
                    //设置预览
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPictureCaptureCallback, null);

                } catch (CameraAccessException e) {
                    e.printStackTrace();
                    mFlash = saved;
                }
            }
        }
    }

    @Override
    public int getFlash() {
        return mFlash;
    }

    @Override
    public void takePicture() {
        if (mAutoFocus) {
            lockFocus();
        } else {
            captureStillPicture();
        }
    }

    /**
     * Locks the focus as the first step for a still image capture
     * 锁住焦点是拍照的第一步
     */
    private void lockFocus() {
        //AF: 自动对焦
        //AE: 自动曝光
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        mPictureCaptureCallback.setState(PictureCaptureCallback.STATE_LOCKING);
        try {
            //拍照
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mPictureCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to lock focus", e);
        }
    }

    @Override
    public void setDisplayOrientation(int displayOrientation) {
        mDisplayOrientation = displayOrientation;
        mCameraPreview.setDisplayOrientation(mDisplayOrientation);
    }

    private static abstract class PictureCaptureCallback extends CameraCaptureSession.CaptureCallback {
        static final int STATE_PREVIEW = 0;
        static final int STATE_LOCKING = 1;
        static final int STATE_LOCKED = 2;
        static final int STATE_PRECAPTURE = 3;
        static final int STATE_WAITING = 4;
        static final int STATE_CAPTURING = 5;

        private int mState;

        public PictureCaptureCallback() {
            super();
        }

        void setState(int state) {
            mState = state;
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }

        private void process(CaptureResult result) {
            //进行状态判断处理
            switch (mState) {
                case STATE_LOCKING: {
                    Integer af = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (af == null) {
                        break;
                    }
                    if (af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            setState(STATE_CAPTURING);
                            onReady();
                        } else {
                            setState(STATE_LOCKED);
                            onPrecaptureRequired();
                        }
                    }
                    break;
                }
                case STATE_CAPTURING: {
                    Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                            ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        setState(STATE_WAITING);
                    }
                }
                break;
                case STATE_WAITING: {
                    Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        setState(STATE_CAPTURING);
                        onReady();
                    }
                }
                break;
            }
        }

        public abstract void onPrecaptureRequired();

        public abstract void onReady();
    }
}
