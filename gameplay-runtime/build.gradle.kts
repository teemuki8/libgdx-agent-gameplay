plugins {
    `java-library`
}

dependencies {
    api(project(":gameplay-core"))
    api(libs.agent.runtime.core)
}
