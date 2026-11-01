plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.zeromq:jeromq:0.6.0")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<JavaCompile> {
	options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<Jar>("jar") {
	enabled = false
}

tasks.register<JavaExec>("demoAdapter") {
	group = "application"
	description = "Publishes a synthetic trading day on ZeroMQ for trying the UI (--args=\"--endpoint ... --speed ...\")"
	classpath = sourceSets["test"].runtimeClasspath
	mainClass = "com.alert_service.demo.DemoAdapter"
	javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.register<Sync>("installDemoAdapter") {
	group = "application"
	description = "Copies the demo adapter and its classpath to build/demo-adapter/lib (for the Docker image)"
	from(sourceSets["test"].runtimeClasspath)
	into(layout.buildDirectory.dir("demo-adapter/lib"))
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
