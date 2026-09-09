import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val sourceSecrets = Properties().apply {
    rootProject.file("source-secrets.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun sourceSecret(name: String): String =
    providers.environmentVariable(name).orNull ?: sourceSecrets.getProperty(name).orEmpty()

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.aurorashelf.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aurorashelf.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 132
        versionName = "2.14.1"

        buildConfigField("String", "PICACG_API_KEY", sourceSecret("PICACG_API_KEY").asBuildConfigString())
        buildConfigField(
            "String",
            "PICACG_SIGNATURE_SECRET",
            sourceSecret("PICACG_SIGNATURE_SECRET").asBuildConfigString(),
        )
        buildConfigField("String", "JM_TOKEN_SECRET", sourceSecret("JM_TOKEN_SECRET").asBuildConfigString())
        buildConfigField("String", "JM_TOKEN_SECRET_2", sourceSecret("JM_TOKEN_SECRET_2").asBuildConfigString())
        buildConfigField("String", "JM_DATA_SECRET", sourceSecret("JM_DATA_SECRET").asBuildConfigString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Internal installable candidate only; replace with the publisher key for distribution.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("dev.chrisbanes.haze:haze:1.7.3")
    implementation("dev.chrisbanes.haze:haze-materials:1.7.3")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
    implementation("androidx.media3:media3-database:1.11.0")
    implementation("androidx.media3:media3-datasource:1.11.0")
    implementation(group = "com.github.InfinityLoop1308.PipePipeExtractor", name = "extractor")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
