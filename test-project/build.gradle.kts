plugins {
    java
    id("org.babelserver.gradle.test-logger") version "1.0.1"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3") // https://central.sonatype.com/artifact/org.junit.jupiter/junit-jupiter/versions
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
