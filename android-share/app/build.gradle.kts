plugins { id("com.android.application") }
// A ramificação de testes pode gerar um app paralelo, sem substituir o Motorola.
val isolatedTest = providers.gradleProperty("xandIsolatedTest").orNull == "true"
android {
    namespace = "music.xand.share"
    compileSdk = 35
    defaultConfig {
        applicationId = if (isolatedTest) "music.xand.share.test" else "music.xand.share"
        minSdk = 26
        targetSdk = 35
        versionCode = if (isolatedTest) 8 else 7
        versionName = if (isolatedTest) "0.8-validation" else "0.7"
        manifestPlaceholders["xandAppLabel"] = if (isolatedTest) "XAND MUSIC TESTE" else "XAND MUSIC"
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
