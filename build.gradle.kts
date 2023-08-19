plugins {
    java
    id("org.springframework.boot") version "3.1.2"
    id("io.spring.dependency-management") version "1.1.2"
}

group = "name.heavycarbon"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {

    // https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-data-jdbc
    //  Starter for using Spring Data JDBC
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")

    // https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-jdbc
    // Starter for using JDBC with the HikariCP connection pool
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    // one needs to give the version here for some reason
    implementation("org.jetbrains:annotations:latest.release")

    // -------------

    // https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-test
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter
    testImplementation("org.junit.jupiter:junit-jupiter:latest.release")
    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation("org.assertj:assertj-core:latest.release")
    testImplementation("org.projectlombok:lombok:latest.release")

    // -------------

    compileOnly("org.projectlombok:lombok")

    // -------------

    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // -------------

    // https://mvnrepository.com/artifact/com.h2database/h2
    runtimeOnly("com.h2database:h2")

    // -------------

    developmentOnly("org.springframework.boot:spring-boot-devtools")
}

tasks.withType<JavaCompile> {
    doFirst {
        println("AnnotationProcessorPath for '$name' is ${options.annotationProcessorPath?.joinToString(prefix = "\n", separator = "\n", transform = { it -> it.toString() })}")
    }
}
tasks.withType<Test> {
    useJUnitPlatform()
}
