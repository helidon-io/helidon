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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    private static final String GRADLE_VERSION = "9.8.1";

    private Path directory;

    @BeforeEach
    void createProjectDirectory() throws IOException {
        directory = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "gradle-test-");
    }

    @AfterEach
    void stopFixtureDaemons() throws Exception {
        Path registry = directory.resolve("test-kit/test-kit-daemon");
        if (!Files.isDirectory(registry)) {
            return;
        }
        Path installation = Path.of(Files.readString(directory.resolve("gradle-installation.txt")).trim());
        Path launcher;
        try (var libraries = Files.list(installation.resolve("lib"))) {
            launcher = libraries.filter(path -> path.getFileName().toString().startsWith("gradle-gradle-cli-main-")
                            && path.toString().endsWith(".jar"))
                    .findFirst().orElseThrow();
        }
        List<Long> daemonPids;
        try (var logs = Files.walk(registry)) {
            daemonPids = logs.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("daemon-[0-9]+\\.out\\.log"))
                    .map(name -> Long.parseLong(name.substring(7, name.length() - 8)))
                    .toList();
        }
        Path output = directory.resolve("daemon-stop.log");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                                         "-Dorg.gradle.daemon.registry.base=" + registry,
                                         "-jar", launcher.toString(),
                                         "--stop", "--gradle-user-home", directory.resolve("test-kit").toString())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(30, TimeUnit.SECONDS);
            throw new AssertionError("Timed out stopping fixture Gradle daemons: " + Files.readString(output));
        }
        assertThat(Files.readString(output), process.exitValue(), is(0));
        for (long pid : daemonPids) {
            var daemon = ProcessHandle.of(pid);
            if (daemon.isPresent()) {
                daemon.get().onExit().get(30, TimeUnit.SECONDS);
            }
        }
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

    private void fixture() throws IOException {
        List<Path> tools = classpath("fixture-tool-classpath.txt");
        List<Path> runtime = classpath("fixture-runtime-classpath.txt");
        List<Path> generator = tools.stream().filter(path -> !isGradleArtifact(path)).toList();
        List<Path> junit = tools.stream().filter(path -> {
            String name = path.getFileName().toString();
            return name.startsWith("junit-") || name.startsWith("apiguardian-") || name.startsWith("opentest4j-");
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
                """.formatted(files(runtime), files(generator), files(generator), files(junit), files(generator)));
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
                @Service.GenerateBinding("TestApplicationBinding")
                public class BindingTest {
                    @Test
                    void testBindingIncludesMainAndTestServices() {
                        Binding binding = TestApplicationBinding.create();
                        if (binding instanceof EmptyBinding) {
                            throw new AssertionError("Test annotation processor placeholder was not replaced");
                        }
                        var config = ServiceRegistryConfig.builder()
                                        .discoverServices(false)
                                        .discoverServicesFromServiceLoader(false)
                                        .allowLateBinding(false)
                                        .update(binding::configure)
                                        .build();
                        if (config.discoverServices() || config.discoverServicesFromServiceLoader()) {
                            throw new AssertionError("Generated test binding enabled runtime discovery");
                        }
                        var manager = ServiceRegistryManager.start(binding, config);
                        try {
                            String actual = manager.registry().get(TestOnly.class).value();
                            if (!"test:chosen:library".equals(actual)) {
                                throw new AssertionError("Unexpected injected value: " + actual);
                            }
                        } finally {
                            manager.shutdown();
                        }
                    }
                }
                """);
    }

    private GradleRunner runner(String... tasks) throws IOException {
        var arguments = new ArrayList<>(List.of(tasks));
        arguments.addAll(List.of("--stacktrace", "--offline", "--console=plain", "--parallel"));
        var runner = GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withTestKitDir(directory.resolve("test-kit").toFile())
                .withPluginClasspath(classpath("fixture-tool-classpath.txt").stream()
                                             .filter(path -> path.getFileName().toString()
                                                     .startsWith("helidon-service-gradle-plugin-"))
                                             .map(Path::toFile)
                                             .toList())
                .withArguments(arguments);
        String installation = System.getProperty("gradle.home");
        return installation == null ? runner.withGradleVersion(GRADLE_VERSION)
                : runner.withGradleInstallation(new File(installation));
    }

    private void write(String name, String contents) throws IOException {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
