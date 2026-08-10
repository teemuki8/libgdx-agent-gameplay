plugins {
    application
}

application {
    mainClass.set("io.github.teemuki8.libgdx.agent.gameplay.fixture.Launcher")
}

dependencies {
    implementation(project(":gameplay-core"))
    implementation(project(":gameplay-libgdx"))
    implementation(project(":gameplay-runtime"))
    implementation(project(":gameplay-box2d"))
    implementation(libs.gdx.lwjgl3)
    implementation(libs.harness.lwjgl3)
    implementation(libs.harness.mcp)
    implementation(libs.harness.protocol)
    implementation(libs.harness.agent.runtime)
    implementation(libs.markup.core)
    implementation(libs.markup.harness)
    implementation(libs.markup.runtime)
    testImplementation(libs.jackson.databind)
    runtimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
    runtimeOnly(variantOf(libs.gdx.box2d.platform) { classifier("natives-desktop") })
    runtimeOnly(libs.slf4j.nop)
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

base {
    archivesName.set("libgdx-agent-gameplay-fixture")
}
