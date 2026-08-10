plugins {
    `java-library`
}

dependencies {
    api(project(":gameplay-core"))
    api(libs.gdx.box2d)
    api(libs.agent.runtime.box2d)

    testRuntimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
    testRuntimeOnly(variantOf(libs.gdx.box2d.platform) { classifier("natives-desktop") })
}
