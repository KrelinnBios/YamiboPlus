buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            when ("${requested.group}:${requested.name}") {
                "org.apache.commons:commons-compress" -> useVersion("1.28.0")
                "org.bouncycastle:bcprov-jdk18on",
                "org.bouncycastle:bcpkix-jdk18on",
                "org.bouncycastle:bcutil-jdk18on" -> useVersion("1.84")
                "org.bitbucket.b_c:jose4j" -> useVersion("0.9.6")
                "org.jdom:jdom2" -> useVersion("2.0.6.1")
                "com.google.protobuf:protobuf-java",
                "com.google.protobuf:protobuf-kotlin",
                "com.google.protobuf:protobuf-java-util" -> useVersion("3.25.5")
                "io.netty:netty-buffer",
                "io.netty:netty-codec",
                "io.netty:netty-codec-http",
                "io.netty:netty-codec-http2",
                "io.netty:netty-codec-socks",
                "io.netty:netty-common",
                "io.netty:netty-handler",
                "io.netty:netty-handler-proxy",
                "io.netty:netty-resolver",
                "io.netty:netty-transport",
                "io.netty:netty-transport-native-unix-common" -> useVersion("4.1.136.Final")
            }
        }
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
}
