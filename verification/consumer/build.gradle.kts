plugins {
    application
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

application {
    mainClass.set("example.ConsumerSmoke")
}

dependencies {
    implementation("io.github.teemuki8:gameplay-core:1.0.0-SNAPSHOT")
    implementation("io.github.teemuki8:gameplay-libgdx:1.0.0-SNAPSHOT")
    implementation("io.github.teemuki8:gameplay-runtime:1.0.0-SNAPSHOT")
    implementation("io.github.teemuki8:gameplay-box2d:1.0.0-SNAPSHOT")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
