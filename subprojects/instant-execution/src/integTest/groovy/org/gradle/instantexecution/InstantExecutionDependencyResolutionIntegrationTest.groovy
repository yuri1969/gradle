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

package org.gradle.instantexecution

import org.gradle.integtests.resolve.transform.ArtifactTransformTestFixture
import spock.lang.Ignore
import spock.lang.Issue

class InstantExecutionDependencyResolutionIntegrationTest extends AbstractInstantExecutionIntegrationTest implements ArtifactTransformTestFixture {
    def setup() {
        // So that dependency resolution results from previous executions do not interfere
        requireOwnGradleUserHomeDir()
    }

    def "task input artifact collection can include project dependencies, external dependencies and prebuilt file dependencies"() {
        def fixture = newInstantExecutionFixture()

        taskTypeWithOutputFileProperty()
        taskTypeLogsArtifactCollectionDetails()

        mavenRepo.module("group", "lib1", "6500").publish()

        settingsFile << """
            include 'a', 'b'"""

        buildFile << """
            subprojects {
                configurations { create("default") }
                task producer(type: FileProducer) {
                    content = providers.gradleProperty("\${project.name}Content").orElse("content")
                    output = layout.buildDirectory.file("\${project.name}.out")
                }
                configurations.default.outgoing.artifact(producer.output)
            }
            repositories {
                maven { url = uri('${mavenRepo.uri}') }
            }
            configurations {
                implementation
            }
            dependencies {
                implementation project(':a')
                implementation project(':b')
                implementation "group:lib1:6500"
                implementation files('a.thing')
            }
            task resolve(type: ShowArtifactCollection) {
                collection = configurations.implementation.incoming.artifacts
            }
        """

        given:
        instantRun(":resolve")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("files = [a.thing, a.out, b.out, lib1-6500.jar]")
        outputContains("artifacts = [a.thing, a.out (project :a), b.out (project :b), lib1-6500.jar (group:lib1:6500)]")

        when:
        instantRun(":resolve", "-PaContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("files = [a.thing, a.out, b.out, lib1-6500.jar]")
        outputContains("artifacts = [a.thing, a.out (project :a), b.out (project :b), lib1-6500.jar (group:lib1:6500)]")
    }

    def "task input property can include mapped configuration elements that contain project dependencies"() {
        def fixture = newInstantExecutionFixture()

        taskTypeWithOutputFileProperty()
        taskTypeWithInputListProperty()

        settingsFile << """
            include 'a', 'b'"""

        buildFile << """
            subprojects {
                configurations { create("default") }
                task producer(type: FileProducer) {
                    content = providers.gradleProperty("\${project.name}Content").orElse("0")
                    output = layout.buildDirectory.file("\${project.name}.out")
                }
                configurations.default.outgoing.artifact(producer.output)
            }
            repositories {
                maven { url = uri('${mavenRepo.uri}') }
            }
            configurations {
                implementation
            }
            dependencies {
                implementation project(':a')
                implementation project(':b')
            }
            task resolve(type: InputTask) {
                inValue = configurations.implementation.elements.map { files -> files.collect { it.asFile.text.toInteger() } }
                outFile = file('out.txt')
            }
        """

        given:
        instantRun(":resolve")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        result.assertTaskSkipped(":resolve")
        file('out.txt').text == "10,10"

        when:
        instantRun(":resolve", "-PaContent=2")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        result.assertTaskNotSkipped(":resolve")
        file('out.txt').text == "12,10"
    }

    def "task input file collection can include the output of artifact transform of project dependencies"() {
        def fixture = newInstantExecutionFixture()

        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithSimpleColorTransform()
        buildFile << """
            dependencies.artifactTypes {
                green {
                    attributes.attribute(color, 'green')
                }
            }
            dependencies {
                implementation project(':a')
                implementation files('root.green')
                implementation project(':b')
            }
        """
        file('root.green') << 'root'

        when:
        instantRun(":resolve")

        then:
        assertTransformed("a.jar", "b.jar")
        outputContains("result = [root.green, a.jar.green, b.jar.green]")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed()
        outputContains("result = [root.green, a.jar.green, b.jar.green]")

        when:
        instantRun(":resolve", "-PaContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        assertTransformed("a.jar")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("result = [root.green, a.jar.green, b.jar.green]")
    }

    def "task input artifact collection can include the output of artifact transform of project dependencies"() {
        def fixture = newInstantExecutionFixture()

        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithSimpleColorTransform()
        buildFile << """
            dependencies.artifactTypes {
                green {
                    attributes.attribute(color, 'green')
                }
            }
            dependencies {
                implementation project(':a')
                implementation files('root.green')
                implementation project(':b')
            }
        """
        file('root.green') << 'root'

        when:
        instantRun(":resolveArtifacts")

        then:
        assertTransformed("a.jar", "b.jar")
        outputContains("files = [root.green, a.jar.green, b.jar.green]")
        outputContains("artifacts = [root.green, a.jar.green (project :a), b.jar.green (project :b)]")

        when:
        instantRun(":resolveArtifacts")

        then: // everything up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolveArtifacts")
        result.assertTaskOrder(":b:producer", ":resolveArtifacts")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed()
        outputContains("files = [root.green, a.jar.green, b.jar.green]")
        outputContains("artifacts = [root.green, a.jar.green (project :a), b.jar.green (project :b)]")

        when:
        instantRun(":resolveArtifacts", "-PaContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolveArtifacts")
        result.assertTaskOrder(":b:producer", ":resolveArtifacts")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed("a.jar")
        outputContains("files = [root.green, a.jar.green, b.jar.green]")
        outputContains("artifacts = [root.green, a.jar.green (project :a), b.jar.green (project :b)]")
    }

    def "task input file collection can include the output of artifact transform of external dependencies"() {
        def fixture = newInstantExecutionFixture()

        withColorVariants(mavenRepo.module("group", "thing1", "1.2")).publish()
        withColorVariants(mavenRepo.module("group", "thing2", "1.2")).publish()

        setupBuildWithSimpleColorTransform()
        buildFile << """
            repositories {
                maven {
                    url = uri('${mavenRepo.uri}')
                    metadataSources { gradleMetadata() }
                }
            }
            dependencies {
                implementation "group:thing1:1.2"
                implementation "group:thing2:1.2"
            }
        """

        when:
        instantRun(":resolve")

        then:
        assertTransformed("thing1-1.2.jar", "thing2-1.2.jar")
        outputContains("result = [thing1-1.2.jar.green, thing2-1.2.jar.green]")

        when:
        instantRun(":resolve")

        then:
        fixture.assertStateLoaded()
        assertTransformed()
        outputContains("result = [thing1-1.2.jar.green, thing2-1.2.jar.green]")
    }

    def "task input file collection can include the output of artifact transforms of prebuilt file dependencies"() {
        def fixture = newInstantExecutionFixture()

        settingsFile << """
            include 'a'
        """
        setupBuildWithSimpleColorTransform()
        buildFile << """
            dependencies.artifactTypes {
                blue {
                    attributes.attribute(color, 'blue')
                }
            }
            dependencies {
                implementation files('root.blue')
                implementation project(':a')
            }
            project(':a') {
                dependencies {
                    implementation files('a.blue')
                }
            }
        """
        file('root.blue') << 'root'
        file('a/a.blue') << 'a'

        when:
        instantRun(":resolve")

        then:
        assertTransformed("root.blue", "a.blue", "a.jar")
        outputContains("result = [root.blue.green, a.jar.green, a.blue.green]")

        when:
        instantRun(":resolve")

        then: // everything up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        assertTransformed()
        outputContains("result = [root.blue.green, a.jar.green, a.blue.green]")
    }

    @Issue("https://github.com/gradle/gradle/issues/13200")
    def "task input file collection can include the output of artifact transforms of file dependencies that include task outputs"() {
        def fixture = newInstantExecutionFixture()

        settingsFile << """
            rootProject.name = 'root'
            include 'a'
        """
        setupBuildWithSimpleColorTransform()
        buildFile << """
            allprojects {
                task additionalFile(type: FileProducer) {
                    output = layout.buildDirectory.file("\${project.name}.additional.blue")
                }
            }
            dependencies.artifactTypes {
                blue {
                    attributes.attribute(color, 'blue')
                }
            }
            dependencies {
                implementation files(tasks.additionalFile.output, 'root.blue')
                implementation project(':a')
            }
            project(':a') {
                dependencies {
                    implementation files(tasks.additionalFile.output)
                }
            }
        """
        file('root.blue') << 'root'

        when:
        instantRun(":resolve")

        then:
        assertTransformed("root.blue", "root.additional.blue", "a.blue", "a.jar")
        outputContains("result = [root.additional.blue.green, root.blue.green, a.jar.green, a.additional.blue.green]")

        when:
        instantRun(":resolve")

        then: // everything up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":a:additionalFile", ":resolve")
        result.assertTaskOrder(":additionalFile", ":resolve")
        assertTransformed()
        outputContains("result = [root.additional.blue.green, root.blue.green, a.jar.green, a.additional.blue.green]")
    }

    def "task input file collection can include the output of chained artifact transform of project dependencies"() {
        def fixture = newInstantExecutionFixture()

        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithChainedColorTransform()
        buildFile << """
            dependencies {
                implementation project(':a')
                implementation project(':b')
            }
        """

        when:
        instantRun(":resolve")

        then:
        assertTransformed("a.jar", "a.jar.red", "b.jar", "b.jar.red")
        outputContains("result = [a.jar.red.green, b.jar.red.green]")

        when:
        instantRun(":resolve")

        then: // everything up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed()
        outputContains("result = [a.jar.red.green, b.jar.red.green")

        when:
        instantRun(":resolve", "-PaContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed("a.jar", "a.jar.red")
        outputContains("result = [a.jar.red.green, b.jar.red.green")
    }

    def "task input file collection can include the output of artifact transform of project dependencies which takes the output of another transform as input parameter"() {
        def fixture = newInstantExecutionFixture()

        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorTransformWithAnotherTransformOutputAsInput()
        buildFile << """
            dependencies {
                implementation project(':a')
                implementation project(':b')
                transform project(':a')
            }
        """

        when:
        instantRun(":resolve")

        then:
        output.count("processing") == 3
        outputContains("processing a.jar to make red")
        outputContains("processing a.jar using [a.jar.red]")
        outputContains("processing b.jar using [a.jar.red]")
        outputContains("result = [a.jar.green, b.jar.green]")

        when:
        instantRun(":resolve")

        then: // everything up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        output.count("processing") == 0
        outputContains("result = [a.jar.green, b.jar.green]")

        when:
        instantRun(":resolve", "-PaContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        output.count("processing") == 3
        outputContains("processing a.jar to make red")
        outputContains("processing a.jar using [a.jar.red]")
        outputContains("processing b.jar using [a.jar.red]")
        outputContains("result = [a.jar.green, b.jar.green]")
    }

    def "task input file collection can include output of artifact transform of project dependencies when transform takes upstream artifacts"() {
        def fixture = newInstantExecutionFixture()

        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransformThatTakesUpstreamArtifacts()
        buildFile << """
            dependencies {
                implementation project(':a')
            }
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
        """

        when:
        instantRun(":resolve")

        then:
        output.count("processing") == 3
        outputContains("processing c.jar using []")
        outputContains("processing b.jar using []")
        outputContains("processing a.jar using [b.jar, c.jar]")
        outputContains("result = [a.jar.green, b.jar.green, c.jar.green]")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskOrder(":c:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        result.assertTaskSkipped(":c:producer")
        output.count("processing") == 0
        outputContains("result = [a.jar.green, b.jar.green, c.jar.green]")

        when:
        instantRun(":resolve", "-PbContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskOrder(":c:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskNotSkipped(":b:producer")
        result.assertTaskSkipped(":c:producer")
        output.count("processing") == 2
        outputContains("processing b.jar using []")
        outputContains("processing a.jar using [b.jar, c.jar]")
        outputContains("result = [a.jar.green, b.jar.green, c.jar.green]")
    }

    @Issue("https://github.com/gradle/gradle/issues/13245")
    def "task input file collection can include output of artifact transform of project dependencies when transform takes transformed upstream artifacts"() {
        def fixture = newInstantExecutionFixture()

        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithChainedColorTransformThatTakesUpstreamArtifacts()

        buildFile << """
            dependencies {
                implementation project(':a')
            }
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
        """

        when:
        instantRun(":resolve")

        then:
        output.count("processing") == 6
        outputContains("processing a.jar")
        outputContains("processing b.jar")
        outputContains("processing c.jar")
        outputContains("processing b.jar.red using []")
        outputContains("processing c.jar.red using []")
        outputContains("processing a.jar.red using [b.jar.red, c.jar.red]")
        outputContains("result = [a.jar.red.green, b.jar.red.green, c.jar.red.green]")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskOrder(":c:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        result.assertTaskSkipped(":c:producer")
        output.count("processing") == 0
        outputContains("result = [a.jar.red.green, b.jar.red.green, c.jar.red.green]")

        when:
        instantRun(":resolve", "-PbContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskOrder(":c:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskNotSkipped(":b:producer")
        result.assertTaskSkipped(":c:producer")
        output.count("processing") == 3
        outputContains("processing b.jar")
        outputContains("processing b.jar.red using []")
        outputContains("processing a.jar.red using [b.jar.red, c.jar.red]")
        outputContains("result = [a.jar.red.green, b.jar.red.green, c.jar.red.green]")
    }

    @Ignore("wip")
    def 'transform action is re-executed when input artifact changes'() {
        given:
        buildKotlinFile '''

abstract class Summarize : TransformAction<TransformParameters.None> {

    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        println("Transforming ${inputFile.name}...")
        outputs.file("${inputFile.nameWithoutExtension}-summary.txt").run {
            writeText("${inputFile.name}: ${inputFile.length()}")
        }
    }
}

val summarized = Attribute.of("summarized", Boolean::class.javaObjectType)
dependencies {
    attributesSchema {
        attribute(summarized)
    }
    artifactTypes.create("txt") {
        attributes.attribute(summarized, false)
    }
    registerTransform(Summarize::class) {
        from.attribute(summarized, false)
        to.attribute(summarized, true)
    }
}

val sourceFiles by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
}

val summarizedFiles by configurations.creating {
    extendsFrom(sourceFiles)
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(summarized, true)
    }
}

abstract class CombineSummaries : DefaultTask() {

    @get:InputFiles
    abstract val inputSummaries: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun summarize() {
        outputFile.get().asFile.run {
            parentFile.mkdirs()
            writeText(summaryString())
        }
    }

    private
    fun summaryString() = inputSummaries.files.joinToString(separator = "\\n") { it.readText() }
}

tasks.register<CombineSummaries>("summarize") {
    inputSummaries.from(summarizedFiles)
    outputFile.set(layout.buildDirectory.file("summary.txt"))
}

dependencies {
    sourceFiles(files("input.txt"))
}
'''
        def inputFile = file('input.txt').tap { write("the input file") }
        def outputFile = file('build/summary.txt')
        def expectedOutput = "input.txt: ${inputFile.length()}"
        def instant = newInstantExecutionFixture()

        when:
        instantRun 'summarize'

        then:
        instant.assertStateStored()
        outputFile.text == expectedOutput
        outputContains 'Transforming input.txt...'
        result.assertTaskExecuted ':summarize'

        when: 'input file changes'
        inputFile.text = inputFile.text.reverse()
        instantRun 'summarize'

        then:
        instant.assertStateLoaded()
        outputFile.text == expectedOutput
        outputContains 'Transforming input.txt...'
        result.assertTaskExecuted ':summarize'
    }
}
