package com.example.camera_pc;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.Toast;

import com.quickbirdstudios.yuv2mat.Yuv;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Camera2BasicFragment";
    private static final int REQUEST_CAMERA_PERMISSION = 1;

    // Camera2 API提供的最大预览宽度和高度
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private String mCameraId;                    //正在使用的相机id
    private FullScreenTextureView mTextureView;  // 预览使用的自定义TextureView控件
    private CameraCaptureSession mCaptureSession;// 预览用的获取会话
    private CameraDevice mCameraDevice;          // 正在使用的相机
    private Size selectPreviewSize;              // 从相机支持的尺寸中选出来的最佳预览尺寸
    private Size mPreviewSize;                   // 预览数据的尺寸
    private static Range<Integer>[] fpsRanges;   // 相机的FPS范围

    private HandlerThread mBackgroundThread;     // 处理拍照等工作的子线程
    private Handler mBackgroundHandler;          // 上面定义的子线程的处理器
    private ImageReader mImageReader;            // 用于获取画面的数据，并进行识别

    private CaptureRequest.Builder mPreviewRequestBuilder;  // 预览请求构建器
    private CaptureRequest mPreviewRequest;      // 预览请求, 由上面的构建器构建出来
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);  // 信号量控制器

    /**
     * 以下是Detector部分需要的
     */
    private static List<String> resultLabel = new ArrayList<>();  // 识别结果List
    private String fileModel = null;  // 模型文件绝对地址
    private String fileWords = null;  // 类别/物体列表
    private static boolean load_success = false;  // 判断模型初始化成功与否
    private SurfaceHolder surfaceHolder;  // 用于画框的surfaceView的holder
    private Paint paint_rect;  // 画框的
    private Paint paint_txt;   // 写文本的
    private Canvas canvas;     // 画布
    private int mRotateDegree; // 屏幕旋转角度：0/90/180/270
    private OrientationEventListener orientationListener;       // 监听屏幕旋转
    private Net net;
    private static final String[] classNames = {"Background","person",
            "bicycle",
            "car",
            "motorcycle",
            "airplane",
            "bus",
            "train",
            "truck",
            "boat",
            "traffic light",
            "fire hydrant",
            "street sign",
            "stop sign",
            "parking meter",
            "bench",
            "bird",
            "cat",
            "dog",
            "horse",
            "sheep",
            "cow",
            "elephant",
            "bear",
            "zebra",
            "giraffe",
            "hat",
            "backpack",
            "umbrella",
            "shoe",
            "eye glasses",
            "handbag",
            "tie",
            "suitcase",
            "frisbee",
            "skis",
            "snowboard",
            "sports ball",
            "kite",
            "baseball bat",
            "baseball glove",
            "skateboard",
            "surfboard",
            "tennis racket",
            "bottle",
            "plate",
            "wine glass",
            "cup",
            "fork",
            "knife",
            "spoon",
            "bowl",
            "banana",
            "apple",
            "sandwich",
            "orange",
            "broccoli",
            "carrot",
            "hot dog",
            "pizza",
            "donut",
            "cake",
            "chair",
            "couch",
            "potted plant",
            "bed",
            "mirror",
            "dining table",
            "window",
            "desk",
            "toilet",
            "door",
            "tv",
            "laptop",
            "mouse",
            "remote",
            "keyboard",
            "cell phone",
            "microwave",
            "oven",
            "toaster",
            "sink",
            "refrigerator",
            "blender",
            "book",
            "clock",
            "vase",
            "scissors",
            "teddy bear",
            "hair drier",
            "toothbrush",
            "hair brush"};

    /**
     *  SurfaceTexture监听器
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable: width="+width+", height="+height);
            openCamera();    // SurfaceTexture就绪后回调执行打开相机操作
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(Objects.requireNonNull(this), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        // 设置相机输出
        setUpCameraOutputs();

        // 获取CameraManager的实例
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // 尝试获得相机开打关闭许可, 等待2500时间仍没有获得则排除异常
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            // 打开相机, 参数是: 相机id, 相机状态回调, 子线程处理器
            assert manager != null;
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void setUpCameraOutputs() {
        // 获取CameraManager实例
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // 遍历运行本应用的设备的所有摄像头
            assert manager != null;
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // 如果该摄像头是前置摄像头, 则看下一个摄像头(本应用不使用前置摄像头)
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                // StreamConfigurationMap包含相机的可输出尺寸信息
                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // 得到相机的帧率范围，可以在构建CaptureRequest的时候设置画面的帧率
                fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                Log.d(TAG, "setUpCameraOutputs: fpsRanges = " + Arrays.toString(fpsRanges));

                // 获取手机目前的旋转方向(横屏还是竖屏, 对于"自然"状态下高度大于宽度的设备来说横屏是ROTATION_90
                // 或者ROTATION_270,竖屏是ROTATION_0或者ROTATION_180)
                int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
                int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                Log.d(TAG, "displayRotation: " + displayRotation);   // displayRotation: 0
                Log.d(TAG, "sensorOritentation: " + sensorOrientation);  // sensorOritentation: 90
                switch (displayRotation) {
                    // ROTATION_0和ROTATION_180都是竖屏只需做同样的处理操作
                    // 显示为竖屏时, 若传感器方向为90或者270, 则需要进行转换(标志位置true)
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            Log.d(TAG, "swappedDimensions set true !");
                            swappedDimensions = true;
                        }
                        break;
                    // ROTATION_90和ROTATION_270都是横屏只需做同样的处理操作
                    // 显示为横屏时, 若传感器方向为0或者180, 则需要进行转换(标志位置true)
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                // 获取当前的屏幕尺寸, 放到一个点对象里
                Point screenSize = new Point();
                getWindowManager().getDefaultDisplay().getSize(screenSize);
                // 初始时将屏幕认为是横屏的
                int screenWidth = screenSize.y;  // 2029
                int screenHeight = screenSize.x; // 1080
                Log.d(TAG, "screenWidth = "+screenWidth+", screenHeight = "+screenHeight); // 2029 1080

                // swappedDimensions: (竖屏时true，横屏时false)
                if (swappedDimensions) {
                    screenWidth = screenSize.x;  // 1080
                    screenHeight = screenSize.y; // 2029
                }
                // 尺寸太大时的极端处理
                if (screenWidth > MAX_PREVIEW_HEIGHT) screenWidth = MAX_PREVIEW_HEIGHT;
                if (screenHeight > MAX_PREVIEW_WIDTH) screenHeight = MAX_PREVIEW_WIDTH;

                Log.d(TAG, "after adjust, screenWidth = "+screenWidth+", screenHeight = "+screenHeight); // 1080 1920

                // 自动计算出最适合的预览尺寸（实际从相机得到的尺寸，也是ImageReader的输入尺寸）
                // 第一个参数:map.getOutputSizes(SurfaceTexture.class)表示SurfaceTexture支持的尺寸List
                selectPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        screenWidth,screenHeight,swappedDimensions);

                // 这里返回的selectPreviewSize，要注意横竖屏的区分
                Log.d(TAG, "selectPreviewSize.getWidth: " + selectPreviewSize.getWidth());  // 1920
                Log.d(TAG, "selectPreviewSize.getHeight: " + selectPreviewSize.getHeight());  // 1080

                // 横竖屏尺寸交换，以便后面设置各种surface统一代码
                if(swappedDimensions) mPreviewSize = selectPreviewSize;
                else{
                    mPreviewSize = new Size(selectPreviewSize.getHeight(), selectPreviewSize.getWidth());
                }

                Log.d(TAG, "mPreviewSize.getWidth: " + mPreviewSize.getWidth());  // 1920
                Log.d(TAG, "mPreviewSize.getHeight: " + mPreviewSize.getHeight());  // 1080

                // 设置画框用的surfaceView的展示尺寸，也是TextureView的展示尺寸（因为是竖屏，所以宽度比高度小）
                surfaceHolder.setFixedSize(mPreviewSize.getHeight(),mPreviewSize.getWidth());
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());

                // 输入相机的尺寸必须是相机支持的尺寸，这样画面才能不失真，TextureView输入相机的尺寸也是这个
                mImageReader = ImageReader.newInstance(selectPreviewSize.getWidth(), selectPreviewSize.getHeight(),
                        ImageFormat.YUV_420_888, /*maxImages*/5);
                mImageReader.setOnImageAvailableListener(   // 设置监听和后台线程处理器
                        mOnImageAvailableListener, mBackgroundHandler);

                mCameraId = cameraId;   // 获得当前相机的Id
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            Log.e(TAG, "setUpCameraOutputs: error output!");
        }
    }

    private Size chooseOptimalSize(Size[] choices, int screenWidth, int screenHeight, boolean swappedDimensions) {
        List<Size> bigEnough = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();
        if(swappedDimensions){  // 竖屏
            for(Size option : choices){
                String str = "["+option.getWidth()+", "+option.getHeight()+"]";
                stringBuilder.append(str);
                if(option.getHeight() != screenWidth || option.getWidth() > screenHeight) continue;
                bigEnough.add(option);
            }
        } else{     // 横屏
            for(Size option : choices){
                String str = "["+option.getWidth()+", "+option.getHeight()+"]";
                stringBuilder.append(str);
                if(option.getWidth() != screenHeight || option.getHeight() > screenWidth) continue;
                bigEnough.add(option);
            }
        }
        Log.d(TAG, "chooseOptimalSize: "+ stringBuilder);

        if(bigEnough.size() > 0){
            return Collections.max(bigEnough, new CompareSizesByArea());
        }else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[choices.length/2];
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Log.d(TAG, "requestCameraPermission: here");
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    /**
     * 相机状态改变的回调函数
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // 当相机打开执行以下操作:
            mCameraOpenCloseLock.release();  // 1. 释放访问许可
            mCameraDevice = cameraDevice;   // 2. 将正在使用的相机指向将打开的相机
            createCameraPreviewSession();   // 3. 创建相机预览会话
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            // 当相机失去连接时执行以下操作:
            mCameraOpenCloseLock.release();   // 1. 释放访问许可
            cameraDevice.close();             // 2. 关闭相机
            mCameraDevice = null;             // 3. 将正在使用的相机指向null
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            // 当相机发生错误时执行以下操作:
            mCameraOpenCloseLock.release();      // 1. 释放访问许可
            cameraDevice.close();                // 2. 关闭相机
            mCameraDevice = null;                // 3, 将正在使用的相机指向null
            finish();                            // 4. 结束当前Activity
        }
    };

    private void createCameraPreviewSession() {
        try {
            // 获取用来预览的texture实例
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize( selectPreviewSize.getWidth(),selectPreviewSize.getHeight());  // 设置宽度和高度
            Surface surface = new Surface(texture);  // 用获取输出surface

            // 预览请求构建(创建适合相机预览窗口的请求：CameraDevice.TEMPLATE_PREVIEW字段)
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);  //请求捕获的目标surface
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());

            // 创建预览的捕获会话
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        //一个会话的创建需要比较长的时间，当创建成功后就会执行onConfigured回调
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // 相机关闭时, 直接返回
                            if (null == mCameraDevice) {
                                return;
                            }

                            // 会话可行时, 将构建的会话赋给mCaptureSession
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // 自动对焦
                                //在该模式中，AF算法连续地修改镜头位置以尝试提供恒定对焦的图像流。
                                //聚焦行为应适合静止图像采集; 通常这意味着尽可能快地聚焦。
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // 自动闪光：与ON一样，除了相机设备还控制相机的闪光灯组件，在低光照条件下启动它
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // 设置预览帧率为最高，似乎fpsRanges[fpsRanges.length-1]一般就是手机相机能支持的最大帧率，一般也就是[30,30]
                                // 至少在mi 8和华为p30 pro上是这样
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,fpsRanges[fpsRanges.length-1]);

                                // 构建上述的请求
                                //(CaptureRequest mPreviewRequest是请求捕获参数的集合，包括连续捕获的频率等)
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                // 重复进行上面构建的请求, 以便显示预览
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        null, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(MainActivity.this, "createCaptureSession Failed", Toast.LENGTH_SHORT).show();
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 全屏模式，另外在清单文件中已指定此Activity为固定竖屏模式
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        // 得到UI
        mTextureView =  findViewById(R.id.texture);
        SurfaceView surfaceView = findViewById(R.id.preview_detector_surfaceView);


        // 设置SurfaceView
        surfaceView.setZOrderOnTop(true);  // 设置surfaceView在顶层
        surfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT); // 设置surfaceView为透明
        surfaceHolder = surfaceView.getHolder();  // 获取surfaceHolder以便后面画框

    }
    @Override
    protected void onStart() {
        Log.d(TAG, "onStart: ");
        super.onStart();
        initLoadOpenCVLibs();
        readCacheLabelFromLocalFile();  // 读取model

        // 初始化画框用的两个Paint和一个Canvas，避免在子线程中重复创建
        paint_rect = new Paint();  // 画矩形的paint
        paint_rect.setColor(Color.YELLOW);
        paint_rect.setStyle(Paint.Style.STROKE);//不填充
        paint_rect.setStrokeWidth(5); //线的宽度

        paint_txt = new Paint();  // 写文本的paint
        paint_txt.setColor(Color.RED);
        paint_txt.setStyle(Paint.Style.FILL);//不填充
        paint_txt.setTextSize(30.0f);  // 文本大小
        paint_txt.setStrokeWidth(8); //线的宽度
        canvas = new Canvas();  // 画布
    }

    private void initLoadOpenCVLibs() {
        boolean success= OpenCVLoader.initDebug();

        if(success){

            Log.d(TAG,"Load Library successfully......");

        }
    }

    private void readCacheLabelFromLocalFile() {
        fileModel="frozen_inference_graph.pb";
        fileWords="graph.pbtxt";
        String pbpath = getPath("frozen_inference_graph.pb", this);
        String configpath = getPath("graph.pbtxt", this);
        net= Dnn.readNetFromTensorflow(pbpath,configpath);
        load_success=true;
    }

    private static String getPath(String file, Context context) {
        AssetManager assetManager = context.getAssets();
        BufferedInputStream inputStream = null;
        try {
            // Read data from assets.
            inputStream = new BufferedInputStream(assetManager.open(file));
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();
            // Create copy file in storage.
            File outFile = new File(context.getFilesDir(), file);
            FileOutputStream os = new FileOutputStream(outFile);
            os.write(data);
            os.close();
            // Return a path to file which may be read in common way.
            return outFile.getAbsolutePath();
        } catch (IOException ex) {
            Log.i("COCO-NET", "Failed to upload a file");
        }
        return "";
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume: ");
        super.onResume();
        startBackgroundThread();

        // 初始化Detector
        load_success = false;
        if(fileModel !=null && fileWords != null){
            //load_success = BvaNative.detector_init(fileModel, fileWords,device.getValue());
            Log.d(TAG, "onResume: load_success="+load_success);
        }

        // 当屏幕关闭后重新打开, 若SurfaceTexture已经就绪, 此时onSurfaceTextureAvailable不会被回调, 这种情况下
        // 如果SurfaceTexture已经就绪, 则直接打开相机, 否则等待SurfaceTexture已经就绪的回调
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        // 监听屏幕的转动，mRotateDegree有四个值：0/90/180/270,0是平常的竖屏，然后依次顺时针旋转90°得到后三个值
        orientationListener = new OrientationEventListener(this,
                SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {

                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    return;  //手机平放时，检测不到有效的角度
                }

                //可以根据不同角度检测处理，这里只检测四个角度的改变
                // 可以扩展到多于四个的检测，以在不同角度都可以画出完美的框
                // （要在对应的画框处添加多余角度的Canvas的旋转）
                orientation = (orientation + 45) / 90 * 90;
                mRotateDegree = orientation % 360;
                //Log.d(TAG, "mRotateDegree: "+mRotateDegree);
            }
        };

        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable();   // 开启此监听
        } else {
            orientationListener.disable();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: ");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        try {
            // 获得相机开打关闭许可
            mCameraOpenCloseLock.acquire();
            // 关闭捕获会话
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            // 关闭当前相机
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            // 关闭拍照处理器
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            // 释放相机开打关闭许可
            mCameraOpenCloseLock.release();
        }
    }
    private final static int EXECUTION_FREQUENCY = 1;
    private int PREVIEW_RETURN_IMAGE_COUNT;

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            // 设置识别的频率，当EXECUTION_FREQUENCY为5时，也就是此处被回调五次只识别一次
            // 假若帧率被我设置在15帧/s，那么就是 1s 识别 3次，若是30帧/s，那就是1s识别6次，以此类推
            PREVIEW_RETURN_IMAGE_COUNT++;
            if (PREVIEW_RETURN_IMAGE_COUNT % EXECUTION_FREQUENCY != 0) return;
            PREVIEW_RETURN_IMAGE_COUNT = 0;
            final Image image = reader.acquireLatestImage();
            if (load_success) {
                 // 获取最近一帧图像
                mBackgroundHandler.post(new Runnable() {
                    @Override
                    public void run() {
                          Mat input = Yuv.rgb(image);  // 从YUV_420_888 到 Mat(RGB)，这里使用了第三方库，build.gradle中可见
                        final int IN_WIDTH = 300;
                        final int IN_HEIGHT = 300;
                        final float WH_RATIO = (float)IN_WIDTH / IN_HEIGHT;
                        final double IN_SCALE_FACTOR = 0.007843;
                        final double MEAN_VAL = 127.5;
                        final double THRESHOLD = 0.5;
                        // Forward image through network.
                        Mat blob = Dnn.blobFromImage(input, IN_SCALE_FACTOR,
                                new org.opencv.core.Size(IN_WIDTH, IN_HEIGHT),
                                new Scalar(MEAN_VAL, MEAN_VAL, MEAN_VAL), /*swapRB*/false, /*crop*/false);
                        net.setInput(blob);
                        Mat detections = net.forward();
                        int cols = input.cols();
                        int rows = input.rows();
                        detections = detections.reshape(1, (int)detections.total() / 7);
                        ArrayList<int []> results=new ArrayList<>();
                        ArrayList<String> class_label=new ArrayList<>();
                        for (int i = 0; i < detections.rows(); ++i) {
                            double confidence = detections.get(i, 2)[0];
                            if (confidence > THRESHOLD) {
                                int[] loaction=new int[4];
                                int classId = (int)detections.get(i, 1)[0];
                                loaction[0]= (int)(detections.get(i, 3)[0] * cols);//left
                                loaction[1]= (int)(detections.get(i, 4)[0] * rows);//top
                                loaction[2]= (int)(detections.get(i, 5)[0] * cols);//right
                                loaction[3]= (int)(detections.get(i, 6)[0] * rows);//bottom
                                results.add(loaction);
                                String label = classNames[classId] + ": " + confidence;
                                class_label.add(label);
                                Log.d("PCCC",label);
                            }
                        }
                        if(results.toArray().length>0){
                            show_detect_results((String [])class_label.toArray(),(int [][])results.toArray());
                        }
                    }


                });
            }
            image.close();
        }
    };




    /**
     * 接收识别结果进行画框
     * @param ，包含物体名称name、置信度confidence和用于画矩形的参数（x,y,width,height）
     *            name=floats[0]  confidence=floats[1]  x=floats[2] y=floats[3] width=floats[4] height=floats[5]
     */
    private void show_detect_results(final String[] classlabel, final int[][] location) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ClearDraw();   // 先清空上次画的框

                canvas = surfaceHolder.lockCanvas();   // 得到surfaceView的画布
                // 根据屏幕旋转角度调整canvas，以使画框方向正确
                if(mRotateDegree != 0){
                    if(mRotateDegree == 270){
                        canvas.translate(mPreviewSize.getHeight(),0); // 坐标原点在x轴方向移动屏幕宽度的距离
                        canvas.rotate(90);   // canvas顺时针旋转90°
                    } else if(mRotateDegree == 90){
                        canvas.translate(0,mPreviewSize.getWidth());
                        canvas.rotate(-90);
                    } else if(mRotateDegree == 180){
                        canvas.translate(mPreviewSize.getHeight(),mPreviewSize.getWidth());
                        canvas.rotate(180);
                    }
                }
                for (int i=0;i<classlabel.length;i++) {   // 画框并在框上方输出识别结果和置信度
                    canvas.drawRect(location[i][0],location[i][1],location[i][2],location[i][3],paint_rect);
                    canvas.drawText(classlabel[i],location[i][0],location[i][1], paint_txt);
                }
                surfaceHolder.unlockCanvasAndPost(canvas);  // 释放
            }
        });
    }

    /**
     * 清空上次的框
     */
    private void ClearDraw(){
        try{
            canvas = surfaceHolder.lockCanvas(null);
            canvas.drawColor(Color.WHITE);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC);
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            if(canvas != null){
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        load_success = false;
        orientationListener.disable();
        super.onDestroy();
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
