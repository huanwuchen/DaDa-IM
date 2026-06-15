# =====================================================================
# DaDa IM ProGuard / R8 规则
#
# 启用方式：在 app/build.gradle 的 release buildType 中设置
#   minifyEnabled true
#   shrinkResources true
# 后，本文件配合 proguard-android-optimize.txt 一起生效。
#
# 维护原则：
#   - 优先用「精确包名 + 必要 attribute」保留；不要写大锅 -keep class **。
#   - 给闭源 SDK 留出「整包 keep」窗口，避免反射/JNI 找不到类。
#   - 反射/序列化/JNI 入口必须显式保留。
# =====================================================================

# ---------- 通用调试信息 ----------
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
# 在 release 包里把源文件名改写成 SourceFile，便于回溯但不泄露内部路径
-renamesourcefileattribute SourceFile

# Kotlin Metadata（反射 / kotlinx.serialization 之外的库可能依赖）
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---------- 项目数据模型（Gson / Retrofit / Room） ----------
# 网络层 DTO（@SerializedName 反射读取字段）
-keep class com.dada.core.network.model.** { *; }
-keep class com.dada.core.network.websocket.** { *; }
# Room 实体（Room 通过反射 + 生成代码访问字段）
-keep class com.dada.core.database.entity.** { *; }

# ---------- Retrofit / OkHttp / Gson ----------
-dontwarn retrofit2.**
-dontwarn okio.**
-dontwarn okhttp3.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
-keep class sun.misc.Unsafe { *; }

# ---------- Hilt / Dagger ----------
-dontwarn dagger.**
-dontwarn javax.inject.**
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-keep,allowobfuscation @interface dagger.hilt.android.* { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# ---------- AndroidX ----------
-dontwarn androidx.**
-keep class androidx.lifecycle.** { *; }

# ---------- Glide ----------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { **[] $VALUES; public *; }

# ---------- PhotoView ----------
-keep class com.github.chrisbanes.photoview.** { *; }

# ---------- Lottie ----------
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ---------- SmartRefreshLayout ----------
-keep class com.scwang.smart.refresh.** { *; }
-dontwarn com.scwang.smart.refresh.**

# ---------- ZXing ----------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class com.journeyapps.barcodescanner.** { *; }

# ---------- ExoPlayer / Media3 ----------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---------- CameraView (otaliastudios) ----------
-keep class com.otaliastudios.cameraview.** { *; }
-dontwarn com.otaliastudios.cameraview.**

# ---------- 极光推送 JPush / JCore ----------
-keep class cn.jiguang.** { *; }
-keep class cn.jpush.** { *; }
-dontwarn cn.jiguang.**
-dontwarn cn.jpush.**
# 项目自定义的 Receiver / Service（被 manifest 引用，但仍保险声明）
-keep class com.dada.app.push.** { *; }

# ---------- 腾讯 TUICallKit / LiteAVSDK ----------
-keep class com.tencent.** { *; }
-dontwarn com.tencent.**

# ---------- MMKV ----------
-keep class com.tencent.mmkv.** { *; }

# ---------- WebRTC / 通话相关 native 类 ----------
# 与 native 层交互的回调类（CallManager 内的 listener 子类），保留以防反射调用
-keep class com.dada.app.network.call.** { *; }

# ---------- 反射访问的 ViewBinding 静态方法 ----------
-keepclassmembers class * {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static *** bind(android.view.View);
}

# ---------- Activity / Fragment / View 默认保留（Android 框架反射） ----------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment

# ---------- Parcelable / Serializable ----------
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---------- 其他 ----------
# 避免 R8 优化掉 enum values()，被反射/Gson 用到
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
