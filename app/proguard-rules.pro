-keepattributes *Annotation*
-dontwarn org.jsoup.**

# Keep ViewModel constructors for ViewModelProvider reflection
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep JavascriptInterface methods for WebView reflection
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
