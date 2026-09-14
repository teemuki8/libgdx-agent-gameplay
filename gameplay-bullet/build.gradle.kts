plugins { `java-library` }
dependencies {
    api(project(":gameplay-core"))
    implementation(libs.gdx.bullet)
    api(libs.agent.runtime.bullet)
    testRuntimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
    testRuntimeOnly(variantOf(libs.gdx.bullet.platform) { classifier("natives-desktop") })
}
