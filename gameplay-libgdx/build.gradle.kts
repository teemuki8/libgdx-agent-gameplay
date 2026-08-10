plugins {
    `java-library`
}

dependencies {
    api(project(":gameplay-core"))
    api(libs.gdx.core)
    testRuntimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
}
