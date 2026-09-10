plugins {
    `java-library`
}

dependencies {
    api(project(":gameplay-core"))
    api(libs.gdx.core)
    testImplementation(libs.gdx.lwjgl3)
    constraints {
        listOf("lwjgl", "lwjgl-glfw", "lwjgl-jemalloc", "lwjgl-openal", "lwjgl-opengl", "lwjgl-stb")
            .forEach { testImplementation("org.lwjgl:$it:3.4.3") }
    }
    testRuntimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
}
