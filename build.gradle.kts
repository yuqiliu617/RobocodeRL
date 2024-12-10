val robocodeHome: String by project
val robotClass: String by project
val enemyClasses: String by project
val battleFile: String by project
val buildBattleFile = "$rootDir/config/battles/$battleFile"
val robocodeBattlesDir = "$robocodeHome/battles/"
val robocodeBattleFile = "$robocodeBattlesDir$battleFile"

plugins {
    kotlin("jvm") version "2.0.21"
}

group = "org.yuqi"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(fileTree("$robocodeHome/libs"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kandy-lets-plot:0.7.0")
}

tasks.register<Jar>("robotJar") {
    group = "robocode"

    archiveBaseName = robotClass
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("robots" to robotClass)
    }

    from(sourceSets["main"].output)
}

tasks.register("registerRobot") {
    dependsOn("robotJar")
    group = "robocode"

    doLast {
        copy {
            from(fileTree("$rootDir/build/libs/").include("**/**/*.jar"))
            into("$robocodeHome/robots/")
        }
    }
}

tasks.register("generateBattleFile") {
    dependsOn("registerRobot")
    group = "robocode"

    val battleRobots = "$enemyClasses, $robotClass $version"

    doFirst {
        copy {
            from(buildBattleFile)
            into(robocodeBattlesDir)
        }
    }

    doLast {
        with(File(robocodeBattleFile)) {
            val content = readText()
            writeText(content.replace("\$selectedRobots\$", battleRobots))
        }
    }
}

tasks.clean {
    group = "robocode"

    doFirst {
        delete(fileTree("$robocodeHome/robots/").matching {
            include("**/$robotClass*.jar")
            include("**/robot.database")
        })
        delete(fileTree(robocodeBattlesDir).matching {
            include("**/$battleFile")
        })
    }
}

tasks.register("startRobocode") {
    dependsOn("clean", "generateBattleFile")
    group = "robocode"

    doLast {
        exec {
            commandLine("$robocodeHome/java2d.bat")
            commandLine("$robocodeHome/set_java_options.bat")
            commandLine(
                "java",
                "-Xmx4096M",
                "-Ddebug=true",
                "-DWORKINGDIRECTORY=$robocodeHome",
                "-Dsun.io.useCanonCaches=false",
                "-Djava.security.manager=allow",
                "--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                "-cp", "$robocodeHome/libs/*", "robocode.Robocode",
                "-battle", robocodeBattleFile
            )
        }
    }
}

tasks.test {
    useJUnitPlatform()
}