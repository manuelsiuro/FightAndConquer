plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    // The device-fixture writers (PanelTapFixturesTest, PurchaseMenuFixturesTest) only emit
    // JSON when FC_FIXTURES_OUT is set. Gradle cannot see that env var, so asking for the
    // fixtures again would otherwise be UP-TO-DATE and write nothing.
    outputs.upToDateWhen { System.getenv("FC_FIXTURES_OUT").isNullOrBlank() }
}
