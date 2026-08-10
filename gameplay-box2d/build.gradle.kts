plugins {
    `java-library`
}

dependencies {
    api(project(":gameplay-core"))
    api(libs.gdx.box2d)
    api(libs.agent.runtime.box2d)
}
