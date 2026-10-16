import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/* 签名材料两条来源：环境变量（CI 用），或仓库外的一个 keystore.properties（本机用）。
   指针写在 gitignore 掉的 local.properties 里，所以仓库里永远不会有口令。
   环境变量优先，这样 CI 不需要伪造一个本机文件。 */
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val keystoreProperties = Properties().apply {
    val pointer = System.getenv("SHOWCODE_KEYSTORE_PROPERTIES")
        ?: localProperties.getProperty("showcode.keystore.props")
    val f = pointer?.let { file(it) }
    if (f != null && f.exists()) f.inputStream().use { load(it) }
}

fun signingProperty(key: String, envVar: String): String? =
    System.getenv(envVar) ?: keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }

android {
    namespace = "dev.showcode"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.showcode"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            /* 密钥与口令一律不进仓库（仓库是公开的）。两条来源：
               1) CI：设 SHOWCODE_KEYSTORE / SHOWCODE_KEYSTORE_PASSWORD /
                  SHOWCODE_KEY_ALIAS / SHOWCODE_KEY_PASSWORD 四个环境变量；
               2) 本机：在 gitignore 掉的 local.properties 里写一行
                  showcode.keystore.props=<仓库外>\\keystore.properties，
               文件里是 storeFile / storePassword / keyPassword / keyAlias。
               自己签一个：
                 keytool -genkeypair -keystore release.keystore -storetype PKCS12 \
                     -alias showcode -keyalg RSA -keysize 2048 -validity 10000
               两者都没有时 assembleRelease 直接失败，而不是悄悄产出一个装不上的包。 */
            storeFile = signingProperty("storeFile", "SHOWCODE_KEYSTORE")?.let { file(it) }
            storePassword = signingProperty("storePassword", "SHOWCODE_KEYSTORE_PASSWORD")
            keyAlias = signingProperty("keyAlias", "SHOWCODE_KEY_ALIAS")
            keyPassword = signingProperty("keyPassword", "SHOWCODE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        // 折射引擎（GlassLens*）的调试日志挂在 BuildConfig.DEBUG 上
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    /* 底栏那层玻璃。取件码只用到 backdrop，不引 shapes：shapes 是给 lens() 的圆角变体用的，
       本仓库的折射形状只有 CircleShape（CornerBasedShape 系），见 ui/glass/GlassLensCompat。 */
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    /* 折射引擎的 GL 冒烟探针（androidTest）：EGL/着色器在这台设备上起不起来。 */
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")

    testImplementation("junit:junit:4.13.2")
}
