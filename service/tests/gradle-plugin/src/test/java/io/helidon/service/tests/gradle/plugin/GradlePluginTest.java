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
        customClassDirectories(true);
    }

    @Test
    void preservesCompilerDestinationConfiguredAfterPlugin() throws IOException {
        customClassDirectories(false);
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
        assertThat("Extra metadata must remain in its producer's output",
                   Files.readString(directory.resolve("build/classes/helidon/main/" + configMetadata)),
                   is(mainMetadata));
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
    void generatorProjectDependencySelectsRuntimeVariant() throws IOException {
        fixture();
        write("settings.gradle", Files.readString(directory.resolve("settings.gradle")) + "\ninclude 'generator'\n");
        List<Path> generator = classpath("fixture-tool-classpath.txt").stream()
                .filter(path -> !isGradleArtifact(path))
                .toList();
        write("generator/build.gradle", """
                configurations {
                    generatorApiElements {
                        canBeConsumed = true
                        canBeResolved = false
                        attributes {
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                                      objects.named(LibraryElements, LibraryElements.JAR))
                            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
                        }
                    }
                    generatorRuntimeElements {
                        canBeConsumed = true
                        canBeResolved = false
                        attributes {
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                                      objects.named(LibraryElements, LibraryElements.JAR))
                            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
                        }
                    }
                }
                files(%s).each { artifact ->
                    configurations.generatorRuntimeElements.outgoing.artifact(artifact)
                }
                """.formatted(files(generator)));
        write("build.gradle", Files.readString(directory.resolve("build.gradle"))
                .replace("helidonServiceGenerator files(" + files(generator) + ")",
                         "helidonServiceGenerator project(':generator')") + """

                def generatorUsage = configurations.helidonServiceGenerator.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)
                if (generatorUsage?.name != Usage.JAVA_RUNTIME) {
                    throw new GradleException('helidonServiceGenerator must explicitly request java-runtime usage; found '
                                              + generatorUsage)
                }
                """);

        BuildResult result = runner("packagedRun").build();
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":jar", TaskOutcome.SUCCESS);
        assertTask(result, ":packagedRun", TaskOutcome.SUCCESS);
        assertThat(result.getOutput(), containsString("APPLICATION:chosen:library"));
    }

    @Test
    void regeneratesAfterServiceChangesAndRemovesOldBinding() throws IOException {
        fixture();
        runner("jar").build();
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
        BuildResult added = runner("jar", "packagedRun").build();
        assertThat(added.getOutput(), containsString("APPLICATION:chosen:library"));
        Path binding = directory.resolve("build/generated/sources/helidon/main/example/ApplicationBinding.java");
        assertThat(Files.readString(binding), containsString("Added__ServiceDescriptor"));

        Files.delete(directory.resolve("src/main/java/example/Added.java"));
        Files.writeString(consumer, originalConsumer);
        String greeting = Files.readString(directory.resolve("src/main/java/example/Greeting.java"));
        write("src/main/java/example/Greeting.java", greeting.replace("chosen:", "updated:"));
        BuildResult changed = runner("jar", "packagedRun").build();
        assertThat(changed.getOutput(), containsString("APPLICATION:updated:library"));
        assertThat(Files.readString(binding), not(containsString("Added__ServiceDescriptor")));

        Path library = directory.resolve("library/src/main/java/library/LibraryService.java");
        Files.writeString(library, Files.readString(library).replace("\"library\"", "\"changed-library\""));
        BuildResult dependencyChanged = runner("jar", "packagedRun").build();
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
        fixture();
        write("build.gradle", Files.readString(directory.resolve("build.gradle"))
                + "\nhelidonService { failOnWarning = true }\n");
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
        BuildResult failure = runner("jar").buildAndFail();
        assertTask(failure, ":generateApplicationBindings", TaskOutcome.FAILED);
        assertThat(failure.getOutput(), containsString("request scoped"));
        assertThat(failure.getOutput(), containsString("RequestScoped"));
        assertThat(failure.getOutput(), containsString("Broken"));
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

    private void customClassDirectories(boolean configureBeforePlugin) throws IOException {
        fixture();
        String rawDirectory = configureBeforePlugin ? "custom-raw-main" : "custom-build/custom/raw-main";
        String configure = configureBeforePlugin
                ? "tasks.named('compileJava', JavaCompile).get().destinationDirectory = "
                        + "layout.projectDirectory.dir('custom-raw-main')\n"
                : "tasks.named('compileJava', JavaCompile) { destinationDirectory = "
                        + "layout.buildDirectory.dir('custom/raw-main') }\n";
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
                    helidonServiceGenerator files(%s)
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
                """.formatted(files(compileLibraries), files(generator), files(generator), files(testLibraries), files(generator)));
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
                                             .filter(path -> {
                                                 String name = path.getFileName().toString();
                                                 return name.startsWith("helidon-service-gradle-plugin-")
                                                         || name.startsWith("helidon-metadata-hson-")
                                                         || name.startsWith("helidon-common-buffers-")
                                                         || name.matches("helidon-common-[0-9].*");
                                             })
                                             .map(Path::toFile)
                                             .toList());
    }

    private void write(String name, String contents) throws IOException {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
