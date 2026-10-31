plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.xuezhang"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "app.xuezhang"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "0.0.5"
        androidResources.localeFilters += arrayOf("zh", "en")
    }
    signingConfigs {
        create("rel") {
            storeFile = file("../xuezhang.jks")
            storePassword = "xuezhang"
            keyAlias = "xuezhang"
            keyPassword = "xuezhang"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("rel")
        }
    }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += arrayOf("META-INF/*.version", "META-INF/**/LICENSE*", "kotlin/**", "DebugProbesKt.bin") }
    }
    testOptions { unitTests { isIncludeAndroidResources = true; all { it.systemProperty("roborazzi.test.record", "true"); it.maxHeapSize = "3g"; it.systemProperty("robolectric.graphicsMode", "NATIVE"); it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") } } }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
    implementation("org.jetbrains.compose.ui:ui:1.12.0")
    implementation("org.jetbrains.compose.ui:ui-graphics:1.12.0")
    implementation("org.jetbrains.compose.material:material-ripple:1.12.0")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("ru.noties:jlatexmath-android:0.2.0")
    implementation("ru.noties:jlatexmath-android-font-greek:0.2.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.46.1")
    testImplementation("androidx.test:core:1.6.1")
}
