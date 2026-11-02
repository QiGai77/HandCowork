plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.xuezhang"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "app.xuezhang"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "0.0.7.7"
        // Google Drive 云同步用的 OAuth 客户端（「桌面应用」类型）。也可以在 App 里「我的 › 云同步 › Google Drive」填写。
        // 编译时传入：./gradlew assembleRelease -PgoogleClientId=xxx.apps.googleusercontent.com -PgoogleClientSecret=GOCSPX-xxx
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"" + (project.findProperty("googleClientId")?.toString() ?: "") + "\"")
        buildConfigField("String", "GOOGLE_CLIENT_SECRET", "\"" + (project.findProperty("googleClientSecret")?.toString() ?: "") + "\"")
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources { excludes += arrayOf("META-INF/*.version", "META-INF/**/LICENSE*", "kotlin/**", "DebugProbesKt.bin") }
    }
    testOptions { unitTests { isIncludeAndroidResources = true; all { it.systemProperty("roborazzi.test.record", "true"); it.maxHeapSize = "3g"; it.systemProperty("robolectric.graphicsMode", "NATIVE"); it.systemProperty("robolectric.pixelCopyRenderMode", "hardware"); it.systemProperty("xz.shots", project.findProperty("shots")?.toString() ?: "false") } } }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

tasks.withType<com.android.build.gradle.internal.tasks.CheckAarMetadataTask> {
    enabled = false
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(listOf("-Xskip-metadata-version-check", "-Xskip-prerelease-check"))
    }
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
    // 翻书卷页（Apache-2.0）https://github.com/oleksandrbalan/pagecurl
    implementation("io.github.oleksandrbalan:pagecurl:1.5.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.46.1")
    testImplementation("androidx.test:core:1.6.1")
}

// 截图测试（Robolectric 原生渲染）只在 -Pshots=true 时跑：./gradlew testDebugUnitTest -Pshots=true --tests '*V0076ShotsTest*'
tasks.withType<Test>().configureEach {
    if (!project.hasProperty("shots")) { exclude("**/V0076ShotsTest*"); exclude("**/V0077ShotsTest*"); exclude("**/V0078ShotsTest*") }
    (project.findProperty("testJava") as String?)?.let { executable = it }
}
