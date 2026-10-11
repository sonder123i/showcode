plugins {
    id("com.android.application") version "9.2.1" apply false
    // 版本必须与 AGP 9.2.1 内置的 Kotlin 一致（AGP 9 起不再单独 apply kotlin.android）。
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
