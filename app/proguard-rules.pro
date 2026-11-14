-keepattributes *Annotation*
-dontwarn org.jsoup.**

# Keep NewPipe / PipePipe Extractor and internal models
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**

-keep class com.aurorashelf.app.model.** { *; }
-keep class com.aurorashelf.app.data.** { *; }
-keep class com.aurorashelf.app.game.** { *; }
-keep class com.aurorashelf.app.cast.** { *; }
-keep class com.aurorashelf.app.ui.LiveOrientationBridge { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# Keep ViewModel constructors for ViewModelProvider reflection
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep JavascriptInterface methods for WebView reflection
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
