-keepattributes *Annotation*
-dontwarn org.jsoup.**

# Keep ViewModel constructors for ViewModelProvider reflection
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
