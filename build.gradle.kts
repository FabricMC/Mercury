import java.util.concurrent.Callable

plugins {
    `java-library`
    `maven-publish`
    id("uk.jamierocks.propatcher") version "2.0.1"
    id("org.cadixdev.licenser") version "0.6.1"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

val artifactId = name.toLowerCase()
base.archivesName.set(artifactId)

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

configurations {
    register("jdtSources") {
        isTransitive = false
    }
    register("jdt")
}
configurations["api"].extendsFrom(configurations["jdt"])

repositories {
    mavenCentral()
}

// Update with: ./gradlew dependencies --write-locks
dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

val jdtVersion = "org.eclipse.jdt:org.eclipse.jdt.core:3.40.0"
dependencies {
    // JDT pulls all of these deps in, however they do not specify the exact version to use so they can get updated without us knowing.
    // Depend specifically on these versions to prevent them from being updated under our feet.
    // The POM is also patched later on to as this strict versioning does not make it through.
    "jdt" (jdtVersion)

    // TODO: Split in separate modules
    api("org.cadixdev:at:0.1.0-rc1")
    api("org.cadixdev:lorenz:0.5.7")

    "jdtSources"("$jdtVersion:sources")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.cadixdev:lorenz-io-jam:0.5.7")
}

tasks.withType<Javadoc> {
    exclude("org.cadixdev.$artifactId.jdt.".replace('.', '/'))
}

// Patched ImportRewrite from JDT
patches {
    patches = file("patches")
    rootDir = file("build/jdt/original")
    target = file("build/jdt/patched")
}
val jdtSrcDir = file("jdt")

val extract = task<Copy>("extractJdt") {
    dependsOn(configurations["jdtSources"])
    from(Callable { zipTree(configurations["jdtSources"].singleFile) })
    destinationDir = patches.rootDir

    include("org/eclipse/jdt/core/dom/rewrite/ImportRewrite.java")
    include("org/eclipse/jdt/internal/core/dom/rewrite/imports/*.java")
}
tasks["applyPatches"].inputs.files(extract)
tasks["resetSources"].inputs.files(extract)

tasks["resetSources"].dependsOn(tasks.register("ensureTargetDirectory") {
    doLast {
        patches.target.mkdirs()
    }
})

val renames = listOf(
        "org.eclipse.jdt.core.dom.rewrite" to "org.cadixdev.$artifactId.jdt.rewrite.imports",
        "org.eclipse.jdt.internal.core.dom.rewrite.imports" to "org.cadixdev.$artifactId.jdt.internal.rewrite.imports"
)

fun createRenameTask(prefix: String, inputDir: File, outputDir: File, renames: List<Pair<String, String>>): Task
        = task<Copy>("${prefix}renameJdt") {
    destinationDir = file(outputDir)

    renames.forEach { (old, new) ->
        from("$inputDir/${old.replace('.', '/')}") {
            into("${new.replace('.', '/')}/")
        }
    }

    filter { renames.fold(it) { s, (from, to) -> s.replace(from, to) } }
}

val renameTask = createRenameTask("", patches.target, jdtSrcDir, renames)
renameTask.inputs.files(tasks["applyPatches"])

tasks["makePatches"].inputs.files(createRenameTask("un", jdtSrcDir, patches.target, renames.map { (a,b) -> b to a }))
sourceSets["main"].java.srcDirs(renameTask)

tasks.jar.configure {
    manifest.attributes(mapOf("Automatic-Module-Name" to "org.cadixdev.$artifactId"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.release.set(17)
}

tasks.jar {
    archiveClassifier.set("thin")
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
    configurations = listOf(project.configurations["jdt"])
    relocate("org.apache", "org.cadixdev.mercury.shadow.org.apache")
    relocate("org.eclipse", "org.cadixdev.mercury.shadow.org.eclipse")
    relocate("org.osgi", "org.cadixdev.mercury.shadow.org.osgi")
}
tasks["build"].dependsOn(tasks.shadowJar)

val sourceJar = task<Jar>("sourceJar") {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

val javadocJar = task<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks["javadoc"])
}

artifacts {
    add("archives", sourceJar)
    add("archives", javadocJar)
    add("archives", tasks.shadowJar)
}

license {
    setHeader(file("HEADER"))
    exclude("org.cadixdev.$artifactId.jdt.".replace('.', '/'))
}

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
           from(components["java"])
            artifactId = base.archivesName.get()

            artifact(sourceJar)
            artifact(javadocJar)

            pom {
                val name: String by project
                val description: String by project
                val url: String by project
                name(name)
                description(description)
                url(url)

                scm {
                    url(url)
                    connection("scm:git:$url.git")
                    developerConnection.set(connection)
                }

                issueManagement {
                    system("GitHub Issues")
                    url("$url/issues")
                }

                licenses {
                    license {
                        name("Eclipse Public License, Version 2.0")
                        url("https://www.eclipse.org/legal/epl-2.0/")
                        distribution("repo")
                    }
                }

                developers {
                    developer {
                        id("jamierocks")
                        name("Jamie Mansfield")
                        email("jmansfield@cadixdev.org")
                        url("https://www.jamiemansfield.me/")
                        timezone("Europe/London")
                    }
                }

                withXml {
                    // I pray that im being stupid that this isn't what you have to put up with when using kotlin
                    (((asNode().get("dependencies") as groovy.util.NodeList).first() as groovy.util.Node).value() as groovy.util.NodeList)
                            .removeIf { node ->
                                val group = ((((node as groovy.util.Node).get("groupId") as groovy.util.NodeList).first() as groovy.util.Node).value() as groovy.util.NodeList).first() as String
                                group.startsWith("org.eclipse.")
                            }
                }
            }
        }
    }

    repositories {
        val mavenUrl: String? = System.getenv("MAVEN_URL")

        if (mavenUrl != null) {
            maven(url = mavenUrl) {
                val mavenPass: String? = System.getenv("MAVEN_PASSWORD")
                mavenPass?.let {
                    credentials {
                        username = System.getenv("MAVEN_USERNAME")
                        password = mavenPass
                    }
                }
            }
        }
    }
}

operator fun Property<String>.invoke(v: String) = set(v)
