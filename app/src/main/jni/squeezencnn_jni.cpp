#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>
#include "net.h"
#include "benchmark.h"
#include <time.h>
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, "libssd", __VA_ARGS__))
static ncnn::UnlockedPoolAllocator g_blob_pool_allocator;
static ncnn::PoolAllocator g_workspace_pool_allocator;
static ncnn::Net squeezenet;
extern "C" {
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "SqueezeNcnn", "JNI_OnLoad");
    ncnn::create_gpu_instance();
    return JNI_VERSION_1_4;
}
JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "SqueezeNcnn", "JNI_OnUnload");
    ncnn::destroy_gpu_instance();
}
// public native boolean Init(AssetManager mgr);
JNIEXPORT jboolean JNICALL Java_com_tencent_squeezencnn_SqueezeNcnn_Init(JNIEnv* env, jobject thiz, jobject assetManager)
{
    ncnn::Option opt;
    opt.lightmode = true;
    opt.num_threads = 8;
    opt.blob_allocator = &g_blob_pool_allocator;
    opt.workspace_allocator = &g_workspace_pool_allocator;
    if (ncnn::get_gpu_count() != 0)
        opt.use_vulkan_compute = true;
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    squeezenet.opt = opt;
    {
        int ret = squeezenet.load_param(mgr, "ncnn.param");
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "SqueezeNcnn", "load_param_bin failed");
            return JNI_FALSE;
        }
    }
    {
        int ret = squeezenet.load_model(mgr, "ncnn.bin");
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "SqueezeNcnn", "load_model failed");
            return JNI_FALSE;
        }
    }
    return JNI_TRUE;
}

// public native String Detect(Bitmap bitmap, boolean use_gpu);
JNIEXPORT jboolean JNICALL Java_com_tencent_squeezencnn_SqueezeNcnn_Detect(JNIEnv* env, jobject thiz, jobject bitmap, jobject bitmapBack, jboolean use_gpu)
{
    if (use_gpu == JNI_TRUE && ncnn::get_gpu_count() == 0)
    {
        return JNI_FALSE;
    }
    double start_time = ncnn::get_current_time();
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    int width = info.width;
    int height = info.height;
//    LOGD("width %d height %d\n", width, height);
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return JNI_FALSE;
    ncnn::Mat in = ncnn::Mat::from_android_bitmap_resize(env, bitmap, ncnn::Mat::PIXEL_BGR, 224, 224);
    ncnn::Mat out_mat = ncnn::Mat::from_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_BGR);
    ncnn::Mat back_mat = ncnn::Mat::from_android_bitmap(env, bitmapBack, ncnn::Mat::PIXEL_BGR);
    const float mean_vals[3] = {128.f, 128.f, 128.f};
    const float std_vals[3] = {0.00392156f, 0.00392156f, 0.00392156f};
    in.substract_mean_normalize(mean_vals, std_vals);
    ncnn::Extractor ex = squeezenet.create_extractor();
    ex.set_vulkan_compute(use_gpu);
    ex.input("input", in);
//    int st, et;
//    double costtime;
//    st = clock();
    ncnn::Mat out;
    ex.extract("output", out);
//    et = clock();
//    costtime = et - st;
//    LOGD("detect cost %fs\n", costtime / CLOCKS_PER_SEC);
    ncnn::Mat channel0 = out.channel(0);
//    LOGD("%d, %d, %d", channel0.w, channel0.h, channel0.c);
//    ncnn::Mat channel1 = out.channel(1);
//    LOGD("ok 1\n");
    double h_scale = height / 224.f;
    double w_scale = width / 224.f;
    for(int i=0;i<height;i++){
        for (int j=0; j<width; j++) {
            int i_scale = int(i / h_scale);
            int j_scale = int(j / w_scale);
//            if(channel0.row(i_scale)[j_scale]>channel1.row(i_scale)[j_scale]){
            if(channel0.row(i)[j]>0.5){
                ((float*)out_mat.data)[0*height*width+i*width+j] = ((float*)back_mat.data)[0*height*width+i*width+j];
                ((float*)out_mat.data)[1*height*width+i*width+j] = ((float*)back_mat.data)[1*height*width+i*width+j];
                ((float*)out_mat.data)[2*height*width+i*width+j] = ((float*)back_mat.data)[2*height*width+i*width+j];
            }
        }
    }
    double elasped = ncnn::get_current_time() - start_time;
    __android_log_print(ANDROID_LOG_DEBUG, "SqueezeNcnn", "%.2fms   detect", elasped);
    out_mat.to_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_BGR);
    return JNI_TRUE;
}

}
