plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    compileSdk = property("compileSdkVersion") as Int
    defaultConfig {
        applicationId = "com.otaliastudios.cameraview.demo"
        minSdk = property("minSdkVersion") as Int
        targetSdk = property("targetSdkVersion") as Int
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
    }
    sourceSets["main"].java.srcDir("src/main/kotlin")
}

dependencies {
    implementation(project(":cameraview"))
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("com.google.android.material:material:1.4.0")
    implementation ("org.pytorch:pytorch_android_lite:1.9.0")
    implementation ("org.pytorch:pytorch_android_torchvision:1.9.0")
//    implementation("io.github.lucksiege:pictureselector:v3.11.1")
//    implementation("io.github.lucksiege:compress:v3.11.1")
//    implementation ("io.github.lucksiege:camerax:v3.11.1")
    // 图片裁剪 (按需引入)
    implementation("io.github.lucksiege:ucrop:v3.11.1")
}
