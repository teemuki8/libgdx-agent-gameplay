plugins {
    `java-library`
}

description = "Qualification-only official Box2D 3 binding spike; never published"

dependencies {
    testImplementation(libs.gdx.box2d3)
    testRuntimeOnly(variantOf(libs.gdx.box2d3.platform) { classifier("natives-desktop") })
}
