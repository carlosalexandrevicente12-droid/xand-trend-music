plugins { id("com.android.application") }
android {
    namespace = "music.xand.share"
    compileSdk = 35
    defaultConfig {
        applicationId = "music.xand.share"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
dependencies {
    implementation("androidx.media3:media3-transformer:1.7.1")
    implementation("androidx.media3:media3-effect:1.7.1")
    implementation("androidx.media3:media3-common:1.7.1")
}
