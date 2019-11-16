#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <opencv2/opencv.hpp>
#include <android/bitmap.h>
#include <android/log.h>
#include <vector>

#define TAG "JNI_TAG"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG,__VA_ARGS__)

using namespace std;
using namespace cv;
using namespace dnn;

Net net;
vector<String> classname;
const char* classNames[] = {"Background","person",
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

void turn90(int tlx, int tly, int brX, int brY);

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_camera_1pc_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}


extern "C"
JNIEXPORT void JNICALL
Java_com_example_camera_1pc_MainActivity_load2JNI_1SSD(JNIEnv *env, jobject thiz, jstring pbpath,
                                                       jstring configpath) {
    // TODO: implement load2JNI_SSD()
    const char *filePath_1 = env->GetStringUTFChars(pbpath, 0);
    const char *filePath_2 = env->GetStringUTFChars(configpath, 0);
    net=readNetFromTensorflow(filePath_1,filePath_2);
    LOGE("加载分类器文件成功");
    env->ReleaseStringUTFChars(pbpath, filePath_1);
    env->ReleaseStringUTFChars(pbpath, filePath_2);
}
extern "C"
JNIEXPORT jintArray JNICALL
Java_com_example_camera_1pc_MainActivity_SSDdetetct(JNIEnv *env, jobject thiz, jbyteArray yuvdata,
                                                    jint height, jint width,jint degree) {
    // TODO: implement SSDdetetct()
    LOGE("开始0");
    jbyte  *pBuf = env->GetByteArrayElements(yuvdata, 0);
    LOGE("开始1");
    Mat img(height+height/2,width,CV_8UC1,(uchar*)pBuf);
    Mat mRGB(height,width,CV_8UC3);
    LOGE("宽度： %d 高度: %d",width,height);
    cvtColor(img,mRGB,COLOR_YUV2BGR_YV12);
    cvtColor(mRGB,mRGB,COLOR_BGR2RGB);
    if(degree==0){
        transpose(mRGB,mRGB);
    }
    LOGE("成功了1");
    vector<int> location_vec;
    LOGE("分析识别");
    int IN_WIDTH = 300;
    int IN_HEIGHT = 300;
    float WH_RATIO = (float)IN_WIDTH / IN_HEIGHT;
    double IN_SCALE_FACTOR = 0.007843;
    double MEAN_VAL = 127.5;
    double THRESHOLD = 0.6;
    //resize(temp,temp,Size(IN_HEIGHT,IN_WIDTH));
    Mat blob=blobFromImage(mRGB,IN_SCALE_FACTOR,Size(IN_WIDTH,IN_HEIGHT),Scalar(MEAN_VAL,MEAN_VAL,MEAN_VAL),
                           false, false);
    net.setInput(blob);
    Mat detections =net.forward();
    Mat detectionMat(detections.size[2], detections.size[3], CV_32F, detections.ptr<float>());
    for(int i=0;i<detectionMat.rows;i++){
        float confidence = detectionMat.at<float>(i, 2);

        if (confidence > THRESHOLD)
        {
            size_t objectClass = (size_t)(detectionMat.at<float>(i, 1));
            int tl_x = static_cast<int>(detectionMat.at<float>(i, 3) * width);
            int tl_y = static_cast<int>(detectionMat.at<float>(i, 4) * height);
            int br_x = static_cast<int>(detectionMat.at<float>(i, 5) * width);
            int br_y = static_cast<int>(detectionMat.at<float>(i, 6) * height);
            String label = format("%s: %.2f", classNames[objectClass], confidence);
            if(degree==0){
                location_vec.push_back(tl_y);
                location_vec.push_back(width-tl_x);
                location_vec.push_back(br_y);
                location_vec.push_back(width-br_x);
            } else{
                location_vec.push_back(tl_x);
                location_vec.push_back(tl_y);
                location_vec.push_back(br_x);
                location_vec.push_back(br_y);
            }
            classname.push_back(label);
            LOGE("location: %d,%d,%d,%d\n",tl_x,tl_y,br_x,br_y);
            LOGE("label: %s",label.c_str());
            //rectangle(temp, Point(tl_x, tl_y), Point(br_x, br_y), Scalar(255,155,155),3);
            //putText(temp, label, Point(tl_x, tl_y), FONT_HERSHEY_SIMPLEX, 0.5, Scalar(0, 255, 0));
        }
    }
    LOGE("识别结束");
    env->ReleaseByteArrayElements(yuvdata,pBuf,0);
    int vecSize=location_vec.size();
    if (vecSize == 0){
        location_vec.push_back(0);
    }
    jintArray jarr = env->NewIntArray(vecSize);
    //2.获取数组指针
    jint *PCarr = env->GetIntArrayElements(jarr, NULL);
    //3.赋值
    int i = 0;
    for(; i < vecSize; i++){
        PCarr[i] = location_vec.at(i);
    }
    location_vec.clear();
    //4.释放资源
    env->ReleaseIntArrayElements(jarr, PCarr, 0);
    //5.返回数组
    return jarr;
}extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_example_camera_1pc_MainActivity_getlabel(JNIEnv *env, jobject thiz) {
    // TODO: implement getlabel()
    if(classname.size()<=0) return 0;
    int size=classname.size();
    jclass objClass = env->FindClass("java/lang/String");//定义数组中元素类型
    jobjectArray classArray=env->NewObjectArray(size,objClass,0);
    int i = 0;
    for(; i < size; i++){
        jstring pcclassname=env->NewStringUTF(classname.at(i).c_str());
        env->SetObjectArrayElement(classArray,i,pcclassname);
    }
    classname.clear();
    return classArray;
}