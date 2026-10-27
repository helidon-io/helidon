/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.service.tests.gradle.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class GradlePluginTest {
    private Path directory;

    @BeforeEach
    void createProjectDirectory() throws IOException {
        directory = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "gradle-test-");
    }

    @AfterEach
    void stopFixtureDaemons() throws Exception {
        GradleFixtureSupport.stopDaemons(directory);
    }

    @Test
    void packagedApplicationAndTestBindings() throws IOException {
        fixture();
        BuildResult first = runner("test", "packagedRun", "generatedRun", "--configuration-cache").build();
        assertThat(first.getOutput(), containsString("APPLICATION:chosen:library"));
        assertThat(first.getOutput(), containsString("GENERATED:chosen:library"));
        assertTask(first, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(first, ":generateTestApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(first, ":test", TaskOutcome.SUCCESS);
        assertThat(Files.readString(directory.resolve("build/test-results/test/TEST-example.BindingTest.xml")),
                   containsString("tests=\"1\""));

        String binding = Files.readString(directory.resolve(
                "build/generated/sources/helidon/main/example/ApplicationBinding.java"));
        assertThat(binding, containsString(".bind("));
        assertThat(binding, containsString("Greeting__ServiceDescriptor"));
        assertThat(binding, containsString("LibraryService__ServiceDescriptor"));
        assertThat(binding, not(containsString("TestOnly__ServiceDescriptor")));
        String testBinding = Files.readString(directory.resolve(
                "build/generated/sources/helidon/test/example/TestApplicationBinding.java"));
        assertThat(testBinding, containsString("TestOnly__ServiceDescriptor"));
        assertThat(testBinding, containsString("Greeting__ServiceDescriptor"));

        try (var jar = new ZipFile(directory.resolve("build/libs/application.jar").toFile())) {
            assertThat(jar.stream().filter(entry -> entry.getName().equals("example/ApplicationBinding.class")).count(),
                       is(1L));
            assertThat(jar.getEntry("example/ApplicationMain.class"), notNullValue());
            assertThat(jar.getEntry("example/Greeting__ServiceDescriptor.class"), notNullValue());
            assertThat(jar.getEntry("example/TestApplicationBinding.class"), nullValue());
            assertThat(jar.getEntry("example/TestOnly.class"), nullValue());
        }

        BuildResult second = runner("test", "packagedRun", "generatedRun", "--configuration-cache").build();
        assertThat(second.getOutput(), containsString("Reusing configuration cache"));
        assertTask(second, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(second, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(second, ":compileTestJava", TaskOutcome.UP_TO_DATE);
        assertTask(second, ":generateTestApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(second, ":jar", TaskOutcome.UP_TO_DATE);
        assertTask(second, ":test", TaskOutcome.UP_TO_DATE);
    }

    @Test
    void namedBindingTaskAcceptsAdditionalCompilerInputs() throws IOException {
        fixture();
        write("build.gradle", "import io.helidon.service.gradle.plugin.GenerateBindings\n"
                + Files.readString(directory.resolve("build.gradle")) + """

                def compileExtra = tasks.register('compileExtra', JavaCompile) {
                    dependsOn tasks.named('compileJava')
                    source = fileTree('src/extra/java')
                    classpath = sourceSets.main.compileClasspath + files(
                            tasks.named('compileJava', JavaCompile).flatMap { it.destinationDirectory })
                    options.annotationProcessorPath = configurations.annotationProcessor
                    destinationDirectory = layout.buildDirectory.dir('classes/extra/main')
                }
                tasks.named('generateApplicationBindings', GenerateBindings) {
                    dependsOn compileExtra
                    classpath.from(compileExtra.flatMap { it.destinationDirectory })
                    sourceRoots.from(file('src/extra/java'))
                }
                tasks.named('packagedRun', JavaExec) {
                    classpath += files(compileExtra.flatMap { it.destinationDirectory })
                }
                """);
        write("src/extra/java/extra/ExtraService.java", """
                package extra;
                import example.Greeting;
                import io.helidon.service.registry.Service;
                @Service.Singleton
                public class ExtraService {
                    private final Greeting greeting;
                    @Service.Inject
                    public ExtraService(@Service.Named("chosen") Greeting greeting) { this.greeting = greeting; }
                    public String value() { return greeting.value(); }
                }
                """);
        write("src/main/java/example/Main.java", Files.readString(directory.resolve("src/main/java/example/Main.java"))
                .replace("main(String[] args) {", "main(String[] args) throws Exception {")
                .replace("manager.shutdown();", """
                        Object extra = manager.registry().get(Class.forName("extra.ExtraService"));
                        System.out.println("EXTRA:" + extra.getClass().getMethod("value").invoke(extra));
                        manager.shutdown();
                        """));

        BuildResult result = runner("packagedRun", "--configuration-cache").build();
        assertTask(result, ":compileJava", TaskOutcome.SUCCESS);
        assertTask(result, ":compileExtra", TaskOutcome.SUCCESS);
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertThat(result.getOutput(), containsString("APPLICATION:chosen:library"));
        assertThat(result.getOutput(), containsString("EXTRA:chosen:library"));
        String binding = Files.readString(directory.resolve(
                "build/generated/sources/helidon/main/example/ApplicationBinding.java"));
        assertThat(binding, containsString("Greeting__ServiceDescriptor"));
        assertThat(binding, containsString("LibraryService__ServiceDescriptor"));
        assertThat(binding, containsString("ExtraService__ServiceDescriptor"));
        assertThat("The extra compiler output remains separate",
                   Files.exists(directory.resolve("build/classes/helidon/main/extra/ExtraService.class")), is(false));

        BuildResult repeated = runner("packagedRun", "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertThat(repeated.getOutput(), containsString("EXTRA:chosen:library"));
        assertTask(repeated, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":compileExtra", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);
    }

    @Test
    void registeredBindingTaskBuildsIndependentArchiveDirectly() throws IOException {
        fixture();
        write("build.gradle", "import io.helidon.service.gradle.plugin.GenerateBindings\n"
                + Files.readString(directory.resolve("build.gradle")) + """

                sourceSets { other }
                configurations {
                    otherImplementation.extendsFrom implementation
                    otherAnnotationProcessor.extendsFrom annotationProcessor
                }
                def compileOther = tasks.named(sourceSets.other.compileJavaTaskName, JavaCompile)
                def generateOtherBinding = tasks.register('generateOtherBinding', GenerateBindings) {
                    dependsOn compileOther
                    classpath.from(compileOther.flatMap { it.destinationDirectory }, configurations.otherRuntimeClasspath)
                    sourceRoots.from(sourceSets.other.allJava.sourceDirectories)
                    options.bindingClassName = 'OtherBinding'
                    options.generateMain = true
                    options.mainClassName = 'OtherMain'
                    prependClassesTo(sourceSets.other)
                }
                tasks.named(sourceSets.other.classesTaskName) {
                    dependsOn generateOtherBinding
                }
                def otherJar = tasks.register('otherJar', Jar) { jar ->
                    dependsOn generateOtherBinding
                    archiveClassifier = 'other'
                    from(sourceSets.other.output)
                    generateOtherBinding.get().addExclusionsTo(jar)
                }
                tasks.register('otherRun', JavaExec) {
                    dependsOn otherJar
                    classpath = files(otherJar.flatMap { it.archiveFile }) + configurations.otherRuntimeClasspath
                    mainClass = 'other.Main'
                }
                """);
        write("src/other/java/other/OtherService.java", """
                package other;
                import io.helidon.service.registry.Service;
                @Service.Singleton
                public class OtherService {
                    public String value() { return "independent"; }
                }
                """);
        write("src/other/java/other/Main.java", """
                package other;
                import io.helidon.service.registry.EmptyBinding;
                import io.helidon.service.registry.Service;
                import io.helidon.service.registry.ServiceRegistryManager;
                @Service.GenerateBinding("OtherBinding")
                public class Main {
                    public static void main(String[] args) {
                        var binding = OtherBinding.create();
                        if (binding instanceof EmptyBinding) {
                            throw new AssertionError("Other binding placeholder was not replaced");
                        }
                        var manager = ServiceRegistryManager.start(binding);
                        try {
                            System.out.println("OTHER:" + manager.registry().get(OtherService.class).value());
                        } finally {
                            manager.shutdown();
                        }
                    }
                }
                """);

        BuildResult archive = runner("otherJar", "--configuration-cache").build();
        assertTask(archive, ":compileOtherJava", TaskOutcome.SUCCESS);
        assertTask(archive, ":generateOtherBinding", TaskOutcome.SUCCESS);
        assertTask(archive, ":otherJar", TaskOutcome.SUCCESS);
        assertThat("An independent archive must not build the default binding",
                   archive.task(":generateApplicationBindings"), nullValue());
        assertThat("An independent archive must not compile the main source set",
                   archive.task(":compileJava"), nullValue());
        Path rawBinding = directory.resolve("build/classes/java/other/other/OtherBinding.class");
        Path generatedBinding = directory.resolve("build/classes/helidon/generateOtherBinding/other/OtherBinding.class");
        assertThat("Standalone task defaults must put generated output under its own task name",
                   Files.exists(generatedBinding), is(true));
        assertThat("The compiler's placeholder must remain in its original output", Files.exists(rawBinding), is(true));
        assertThat("Standalone generation must complete the binding", Files.mismatch(rawBinding, generatedBinding), not(is(-1L)));
        String binding = Files.readString(directory.resolve(
                "build/generated/sources/helidon/generateOtherBinding/other/OtherBinding.java"));
        assertThat(binding, containsString("OtherService__ServiceDescriptor"));
        assertThat(binding, not(containsString("Greeting__ServiceDescriptor")));
        assertThat("Standalone output must not overlap the plugin-created application output",
                   Files.exists(directory.resolve("build/classes/helidon/main")), is(false));
        try (var jar = new ZipFile(directory.resolve("build/libs/application-other.jar").toFile())) {
            assertThat(jar.stream().filter(entry -> entry.getName().equals("other/OtherBinding.class")).count(), is(1L));
            assertThat(jar.getInputStream(jar.getEntry("other/OtherBinding.class")).readAllBytes(),
                       is(Files.readAllBytes(generatedBinding)));
            assertThat(jar.getEntry("other/OtherMain.class"), notNullValue());
            assertThat(jar.getEntry("other/OtherService.class"), notNullValue());
            assertThat(jar.getEntry("example/Greeting.class"), nullValue());
        }
        BuildResult repeated = runner("otherJar", "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertTask(repeated, ":compileOtherJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateOtherBinding", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":otherJar", TaskOutcome.UP_TO_DATE);

        BuildResult run = runner("otherRun", "--configuration-cache").build();
        assertThat(run.getOutput(), containsString("OTHER:independent"));
        assertTask(run, ":generateOtherBinding", TaskOutcome.UP_TO_DATE);
    }

    @Test
    void unrelatedTaskDoesNotRealizeBindingTasks() throws IOException {
        fixture();
        write("build.gradle", "import io.helidon.service.gradle.plugin.GenerateBindings\n"
                + Files.readString(directory.resolve("build.gradle")) + """

                tasks.withType(GenerateBindings).configureEach {
                    throw new GradleException('Binding tasks must stay lazy when running help')
                }
                """);

        BuildResult result = runner("help").build();
        assertTask(result, ":help", TaskOutcome.SUCCESS);
        assertThat(result.task(":generateApplicationBindings"), nullValue());
        assertThat(result.task(":generateTestApplicationBindings"), nullValue());
    }

    @Test
    void preservesCompilerOutputsAndFiltersOnlyShadowedArchiveEntries() throws IOException {
        fixture();
        write("build.gradle", Files.readString(directory.resolve("build.gradle")) + """

                tasks.register('rawClassesJar', Jar) {
                    archiveClassifier = 'raw'
                    from(tasks.named('compileJava', JavaCompile).flatMap { it.destinationDirectory })
                    mustRunAfter tasks.named('generateApplicationBindings')
                    outputs.upToDateWhen { false }
                }
                """);

        BuildResult raw = runner("rawClassesJar").build();
        assertTask(raw, ":rawClassesJar", TaskOutcome.SUCCESS);
        assertThat("An archive of compiler output must not require binding generation",
                   raw.task(":generateApplicationBindings"), nullValue());
        Path rawBinding = directory.resolve("build/classes/java/main/example/ApplicationBinding.class");
        byte[] placeholder = Files.readAllBytes(rawBinding);

        BuildResult result = runner("test", "packagedRun", "generatedRun", "rawClassesJar", "--configuration-cache").build();
        assertThat(result.getOutput(), containsString("APPLICATION:chosen:library"));
        assertThat(result.getOutput(), containsString("GENERATED:chosen:library"));
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":generateTestApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":test", TaskOutcome.SUCCESS);
        assertTask(result, ":rawClassesJar", TaskOutcome.SUCCESS);
        assertThat("Binding generation must leave the compiler's placeholder unchanged",
                   Files.readAllBytes(rawBinding), is(placeholder));
        for (String ordinaryClass : List.of("Main.class", "Greeting.class", "Greeting__ServiceDescriptor.class")) {
            assertThat("Compiler output must retain " + ordinaryClass,
                       Files.exists(directory.resolve("build/classes/java/main/example/" + ordinaryClass)), is(true));
            assertThat("Generated output must not republish " + ordinaryClass,
                       Files.exists(directory.resolve("build/classes/helidon/main/example/" + ordinaryClass)), is(false));
        }
        for (String ordinaryClass : List.of("BindingTest.class", "TestOnly.class", "TestOnly__ServiceDescriptor.class")) {
            assertThat("Generated test output must not republish " + ordinaryClass,
                       Files.exists(directory.resolve("build/classes/helidon/test/example/" + ordinaryClass)), is(false));
        }
        String metadata = "META-INF/helidon/unnamed/example/service-registry.json";
        assertThat("Compiler metadata must stay in its producer's output",
                   Files.exists(directory.resolve("build/classes/java/main/" + metadata)), is(true));
        assertThat("Generated output must not duplicate compiler metadata",
                   Files.exists(directory.resolve("build/classes/helidon/main/" + metadata)), is(false));
        Path generatedBinding = directory.resolve("build/classes/helidon/main/example/ApplicationBinding.class");
        byte[] completed = Files.readAllBytes(generatedBinding);
        assertThat("Generated binding must differ from the annotation processor placeholder",
                   completed, not(is(placeholder)));
        try (var jar = new ZipFile(directory.resolve("build/libs/application.jar").toFile())) {
            assertThat("Application archive must contain exactly one completed binding",
                       jar.stream().filter(entry -> entry.getName().equals("example/ApplicationBinding.class")).count(),
                       is(1L));
            assertThat("Application archive must retain the generated binding",
                       jar.getInputStream(jar.getEntry("example/ApplicationBinding.class")).readAllBytes(), is(completed));
            assertThat("Application archive must retain ordinary compiler output",
                       jar.getEntry("example/Greeting.class"), notNullValue());
        }
        try (var jar = new ZipFile(directory.resolve("build/libs/application-raw.jar").toFile())) {
            assertThat("An archive of compiler output must retain its raw placeholder after generation",
                       jar.getEntry("example/ApplicationBinding.class"), notNullValue());
            assertThat("Binding exclusions must not affect an archive without the generated layer",
                       jar.getInputStream(jar.getEntry("example/ApplicationBinding.class")).readAllBytes(), is(placeholder));
        }

        write("build.gradle", Files.readString(directory.resolve("build.gradle")) + """

                tasks.named('jar') {
                    into 'application'
                    eachFile { path = 'relocated/' + path }
                }
                """);
        BuildResult relocated = runner("jar", "--configuration-cache").build();
        assertTask(relocated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(relocated, ":jar", TaskOutcome.SUCCESS);
        String archiveBinding = "relocated/application/example/ApplicationBinding.class";
        try (var jar = new ZipFile(directory.resolve("build/libs/application.jar").toFile())) {
            assertThat("Relocated application archive must contain exactly one completed binding",
                       jar.stream().filter(entry -> entry.getName().equals(archiveBinding)).count(), is(1L));
            assertThat("Archive relocation must retain the generated binding instead of its raw placeholder",
                       jar.getInputStream(jar.getEntry(archiveBinding)).readAllBytes(), is(completed));
            assertThat("Archive relocation must retain ordinary compiler output",
                       jar.getEntry("relocated/application/example/Greeting.class"), notNullValue());
        }
        BuildResult repeated = runner("jar", "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);
    }

    @Test
    void filtersBindingPlaceholderFromAdditionalCompilerOutput() throws IOException {
        fixture();
        write("src/main/java/example/Main.java", Files.readString(directory.resolve("src/main/java/example/Main.java"))
                .replace("@Service.GenerateBinding\n", ""));
        write("src/extra/java/example/BindingAnchor.java", """
                package example;
                import io.helidon.service.registry.Service;
                @Service.GenerateBinding("ApplicationBinding")
                public class BindingAnchor { }
                """);
        write("build.gradle", Files.readString(directory.resolve("build.gradle")) + """

                def compileExtra = tasks.register('compileExtraBinding', JavaCompile) {
                    source = fileTree('src/extra/java')
                    classpath = sourceSets.main.compileClasspath
                    options.annotationProcessorPath = configurations.annotationProcessor
                    destinationDirectory = layout.buildDirectory.dir('classes/extra/main')
                }
                sourceSets.main.output.classesDirs.from(compileExtra.flatMap { it.destinationDirectory })
                tasks.named('compileJava', JavaCompile) {
                    dependsOn compileExtra
                    classpath += files(compileExtra.flatMap { it.destinationDirectory })
                }
                """);

        BuildResult compiled = runner("compileJava").build();
        assertTask(compiled, ":compileExtraBinding", TaskOutcome.SUCCESS);
        assertTask(compiled, ":compileJava", TaskOutcome.SUCCESS);
        Path rawBinding = directory.resolve("build/classes/extra/main/example/ApplicationBinding.class");
        byte[] placeholder = Files.readAllBytes(rawBinding);
        assertThat("The binding placeholder must come only from the additional compiler",
                   Files.exists(directory.resolve("build/classes/java/main/example/ApplicationBinding.class")), is(false));

        BuildResult result = runner("jar", "packagedRun", "--configuration-cache").build();
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":jar", TaskOutcome.SUCCESS);
        assertThat(result.getOutput(), containsString("APPLICATION:chosen:library"));
        assertThat("Generation must leave the additional compiler's placeholder unchanged",
                   Files.readAllBytes(rawBinding), is(placeholder));
        byte[] completed = Files.readAllBytes(directory.resolve("build/classes/helidon/main/example/ApplicationBinding.class"));
        assertThat("The completed binding must replace the additional compiler's placeholder", completed, not(is(placeholder)));
        try (var jar = new ZipFile(directory.resolve("build/libs/application.jar").toFile())) {
            assertThat("The standard archive must contain exactly one binding",
                       jar.stream().filter(entry -> entry.getName().equals("example/ApplicationBinding.class")).count(), is(1L));
            assertThat("The standard archive must retain the completed binding",
                       jar.getInputStream(jar.getEntry("example/ApplicationBinding.class")).readAllBytes(), is(completed));
            assertThat("The additional compiler's ordinary output must remain in the archive",
                       jar.getEntry("example/BindingAnchor.class"), notNullValue());
        }

        BuildResult repeated = runner("jar", "packagedRun", "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertThat(repeated.getOutput(), containsString("APPLICATION:chosen:library"));
        assertTask(repeated, ":compileExtraBinding", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);
    }

    @Test
    void generatedOnlyRelocationPreservesRawPlaceholder() throws IOException {
        generatedOnlyArchiveRule(false);
    }

    @Test
    void generatedOnlyExclusionPreservesRawPlaceholder() throws IOException {
        generatedOnlyArchiveRule(true);
    }

    @Test
    void testBindingsCanBeGeneratedWithoutApplicationBindings() throws IOException {
        fixture();
        write("build.gradle", Files.readString(directory.resolve("build.gradle")) + """

                helidonService {
                    generateBinding = false
                    generateMain = false
                }
                """);

        BuildResult result = runner("test").build();
        assertTask(result, ":generateTestApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":test", TaskOutcome.SUCCESS);
        assertThat(Files.readString(directory.resolve("build/test-results/test/TEST-example.BindingTest.xml")),
                   containsString("tests=\"1\""));
        assertThat(Files.exists(directory.resolve("build/generated/sources/helidon/main/example/ApplicationBinding.java")),
                   is(false));
        String testBinding = Files.readString(directory.resolve(
                "build/generated/sources/helidon/test/example/TestApplicationBinding.java"));
        assertThat(testBinding, containsString("TestOnly__ServiceDescriptor"));
        assertThat(testBinding, containsString("Greeting__ServiceDescriptor"));
        assertThat(testBinding, containsString("LibraryService__ServiceDescriptor"));
    }

    @Test
    void preservesClassOutputsRegisteredBeforeHelidonPlugin() throws IOException {
        extraClassOutputs(true);
    }

    @Test
    void preservesClassOutputsRegisteredAfterHelidonPlugin() throws IOException {
        extraClassOutputs(false);
    }

    @Test
    void supportsGroovyPluginAppliedBeforeHelidonPlugin() throws IOException {
        groovyClassOutputs(true);
    }

    @Test
    void supportsGroovyPluginAppliedAfterHelidonPlugin() throws IOException {
        groovyClassOutputs(false);
    }

    @Test
    void preservesRealizedCompilerDestinationConfiguredBeforePlugin() throws IOException {
        customClassDirectories(true, false);
    }

    @Test
    void preservesCompilerDestinationProviderConfiguredBeforePlugin() throws IOException {
        customClassDirectories(true, true);
    }

    @Test
    void preservesCompilerDestinationConfiguredAfterPlugin() throws IOException {
        customClassDirectories(false, true);
    }

    @Test
    void preservesMetadataFromMultipleClassOutputs() throws IOException {
        fixture();
        write("build.gradle", Files.readString(directory.resolve("build.gradle")) + """

                def extraCompile = tasks.register('compileExtraServices', JavaCompile) {
                    source = fileTree('src/extra/java')
                    classpath = sourceSets.main.compileClasspath
                    options.annotationProcessorPath = configurations.annotationProcessor
                    destinationDirectory = layout.buildDirectory.dir('classes/extra/main')
                }
                sourceSets.main.output.classesDirs.from(extraCompile.flatMap { it.destinationDirectory })
                tasks.register('splitServicesRun', JavaExec) {
                    dependsOn tasks.named('classes')
                    classpath = sourceSets.main.runtimeClasspath
                    mainClass = 'example.Main'
                }
                """);
        write("src/extra/java/example/ExtraService.java", """
                package example;
                import io.helidon.service.registry.Service;
                @Service.Singleton
                public class ExtraService {
                    public String value() { return "extra"; }
                }
                """);
        write("src/extra/java/example/RemovedService.java", """
                package example;
                import io.helidon.service.registry.Service;
                @Service.Singleton
                public class RemovedService { }
                """);
        write("src/main/java/example/MainConfig.java", """
                package example;
                import io.helidon.config.metadata.Configured;
                import io.helidon.config.metadata.ConfiguredOption;
                @Configured(ignoreBuildMethod = true)
                public class MainConfig {
                    @ConfiguredOption
                    public void mainOption(String value) { }
                }
                """);
        write("src/extra/java/example/ExtraConfig.java", """
                package example;
                import io.helidon.config.metadata.Configured;
                import io.helidon.config.metadata.ConfiguredOption;
                @Configured(ignoreBuildMethod = true)
                public class ExtraConfig {
                    @ConfiguredOption
                    public void extraOption(String value) { }
                }
                """);
        write("src/main/java/example/Main.java", Files.readString(directory.resolve("src/main/java/example/Main.java"))
                .replace("main(String[] args) {", "main(String[] args) throws Exception {")
                .replace("manager.shutdown();", """
                        Class<?> extraType = Class.forName("example.ExtraService");
                        Object extra = manager.registry().get(extraType);
                        System.out.println("EXTRA:" + extraType.getMethod("value").invoke(extra));
                        manager.shutdown();
                        """));

        BuildResult first = runner("classes", "splitServicesRun").build();
        assertTask(first, ":compileJava", TaskOutcome.SUCCESS);
        assertTask(first, ":compileExtraServices", TaskOutcome.SUCCESS);
        assertTask(first, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(first, ":splitServicesRun", TaskOutcome.SUCCESS);
        assertThat(first.getOutput(), containsString("APPLICATION:chosen:library"));
        assertThat(first.getOutput(), containsString("EXTRA:extra"));
        String metadata = "META-INF/helidon/unnamed/example/service-registry.json";
        assertThat("Default compiler must contribute service metadata",
                   Files.exists(directory.resolve("build/classes/java/main/" + metadata)), is(true));
        assertThat("Extra compiler must contribute service metadata at the same path",
                   Files.exists(directory.resolve("build/classes/extra/main/" + metadata)), is(true));
        String configMetadata = "META-INF/helidon/config-metadata.json";
        String mainMetadata = Files.readString(directory.resolve("build/classes/java/main/" + configMetadata));
        String extraMetadata = Files.readString(directory.resolve("build/classes/extra/main/" + configMetadata));
        assertThat(mainMetadata, containsString("MainConfig"));
        assertThat(mainMetadata, not(containsString("ExtraConfig")));
        assertThat(extraMetadata, containsString("ExtraConfig"));
        assertThat(extraMetadata, not(containsString("MainConfig")));
        assertThat("Generated output must not duplicate compiler configuration metadata",
                   Files.exists(directory.resolve("build/classes/helidon/main/" + configMetadata)), is(false));
        assertThat("Extra classes must remain in their producer's output",
                   Files.exists(directory.resolve("build/classes/helidon/main/example/ExtraService.class")), is(false));
        Path binding = directory.resolve("build/generated/sources/helidon/main/example/ApplicationBinding.java");
        String firstBinding = Files.readString(binding);
        assertThat(firstBinding, containsString("Greeting__ServiceDescriptor"));
        assertThat(firstBinding, containsString("ExtraService__ServiceDescriptor"));
        assertThat(firstBinding, containsString("RemovedService__ServiceDescriptor"));

        Files.delete(directory.resolve("src/extra/java/example/RemovedService.java"));
        BuildResult changed = runner("classes", "splitServicesRun").build();
        assertTask(changed, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(changed, ":compileExtraServices", TaskOutcome.SUCCESS);
        assertTask(changed, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertThat(changed.getOutput(), containsString("APPLICATION:chosen:library"));
        assertThat(changed.getOutput(), containsString("EXTRA:extra"));
        String changedBinding = Files.readString(binding);
        assertThat(changedBinding, containsString("Greeting__ServiceDescriptor"));
        assertThat(changedBinding, containsString("ExtraService__ServiceDescriptor"));
        assertThat(changedBinding, not(containsString("RemovedService__ServiceDescriptor")));

        BuildResult repeated = runner("classes", "splitServicesRun").build();
        assertTask(repeated, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":compileExtraServices", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertThat(repeated.getOutput(), containsString("EXTRA:extra"));
    }

    @Test
    void preservesServiceOutputsRegisteredByLaterCallback() throws IOException {
        fixture();
        write("build.gradle", Files.readString(directory.resolve("build.gradle")) + """

                def lateCompile = tasks.register('compileLateServices', JavaCompile) {
                    source = fileTree('src/late/java')
                    classpath = sourceSets.main.compileClasspath
                    options.annotationProcessorPath = configurations.annotationProcessor
                    destinationDirectory = layout.buildDirectory.dir('classes/late/main')
                }
                def lateTestCompile = tasks.register('compileLateTestServices', JavaCompile) {
                    source = fileTree('src/lateTest/java')
                    classpath = sourceSets.test.compileClasspath + files(tasks.named('compileTestJava')
                            .flatMap { it.destinationDirectory })
                    options.annotationProcessorPath = configurations.testAnnotationProcessor
                    destinationDirectory = layout.buildDirectory.dir('classes/late/test')
                }
                afterEvaluate {
                    sourceSets.main.output.classesDirs.from(lateCompile.flatMap { it.destinationDirectory })
                    sourceSets.test.output.classesDirs.from(lateTestCompile.flatMap { it.destinationDirectory })
                }
                tasks.register('rawLateJar', Jar) {
                    archiveClassifier = 'raw-late'
                    from(lateCompile.flatMap { it.destinationDirectory })
                }
                tasks.register('prefixedOutputJar', Jar) {
                    archiveClassifier = 'prefixed-output'
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    into('nested') { from(sourceSets.main.output) }
                }
                tasks.named('jar') {
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                }
                """);
        write("src/late/java/example/LateService.java", """
                package example;
                import io.helidon.service.registry.Service;
                @Service.Singleton
                public class LateService {
                    public String value() { return "late"; }
                }
                """);
        write("src/lateTest/java/example/LateTestService.java", """
                package example;
                import io.helidon.service.registry.Service;
                @Service.Singleton
                public class LateTestService {
                    public String value() { return "late-test"; }
                }
                """);
        write("src/lateTest/java/example/LateBindingTest.java", """
                package example;
                import io.helidon.service.registry.ServiceRegistryConfig;
                import io.helidon.service.registry.ServiceRegistryManager;
                import org.junit.jupiter.api.Test;
                import static org.hamcrest.CoreMatchers.is;
                import static org.hamcrest.MatcherAssert.assertThat;
                public class LateBindingTest {
                    @Test
                    void lateTestServiceIsBound() {
                        var binding = TestApplicationBinding.create();
                        var config = ServiceRegistryConfig.builder()
                                .discoverServices(false)
                                .discoverServicesFromServiceLoader(false)
                                .allowLateBinding(false)
                                .update(binding::configure)
                                .build();
                        var manager = ServiceRegistryManager.start(binding, config);
                        try {
                            assertThat(manager.registry().get(LateTestService.class).value(), is("late-test"));
                        } finally {
                            manager.shutdown();
                        }
                    }
                }
                """);
        write("src/main/java/example/Main.java", Files.readString(directory.resolve("src/main/java/example/Main.java"))
                .replace("main(String[] args) {", "main(String[] args) throws Exception {")
                .replace("manager.shutdown();", """
                        Class<?> lateType = Class.forName("example.LateService");
                        Object late = manager.registry().get(lateType);
                        System.out.println("LATE:" + lateType.getMethod("value").invoke(late));
                        manager.shutdown();
                        """));

        BuildResult classes = runner("classes", "--configuration-cache").build();
        assertThat("classes must build the producer registered by the later callback",
                   classes.task(":compileLateServices"), notNullValue());
        assertTask(classes, ":compileLateServices", TaskOutcome.SUCCESS);
        assertTask(classes, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        String binding = Files.readString(directory.resolve(
                "build/generated/sources/helidon/main/example/ApplicationBinding.java"));
        assertThat(binding, containsString("LateService__ServiceDescriptor"));

        BuildResult packaged = runner("test", "packagedRun", "rawLateJar", "prefixedOutputJar",
                                      "--configuration-cache").build();
        assertTask(packaged, ":compileLateTestServices", TaskOutcome.SUCCESS);
        assertTask(packaged, ":test", TaskOutcome.SUCCESS);
        assertTask(packaged, ":packagedRun", TaskOutcome.SUCCESS);
        assertThat(packaged.getOutput(), containsString("APPLICATION:chosen:library"));
        assertThat(packaged.getOutput(), containsString("LATE:late"));
        String testBinding = Files.readString(directory.resolve(
                "build/generated/sources/helidon/test/example/TestApplicationBinding.java"));
        assertThat(testBinding, containsString("LateService__ServiceDescriptor"));
        assertThat(testBinding, containsString("LateTestService__ServiceDescriptor"));
        assertThat(Files.readString(directory.resolve("build/test-results/test/TEST-example.LateBindingTest.xml")),
                   containsString("tests=\"1\""));
        try (var jar = new ZipFile(directory.resolve("build/libs/application.jar").toFile())) {
            assertThat(jar.stream().filter(entry -> entry.getName().equals("example/LateService.class")).count(), is(1L));
            assertThat(jar.stream().filter(entry -> entry.getName().equals("example/ApplicationBinding.class")).count(),
                       is(1L));
        }
        try (var jar = new ZipFile(directory.resolve("build/libs/application-raw-late.jar").toFile())) {
            assertThat("A jar of raw compiler output must retain its classes",
                       jar.getEntry("example/LateService.class"), notNullValue());
        }
        try (var jar = new ZipFile(directory.resolve("build/libs/application-prefixed-output.jar").toFile())) {
            assertThat(jar.stream().filter(entry -> entry.getName().equals("nested/example/LateService.class")).count(),
                       is(1L));
            assertThat(jar.getEntry("nested/example/ApplicationBinding.class"), notNullValue());
        }

        BuildResult repeated = runner("test", "packagedRun", "rawLateJar", "prefixedOutputJar",
                                      "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertThat(repeated.getOutput(), containsString("LATE:late"));
        assertTask(repeated, ":compileLateServices", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":compileLateTestServices", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateTestApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":test", TaskOutcome.UP_TO_DATE);
    }

    @Test
    void modularApplicationBindingsArePackagedAndRun() throws IOException {
        fixture();
        write("library/src/main/java/module-info.java", """
                module example.library {
                    requires io.helidon.service.registry;
                    exports library;
                }
                """);
        write("src/main/java/module-info.java", """
                module example.app {
                    requires io.helidon.service.registry;
                    requires example.library;
                    exports example;
                }
                """);
        write("build.gradle", Files.readString(directory.resolve("build.gradle"))
                .replace("id 'java'", "id 'application'") + """

                application {
                    mainModule = 'example.app'
                    mainClass = 'example.Main'
                }
                tasks.named('run') {
                    dependsOn tasks.named('jar')
                    classpath = files(tasks.named('jar').flatMap { it.archiveFile }) + configurations.runtimeClasspath
                }
                """);

        BuildResult result = runner("jar", "run").build();
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":jar", TaskOutcome.SUCCESS);
        assertTask(result, ":run", TaskOutcome.SUCCESS);
        // Main rejects the annotation processor's EmptyBinding placeholder and disables runtime discovery.
        assertThat(result.getOutput(), containsString("APPLICATION:chosen:library"));
        String binding = Files.readString(directory.resolve(
                "build/generated/sources/helidon/main/example/ApplicationBinding.java"));
        assertThat(binding, containsString("Greeting__ServiceDescriptor"));
        assertThat(binding, containsString("LibraryService__ServiceDescriptor"));
        try (var jar = new ZipFile(directory.resolve("build/libs/application.jar").toFile())) {
            assertThat(jar.getEntry("module-info.class"), notNullValue());
            assertThat(jar.getEntry("example/ApplicationBinding.class"), notNullValue());
            assertThat(jar.getEntry("example/ApplicationMain.class"), notNullValue());
            assertThat(jar.getEntry("example/Greeting__ServiceDescriptor.class"), notNullValue());
        }
    }

    @Test
    void regeneratesInSameDaemonAfterServiceChangesAndRemovesOldBinding() throws IOException {
        fixture();
        write("gradle.properties", "org.gradle.daemon=true\norg.gradle.workers.max=2\n");
        write("build.gradle", Files.readString(directory.resolve("build.gradle")) + """

                tasks.register('generationDaemon') {
                    doLast {
                        println 'GENERATION_DAEMON:' + ProcessHandle.current().pid()
                    }
                }
                """);
        BuildResult first = runner("jar", "packagedRun", "generationDaemon", "--configuration-cache").build();
        assertThat(first.getOutput(), containsString("APPLICATION:chosen:library"));
        String daemon = first.getOutput().lines()
                .filter(line -> line.startsWith("GENERATION_DAEMON:"))
                .findFirst().orElseThrow();
        write("src/main/java/example/Added.java", """
                package example;
                import io.helidon.service.registry.Service;
                @Service.Singleton
                public class Added { }
                """);
        Path consumer = directory.resolve("src/main/java/example/Consumer.java");
        String originalConsumer = Files.readString(consumer);
        Files.writeString(consumer, originalConsumer.replace(
                "Consumer(@Service.Named(\"chosen\") Greeting greeting)",
                "Consumer(@Service.Named(\"chosen\") Greeting greeting, Added added)"));
        BuildResult added = runner("jar", "packagedRun", "generationDaemon", "--configuration-cache").build();
        assertSameGenerationDaemon(added, daemon);
        assertTask(added, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertThat(added.getOutput(), containsString("APPLICATION:chosen:library"));
        Path binding = directory.resolve("build/generated/sources/helidon/main/example/ApplicationBinding.java");
        assertThat(Files.readString(binding), containsString("Added__ServiceDescriptor"));

        Files.delete(directory.resolve("src/main/java/example/Added.java"));
        Files.writeString(consumer, originalConsumer);
        String greeting = Files.readString(directory.resolve("src/main/java/example/Greeting.java"));
        write("src/main/java/example/Greeting.java", greeting.replace("chosen:", "updated:"));
        BuildResult changed = runner("jar", "packagedRun", "generationDaemon", "--configuration-cache").build();
        assertSameGenerationDaemon(changed, daemon);
        assertTask(changed, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertThat(changed.getOutput(), containsString("APPLICATION:updated:library"));
        assertThat(Files.readString(binding), not(containsString("Added__ServiceDescriptor")));

        Path library = directory.resolve("library/src/main/java/library/LibraryService.java");
        Files.writeString(library, Files.readString(library).replace("\"library\"", "\"changed-library\""));
        BuildResult dependencyChanged = runner("jar", "packagedRun", "generationDaemon", "--configuration-cache").build();
        assertSameGenerationDaemon(dependencyChanged, daemon);
        assertTask(dependencyChanged, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertThat(dependencyChanged.getOutput(), containsString("APPLICATION:updated:changed-library"));

        write("build.gradle", Files.readString(directory.resolve("build.gradle"))
                .replace("bindingClassName = 'ApplicationBinding'", "bindingClassName = 'RenamedBinding'"));
        write("src/main/java/example/Main.java", Files.readString(directory.resolve("src/main/java/example/Main.java"))
                .replace("@Service.GenerateBinding", "@Service.GenerateBinding(\"RenamedBinding\")")
                .replace("ApplicationBinding.create()", "RenamedBinding.create()"));
        runner("jar", "packagedRun").build();
        assertThat(Files.exists(binding), is(false));
        try (var jar = new ZipFile(directory.resolve("build/libs/application.jar").toFile())) {
            assertThat(jar.getEntry("example/ApplicationBinding.class"), nullValue());
            assertThat(jar.getEntry("example/RenamedBinding.class"), notNullValue());
            assertThat(jar.getEntry("example/Added.class"), nullValue());
            assertThat(jar.getEntry("example/Added__ServiceDescriptor.class"), nullValue());
        }
    }

    @Test
    void invalidScopeFailsDuringBindingGeneration() throws IOException {
        invalidScopeFixture();
        write("build.gradle", Files.readString(directory.resolve("build.gradle"))
                + "\nhelidonService { failOnWarning = true }\n");
        BuildResult failure = runner("jar").buildAndFail();
        assertTask(failure, ":generateApplicationBindings", TaskOutcome.FAILED);
        assertThat(failure.getOutput(), containsString("request scoped"));
        assertThat(failure.getOutput(), containsString("RequestScoped"));
        assertThat(failure.getOutput(), containsString("Broken"));
    }

    @Test
    void invalidScopeCanBeReportedWithoutFailingBuild() throws IOException {
        invalidScopeFixture();
        write("build.gradle", Files.readString(directory.resolve("build.gradle")) + """

                helidonService {
                    failOnWarning = true
                    failOnError = false
                }
                """);
        runner("compileJava").build();
        Path rawBinding = directory.resolve("build/classes/java/main/example/ApplicationBinding.class");
        byte[] placeholder = Files.readAllBytes(rawBinding);

        BuildResult result = runner("jar", "--configuration-cache").build();
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":jar", TaskOutcome.SUCCESS);
        assertThat("Ignored generation failure must still be reported",
                   result.getOutput(), containsString("Failed to generate application binding:"));
        assertThat("Validation diagnostics must identify the invalid dependency",
                   result.getOutput(), containsString("request scoped"));
        assertThat(result.getOutput(), containsString("RequestScoped"));
        assertThat(result.getOutput(), containsString("Broken"));
        assertThat("An ignored generation failure must not replace the compiler's placeholder",
                   Files.readAllBytes(rawBinding), is(placeholder));
        assertThat("Failed validation must not emit a completed binding",
                   Files.exists(directory.resolve("build/classes/helidon/main/example/ApplicationBinding.class")), is(false));
        try (var jar = new ZipFile(directory.resolve("build/libs/application.jar").toFile())) {
            assertThat("Without a completed binding the archive must retain the compiler's placeholder",
                       jar.getEntry("example/ApplicationBinding.class"), notNullValue());
            assertThat("An ignored generation failure must preserve the raw binding in the archive",
                       jar.getInputStream(jar.getEntry("example/ApplicationBinding.class")).readAllBytes(), is(placeholder));
        }

        BuildResult repeated = runner("jar", "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);
    }

    private static List<Path> classpath(String name) throws IOException {
        String value = Files.readString(Path.of(System.getProperty("fixture.classpaths"), name)).trim();
        return Arrays.stream(value.split(Pattern.quote(File.pathSeparator))).map(Path::of).toList();
    }

    private static String files(List<Path> paths) {
        return paths.stream()
                .map(path -> "'" + path.toAbsolutePath().toString().replace("\\", "/").replace("'", "\\'") + "'")
                .collect(Collectors.joining(", "));
    }

    private static boolean isGradleArtifact(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith("gradle-") || name.startsWith("groovy-")
                || name.startsWith("helidon-service-gradle-plugin-");
    }

    private static void assertTask(BuildResult result, String name, TaskOutcome expected) {
        assertThat("Missing task " + name + " in:\n" + result.getOutput(), result.task(name), notNullValue());
        assertThat(name + " in:\n" + result.getOutput(), result.task(name).getOutcome(), is(expected));
    }

    private static void assertSameGenerationDaemon(BuildResult result, String daemon) {
        assertThat(result.getOutput(), containsString("Reusing configuration cache"));
        assertThat(result.getOutput(), result.getOutput().lines()
                .filter(line -> line.startsWith("GENERATION_DAEMON:"))
                .toList(), is(List.of(daemon)));
    }

    private void generatedOnlyArchiveRule(boolean excludeGenerated) throws IOException {
        fixture();
        String action = excludeGenerated ? "details.exclude()" : "details.path = 'relocated/' + details.path";
        write("build.gradle", Files.readString(directory.resolve("build.gradle")) + """

                def generatedClasses = tasks.named('generateApplicationBindings').flatMap { it.classesDirectory }
                tasks.named('jar') {
                    eachFile { details ->
                        if (details.file.toPath().startsWith(generatedClasses.get().asFile.toPath())) {
                            %s
                        }
                    }
                }
                """.formatted(action));

        BuildResult result = runner("jar", "--configuration-cache").build();
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":jar", TaskOutcome.SUCCESS);
        String bindingPath = "example/ApplicationBinding.class";
        byte[] placeholder = Files.readAllBytes(directory.resolve("build/classes/java/main/" + bindingPath));
        byte[] completed = Files.readAllBytes(directory.resolve("build/classes/helidon/main/" + bindingPath));
        assertThat("The completed binding must differ from the compiler's placeholder",
                   completed, not(is(placeholder)));
        try (var jar = new ZipFile(directory.resolve("build/libs/application.jar").toFile())) {
            assertThat("The raw placeholder must remain when no generated binding occupies its archive path",
                       jar.stream().filter(entry -> entry.getName().equals(bindingPath)).count(), is(1L));
            assertThat("Archive customization must preserve the raw binding's bytes at its original path",
                       jar.getInputStream(jar.getEntry(bindingPath)).readAllBytes(), is(placeholder));
            assertThat("Archive customization must retain ordinary compiler output",
                       jar.getEntry("example/Greeting.class"), notNullValue());
            assertThat("The generated main must not remain at its original path",
                       jar.getEntry("example/ApplicationMain.class"), nullValue());
            if (excludeGenerated) {
                assertThat("The archive must contain only the raw binding when generated classes are excluded",
                           jar.stream().filter(entry -> entry.getName().endsWith("/ApplicationBinding.class")).count(),
                           is(1L));
                assertThat("Generated main classes must be absent when generated classes are excluded",
                           jar.stream().filter(entry -> entry.getName().endsWith("/ApplicationMain.class")).count(),
                           is(0L));
            } else {
                String relocatedBinding = "relocated/" + bindingPath;
                assertThat("The relocated generated binding must be included exactly once",
                           jar.stream().filter(entry -> entry.getName().equals(relocatedBinding)).count(), is(1L));
                assertThat("The relocated binding must retain its generated bytes",
                           jar.getInputStream(jar.getEntry(relocatedBinding)).readAllBytes(), is(completed));
                assertThat("The generated main must move with the other generated classes",
                           jar.getEntry("relocated/example/ApplicationMain.class"), notNullValue());
            }
        }

        BuildResult repeated = runner("jar", "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertTask(repeated, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);
    }

    private void invalidScopeFixture() throws IOException {
        fixture();
        write("src/main/java/example/RequestScoped.java", """
                package example;
                import io.helidon.service.registry.Service;
                @Service.PerRequest
                public class RequestScoped { }
                """);
        write("src/main/java/example/Broken.java", """
                package example;
                import io.helidon.service.registry.Service;
                @Service.Singleton
                public class Broken {
                    @Service.Inject
                    public Broken(RequestScoped request) { }
                }
                """);
    }

    private void groovyClassOutputs(boolean applyBeforeHelidon) throws IOException {
        fixture();
        String plugins = applyBeforeHelidon
                ? "id 'groovy'\n    id 'io.helidon.service'"
                : "id 'io.helidon.service'\n    id 'groovy'";
        write("build.gradle", Files.readString(directory.resolve("build.gradle"))
                .replace("id 'io.helidon.service'", plugins) + """

                dependencies {
                    implementation localGroovy()
                }
                """);
        write("src/main/groovy/example/GroovyProbe.groovy", """
                package example
                class GroovyProbe {
                    static String value(Consumer consumer) {
                        return "groovy:" + consumer.value()
                    }
                }
                """);
        write("src/test/groovy/example/TestGroovyProbe.groovy", """
                package example
                class TestGroovyProbe {
                    static String value(TestOnly service) {
                        return "groovy-test:" + service.value()
                    }
                }
                """);
        write("src/main/java/example/Main.java", Files.readString(directory.resolve("src/main/java/example/Main.java"))
                .replace("main(String[] args) {", "main(String[] args) throws Exception {")
                .replace("manager.shutdown();", """
                        Class<?> groovy = Class.forName("example.GroovyProbe");
                        Object value = groovy.getMethod("value", Consumer.class)
                                .invoke(null, manager.registry().get(Consumer.class));
                        System.out.println("GROOVY:" + value);
                        manager.shutdown();
                        """));
        write("src/test/java/example/GroovyInteropTest.java", """
                package example;
                import io.helidon.service.registry.ServiceRegistryConfig;
                import io.helidon.service.registry.ServiceRegistryManager;
                import org.junit.jupiter.api.Test;
                import static org.hamcrest.CoreMatchers.is;
                import static org.hamcrest.MatcherAssert.assertThat;
                public class GroovyInteropTest {
                    @Test
                    void groovyTestSourcesConsumeJavaServices() throws Exception {
                        var binding = TestApplicationBinding.create();
                        var config = ServiceRegistryConfig.builder()
                                .discoverServices(false)
                                .discoverServicesFromServiceLoader(false)
                                .allowLateBinding(false)
                                .update(binding::configure)
                                .build();
                        var manager = ServiceRegistryManager.start(binding, config);
                        try {
                            Class<?> groovy = Class.forName("example.TestGroovyProbe");
                            Object value = groovy.getMethod("value", TestOnly.class)
                                    .invoke(null, manager.registry().get(TestOnly.class));
                            assertThat(value, is("groovy-test:test:chosen:library"));
                        } finally {
                            manager.shutdown();
                        }
                    }
                }
                """);

        BuildResult first = runner("classes", "test", "packagedRun", "--configuration-cache").build();
        assertTask(first, ":compileJava", TaskOutcome.SUCCESS);
        assertTask(first, ":compileGroovy", TaskOutcome.SUCCESS);
        assertTask(first, ":compileTestGroovy", TaskOutcome.SUCCESS);
        assertTask(first, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(first, ":generateTestApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(first, ":test", TaskOutcome.SUCCESS);
        assertTask(first, ":jar", TaskOutcome.SUCCESS);
        assertTask(first, ":packagedRun", TaskOutcome.SUCCESS);
        assertThat(first.getOutput(), containsString("GROOVY:groovy:chosen:library"));
        assertThat(Files.readString(directory.resolve("build/test-results/test/TEST-example.GroovyInteropTest.xml")),
                   containsString("tests=\"1\""));
        try (var jar = new ZipFile(directory.resolve("build/libs/application.jar").toFile())) {
            assertThat(jar.getEntry("example/GroovyProbe.class"), notNullValue());
            assertThat(jar.stream().filter(entry -> entry.getName().equals("example/ApplicationBinding.class")).count(),
                       is(1L));
        }

        BuildResult repeated = runner("classes", "test", "packagedRun", "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertTask(repeated, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":compileGroovy", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":compileTestGroovy", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateTestApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":test", TaskOutcome.UP_TO_DATE);

        Path groovySource = directory.resolve("src/main/groovy/example/GroovyProbe.groovy");
        Files.writeString(groovySource, Files.readString(groovySource).replace("groovy:", "updated-groovy:"));
        BuildResult changed = runner("classes", "test", "packagedRun", "--configuration-cache").build();
        assertTask(changed, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(changed, ":compileGroovy", TaskOutcome.SUCCESS);
        assertTask(changed, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(changed, ":jar", TaskOutcome.SUCCESS);
        assertThat(changed.getOutput(), containsString("GROOVY:updated-groovy:chosen:library"));
    }

    private void customClassDirectories(boolean configureBeforePlugin, boolean buildDirectoryProvider) throws IOException {
        fixture();
        String rawDirectory = buildDirectoryProvider ? "custom-build/custom/raw-main" : "custom-raw-main";
        String destination = buildDirectoryProvider
                ? "layout.buildDirectory.dir('custom/raw-main')"
                : "layout.projectDirectory.dir('custom-raw-main')";
        String configure = configureBeforePlugin
                ? "tasks.named('compileJava', JavaCompile).get().destinationDirectory = " + destination + "\n"
                : "tasks.named('compileJava', JavaCompile) { destinationDirectory = " + destination + " }\n";
        write("build.gradle", Files.readString(directory.resolve("build.gradle"))
                .replace("id 'io.helidon.service'", "id 'io.helidon.service' apply false")
                .replace("java { toolchain", (configureBeforePlugin ? configure : "")
                        + "apply plugin: 'io.helidon.service'\n"
                        + (configureBeforePlugin ? "" : configure)
                        + "java { toolchain") + """

                tasks.named('generateApplicationBindings') {
                    classesDirectory = layout.buildDirectory.dir('custom/completed-main')
                }
                layout.buildDirectory = layout.projectDirectory.dir('custom-build')
                """);

        BuildResult result = runner("packagedRun", "--configuration-cache").build();
        assertTask(result, ":compileJava", TaskOutcome.SUCCESS);
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":jar", TaskOutcome.SUCCESS);
        assertThat(result.getOutput(), containsString("APPLICATION:chosen:library"));
        Path rawBinding = directory.resolve(rawDirectory + "/example/ApplicationBinding.class");
        Path completedBinding = directory.resolve("custom-build/custom/completed-main/example/ApplicationBinding.class");
        assertThat("Configured compiler destination must retain the raw placeholder", Files.exists(rawBinding), is(true));
        assertThat("Generator destination must contain the completed binding", Files.exists(completedBinding), is(true));
        assertThat("Completed binding must replace the placeholder",
                   Files.mismatch(rawBinding, completedBinding), not(is(-1L)));
        assertThat("Late build directory configuration must not leave default output behind",
                   Files.exists(directory.resolve("build/classes/helidon/main")), is(false));
        assertThat("Late build directory configuration must relocate the custom raw output provider",
                   Files.exists(directory.resolve("build/custom/raw-main")), is(false));
        try (var jar = new ZipFile(directory.resolve("custom-build/libs/application.jar").toFile())) {
            assertThat(jar.stream().filter(entry -> entry.getName().equals("example/ApplicationBinding.class")).count(),
                       is(1L));
        }

        BuildResult repeated = runner("packagedRun", "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertTask(repeated, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);
    }

    private void extraClassOutputs(boolean registerBeforePlugin) throws IOException {
        fixture();
        String producers = """
                def extraMainCompile = tasks.register('compileExtraMain', JavaCompile) {
                    source = fileTree('src/extraMain/java')
                    classpath = files()
                    destinationDirectory = layout.buildDirectory.dir('classes/extra/main')
                }
                def extraMainClasses = objects.directoryProperty()
                extraMainClasses.set(extraMainCompile.flatMap { it.destinationDirectory })
                sourceSets.main.output.classesDirs.from(extraMainClasses)

                def extraTestClasses = layout.buildDirectory.dir('classes/extra/test')
                def extraTestCompile = tasks.register('compileExtraTest', JavaCompile) {
                    source = fileTree('src/extraTest/java')
                    classpath = files()
                    destinationDirectory = extraTestClasses
                }
                sourceSets.test.output.classesDirs.from(extraTestClasses)
                sourceSets.test.output.classesDirs.builtBy(extraTestCompile)
                """;
        String applyPlugin = "apply plugin: 'io.helidon.service'\n";
        write("build.gradle", Files.readString(directory.resolve("build.gradle"))
                .replace("id 'io.helidon.service'", "id 'io.helidon.service' apply false")
                .replace("java { toolchain", (registerBeforePlugin ? producers + applyPlugin : applyPlugin + producers)
                        + "java { toolchain"));
        write("src/extraMain/java/extra/MainOutput.java", """
                package extra;
                public class MainOutput {
                    public static String marker() { return "extra-main"; }
                }
                """);
        write("src/extraTest/java/extra/TestOutput.java", """
                package extra;
                public class TestOutput {
                    public static String marker() { return "extra-test"; }
                }
                """);
        write("src/main/java/example/Main.java", Files.readString(directory.resolve("src/main/java/example/Main.java"))
                .replace("main(String[] args) {", "main(String[] args) throws Exception {")
                .replace("manager.shutdown();", """
                        manager.shutdown();
                        System.out.println("EXTRA:" + Class.forName("extra.MainOutput").getMethod("marker").invoke(null));
                        """));
        write("src/test/java/example/ExtraOutputTest.java", """
                package example;

                import org.junit.jupiter.api.Test;

                import static org.hamcrest.CoreMatchers.is;
                import static org.hamcrest.MatcherAssert.assertThat;

                public class ExtraOutputTest {
                    @Test
                    void extraClassesAreOnTestRuntimeClasspath() throws Exception {
                        Object main = Class.forName("extra.MainOutput").getMethod("marker").invoke(null);
                        Object test = Class.forName("extra.TestOutput").getMethod("marker").invoke(null);
                        assertThat("Extra main class marker", main, is("extra-main"));
                        assertThat("Extra test class marker", test, is("extra-test"));
                    }
                }
                """);

        BuildResult result = runner("test", "packagedRun", "--configuration-cache").build();
        assertTask(result, ":compileExtraMain", TaskOutcome.SUCCESS);
        assertTask(result, ":compileExtraTest", TaskOutcome.SUCCESS);
        assertTask(result, ":test", TaskOutcome.SUCCESS);
        assertTask(result, ":packagedRun", TaskOutcome.SUCCESS);
        assertThat(result.getOutput(), containsString("EXTRA:extra-main"));
        assertThat(Files.readString(directory.resolve("build/test-results/test/TEST-example.ExtraOutputTest.xml")),
                   containsString("tests=\"1\""));
        assertThat("Extra main class must remain in its own compiler output",
                   Files.exists(directory.resolve("build/classes/extra/main/extra/MainOutput.class")), is(true));
        assertThat("Extra test class must remain in its own compiler output",
                   Files.exists(directory.resolve("build/classes/extra/test/extra/TestOutput.class")), is(true));
        assertThat("Completed application output must not copy unrelated compiler outputs",
                   Files.exists(directory.resolve("build/classes/helidon/main/extra/MainOutput.class")), is(false));
        assertThat("Completed test output must not copy unrelated compiler outputs",
                   Files.exists(directory.resolve("build/classes/helidon/test/extra/TestOutput.class")), is(false));
        try (var jar = new ZipFile(directory.resolve("build/libs/application.jar").toFile())) {
            assertThat(jar.stream().filter(entry -> entry.getName().equals("extra/MainOutput.class")).count(), is(1L));
            assertThat(jar.getEntry("extra/TestOutput.class"), nullValue());
        }

        BuildResult repeated = runner("test", "packagedRun", "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertThat(repeated.getOutput(), containsString("EXTRA:extra-main"));
        assertTask(repeated, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":compileTestJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":compileExtraMain", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":compileExtraTest", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateTestApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":test", TaskOutcome.UP_TO_DATE);
    }

    private void fixture() throws IOException {
        List<Path> tools = classpath("fixture-tool-classpath.txt");
        List<Path> runtime = classpath("fixture-runtime-classpath.txt");
        List<Path> generator = tools.stream().filter(path -> !isGradleArtifact(path)).toList();
        List<Path> compilation = tools.stream().filter(path -> {
            String name = path.getFileName().toString();
            return name.startsWith("helidon-config-metadata-");
        }).toList();
        List<Path> compileLibraries = Stream.concat(runtime.stream(), compilation.stream()).toList();
        List<Path> testLibraries = tools.stream().filter(path -> {
            String name = path.getFileName().toString();
            return name.startsWith("junit-") || name.startsWith("apiguardian-") || name.startsWith("opentest4j-")
                    || name.startsWith("hamcrest-all-");
        }).toList();
        write("settings.gradle", """
                rootProject.name = 'application'
                include 'library'
                file('gradle-installation.txt').text = gradle.gradleHomeDir.absolutePath
                """);
        write("gradle.properties", "org.gradle.daemon=false\norg.gradle.workers.max=2\n");
        write("library/build.gradle", """
                plugins { id 'java-library' }
                java { toolchain { languageVersion = JavaLanguageVersion.of(27) } }
                dependencies {
                    implementation files(%s)
                    annotationProcessor files(%s)
                }
                """.formatted(files(runtime), files(generator)));
        write("build.gradle", """
                plugins {
                    id 'java'
                    id 'io.helidon.service'
                }
                java { toolchain { languageVersion = JavaLanguageVersion.of(27) } }
                dependencies {
                    implementation files(%s)
                    implementation project(path: ':library', configuration: 'runtimeElements')
                    annotationProcessor files(%s)
                    testAnnotationProcessor files(%s)
                    testImplementation files(%s)
                }
                helidonService {
                    packageName = 'example'
                    bindingClassName = 'ApplicationBinding'
                    generateMain = true
                }
                tasks.named('test') { useJUnitPlatform() }
                tasks.register('packagedRun', JavaExec) {
                    dependsOn tasks.named('jar')
                    classpath = files(tasks.named('jar').flatMap { it.archiveFile }) + configurations.runtimeClasspath
                    mainClass = 'example.Main'
                }
                tasks.register('generatedRun', JavaExec) {
                    dependsOn tasks.named('jar')
                    classpath = files(tasks.named('jar').flatMap { it.archiveFile }) + configurations.runtimeClasspath
                    mainClass = 'example.ApplicationMain'
                }
                """.formatted(files(compileLibraries), files(generator), files(generator), files(testLibraries)));
        write("library/src/main/java/library/LibraryService.java", """
                package library;
                import io.helidon.service.registry.Service;
                @Service.Singleton
                public class LibraryService {
                    public String value() { return "library"; }
                }
                """);
        write("src/main/java/example/Greeting.java", """
                package example;
                import io.helidon.service.registry.Service;
                import library.LibraryService;
                @Service.Singleton
                @Service.Named("chosen")
                @Service.RunLevel(1)
                public class Greeting {
                    private final LibraryService library;
                    @Service.Inject
                    public Greeting(LibraryService library) { this.library = library; }
                    public String value() { return "chosen:" + library.value(); }
                    @Service.PostConstruct
                    public void started() { System.out.println("GENERATED:" + value()); }
                }
                """);
        write("src/main/java/example/Consumer.java", """
                package example;
                import io.helidon.service.registry.Service;
                @Service.Singleton
                public class Consumer {
                    private final Greeting greeting;
                    @Service.Inject
                    public Consumer(@Service.Named("chosen") Greeting greeting) { this.greeting = greeting; }
                    public String value() { return greeting.value(); }
                }
                """);
        write("src/main/java/example/Main.java", """
                package example;
                import io.helidon.service.registry.Binding;
                import io.helidon.service.registry.EmptyBinding;
                import io.helidon.service.registry.Service;
                import io.helidon.service.registry.ServiceRegistryConfig;
                import io.helidon.service.registry.ServiceRegistryManager;
                @Service.GenerateBinding
                public class Main {
                    public static void main(String[] args) {
                        Binding binding = ApplicationBinding.create();
                        if (binding instanceof EmptyBinding) {
                            throw new AssertionError("Annotation processor placeholder was not replaced");
                        }
                        var config = ServiceRegistryConfig.builder()
                                .discoverServices(false)
                                .discoverServicesFromServiceLoader(false)
                                .allowLateBinding(false)
                                .useBinding(true)
                                .update(binding::configure)
                                .build();
                        if (config.discoverServices() || config.discoverServicesFromServiceLoader()) {
                            throw new AssertionError("Generated binding enabled runtime discovery");
                        }
                        var manager = ServiceRegistryManager.start(binding, config);
                        System.out.println("APPLICATION:" + manager.registry().get(Consumer.class).value());
                        manager.shutdown();
                    }
                }
                """);
        write("src/test/java/example/TestOnly.java", """
                package example;
                import io.helidon.service.registry.Service;
                @Service.Singleton
                public class TestOnly {
                    private final Consumer consumer;
                    @Service.Inject
                    public TestOnly(Consumer consumer) { this.consumer = consumer; }
                    public String value() { return "test:" + consumer.value(); }
                }
                """);
        write("src/test/java/example/BindingTest.java", """
                package example;
                import io.helidon.service.registry.Binding;
                import io.helidon.service.registry.EmptyBinding;
                import io.helidon.service.registry.Service;
                import io.helidon.service.registry.ServiceRegistryConfig;
                import io.helidon.service.registry.ServiceRegistryManager;
                import org.junit.jupiter.api.Test;
                import static org.hamcrest.CoreMatchers.instanceOf;
                import static org.hamcrest.CoreMatchers.is;
                import static org.hamcrest.CoreMatchers.not;
                import static org.hamcrest.MatcherAssert.assertThat;
                @Service.GenerateBinding("TestApplicationBinding")
                public class BindingTest {
                    @Test
                    void testBindingIncludesMainAndTestServices() {
                        Binding binding = TestApplicationBinding.create();
                        assertThat("Test annotation processor placeholder was replaced", binding,
                                   not(instanceOf(EmptyBinding.class)));
                        var config = ServiceRegistryConfig.builder()
                                        .discoverServices(false)
                                        .discoverServicesFromServiceLoader(false)
                                        .allowLateBinding(false)
                                        .update(binding::configure)
                                        .build();
                        assertThat("Generated test binding disabled runtime discovery", config.discoverServices(), is(false));
                        assertThat("Generated test binding disabled service loader discovery",
                                   config.discoverServicesFromServiceLoader(), is(false));
                        var manager = ServiceRegistryManager.start(binding, config);
                        try {
                            String actual = manager.registry().get(TestOnly.class).value();
                            assertThat("Injected value", actual, is("test:chosen:library"));
                        } finally {
                            manager.shutdown();
                        }
                    }
                }
                """);
    }

    private GradleRunner runner(String... tasks) throws IOException {
        return GradleFixtureSupport.runner(directory, tasks)
                .withPluginClasspath(classpath("fixture-tool-classpath.txt").stream()
                                             .filter(path -> path.getFileName().toString()
                                                     .startsWith("helidon-service-gradle-plugin-") || !isGradleArtifact(path))
                                             .map(Path::toFile)
                                             .toList());
    }

    private void write(String name, String contents) throws IOException {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
