plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.showcode"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.showcode"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            /* 密钥不进仓库，全部走环境变量。自己签一个：
                 keytool -genkeypair -keystore showcode.jks -alias showcode \
                     -keyalg RSA -keysize 2048 -validity 10000
               然后设 SHOWCODE_KEYSTORE / SHOWCODE_KEYSTORE_PASSWORD /
               SHOWCODE_KEY_ALIAS / SHOWCODE_KEY_PASSWORD 四个变量。
               没设时 assembleRelease 会失败而不是悄悄产出一个不可安装的包——
               这是故意的：未签名的 release 包对使用者没有意义。 */
            storeFile = System.getenv("SHOWCODE_KEYSTORE")?.let { file(it) }
            storePassword = System.getenv("SHOWCODE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SHOWCODE_KEY_ALIAS")
            keyPassword = System.getenv("SHOWCODE_KEY_PASSWORD")
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
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
