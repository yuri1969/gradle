/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks.compile

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class JavaCompileWithJavaInstallationsIntegrationTest extends AbstractIntegrationSpec {

    def "Java installations know the current VM by default"() {
        settingsFile << "rootProject.name = 'javaInstallationsHasCurrent'"
        buildFile << """
            plugins {
                id "java"
            }
            tasks.named("compileJava") {
                println javaInstallations.current.path
            }
        """

        when:
        succeeds("build")

        then:
        outputContains(System.getProperty("java.home"))
    }

    def "add a Java installation"() {
        def jdk14 = AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_14).first()

        settingsFile << "rootProject.name = 'createJavaInstallation'"
        buildFile << """
            plugins {
                id "java"
            }
            javaInstallations {
                create("jdk14") {
                  path = "${jdk14.javaHome.absolutePath}"
                }
            }
            tasks.named("compileJava") {
                println javaInstallations.jdk14.path
            }
        """

        when:
        succeeds("build")

        then:
        outputContains(jdk14.javaHome.absolutePath)
    }

    def "query toolchain"() {
        def jdk14 = AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_14).first()

        file("src/main/java/Foo.java") << "public class Foo {" +
            "static void howMany(int k) {\n" +
            "    System.out.println(\n" +
            "        switch (k) {\n" +
            "            case  1 -> \"one\";\n" +
            "            case  2 -> \"two\";\n" +
            "            default -> \"many\";\n" +
            "        }\n" +
            "    );\n" +
            "}" +
            "}"

        settingsFile << "rootProject.name = 'createJavaInstallation'"
        buildFile << """
            plugins {
                id "java"
            }
            javaInstallations {
                create("jdk14") {
                  path = "${jdk14.javaHome.absolutePath}"
                }
            }
            java {
                sourceCompatibility = JavaVersion.VERSION_14
                targetCompatibility = JavaVersion.VERSION_14
            }
            tasks.named("compileJava") {
                compiler = providers.provider { javaToolchains.query(new ToolchainRequirements()).get().getJavaCompiler() }
            }
        """

        when:
        succeeds("compileJava")

        then:
        outputContains("")
    }

    @Requires(TestPrecondition.JDK14_OR_EARLIER)
    def "verify matching installation for source compatibility"() {
        settingsFile << "rootProject.name = 'createJavaInstallation'"
        buildFile << """
            plugins {
                id "java"
            }
            java {
                sourceCompatibility = JavaVersion.VERSION_15
            }
        """

        def sourceFile = file("src/main/java/Thing.java")
        sourceFile << """
            public class Thing {
                public Thing() {
                }
            }
        """

        when:
        fails("build")

        then:
        failureCauseContains("requested sourceCompatibility=15 but no matching installaton found")
    }
}
