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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class GradlePluginTestIT {
    private final Path projectsDir;
    private final Path testKitDir;

    GradlePluginTestIT() {
        try {
            var location = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            testKitDir = location.resolve("../test-kit").normalize().toAbsolutePath();
            projectsDir = location.resolve("../it/projects").normalize().toAbsolutePath();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void packagesMainAndTestBindings() throws IOException {
        Path root = projectsDir.resolve("packages-main-and-test-bindings");
        runner(root, "clean").build();
        var first = runner(root, "test", "packagedRun", "generatedRun", "--configuration-cache").build();
        assertThat(first.getOutput(), containsString("APPLICATION:chosen"));
        assertThat(first.getOutput(), containsString("GENERATED:chosen"));
        assertTask(first, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(first, ":generateTestApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(first, ":test", TaskOutcome.SUCCESS);
        assertThat(Files.readString(root.resolve("build/test-results/test/TEST-example.BindingTest.xml")),
                   containsString("tests=\"1\""));

        String binding = Files.readString(root.resolve(
                "build/generated/sources/helidon/main/example/ApplicationBinding.java"));
        assertThat(binding, containsString("Main_Greeting__ServiceDescriptor"));
        assertThat(binding, containsString("Main_Consumer__ServiceDescriptor"));
        assertThat(binding, not(containsString("BindingTest_TestOnly__ServiceDescriptor")));
        String testBinding = Files.readString(root.resolve(
                "build/generated/sources/helidon/test/example/TestApplicationBinding.java"));
        assertThat(testBinding, containsString("BindingTest_TestOnly__ServiceDescriptor"));
        assertThat(testBinding, containsString("Main_Greeting__ServiceDescriptor"));

        try (var jar = new ZipFile(root.resolve("build/libs/packages-main-and-test-bindings.jar").toFile())) {
            assertThat(bindingCount(jar, "example/ApplicationBinding.class"), is(1L));
            assertThat(jar.getEntry("example/ApplicationMain.class"), notNullValue());
            assertThat(jar.getEntry("example/Main_Greeting__ServiceDescriptor.class"), notNullValue());
            assertThat(jar.getEntry("example/TestApplicationBinding.class"), nullValue());
            assertThat(jar.getEntry("example/BindingTest$TestOnly.class"), nullValue());
        }

        var repeated = runner(root, "test", "packagedRun", "generatedRun", "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertTask(repeated, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":compileTestJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateTestApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":test", TaskOutcome.UP_TO_DATE);
    }

    @Test
    void testBindingWithoutMainBinding() throws IOException {
        var root = projectsDir.resolve("test-binding-without-main-binding");
        var result = runner(root, "clean", "test").build();
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":generateTestApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":test", TaskOutcome.SUCCESS);
        assertThat(Files.exists(root.resolve(
                "build/generated/sources/helidon/main/example/ApplicationBinding.java")), is(false));
        assertThat(Files.exists(root.resolve("build/classes/helidon/main/example/ApplicationMain.class")), is(false));
        String testBinding = Files.readString(root.resolve(
                "build/generated/sources/helidon/test/example/TestApplicationBinding.java"));
        assertThat(testBinding, containsString("BindingTest_TestOnly__ServiceDescriptor"));
        assertThat(testBinding, containsString("Main_MainService__ServiceDescriptor"));
    }

    @Test
    void modularApplication() throws IOException {
        var root = projectsDir.resolve("modular-application");
        var result = runner(root, "clean", "jar", "run").build();
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":jar", TaskOutcome.SUCCESS);
        assertTask(result, ":run", TaskOutcome.SUCCESS);
        assertThat(result.getOutput(), containsString("MODULAR:module"));
        String binding = Files.readString(root.resolve(
                "build/generated/sources/helidon/main/example/ApplicationBinding.java"));
        assertThat(binding, containsString("Main_ModularService__ServiceDescriptor"));
        try (var jar = new ZipFile(root.resolve("build/libs/modular-application.jar").toFile())) {
            assertThat(jar.getEntry("module-info.class"), notNullValue());
            assertThat(jar.getEntry("example/ApplicationBinding.class"), notNullValue());
            assertThat(jar.getEntry("example/ApplicationMain.class"), notNullValue());
            assertThat(jar.getEntry("example/Main_ModularService__ServiceDescriptor.class"), notNullValue());
        }
    }

    @Test
    void defaultTaskAdditionalInputs() throws IOException {
        var root = projectsDir.resolve("default-task-additional-inputs");
        runner(root, "clean").build();
        var result = runner(root, "runApplication", "--configuration-cache").build();
        assertTask(result, ":compileJava", TaskOutcome.SUCCESS);
        assertTask(result, ":compileExtra", TaskOutcome.SUCCESS);
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertThat(result.getOutput(), containsString("DEFAULT:main"));
        String binding = Files.readString(root.resolve(
                "build/generated/sources/helidon/main/example/ApplicationBinding.java"));
        assertThat(binding, containsString("Main_MainService__ServiceDescriptor"));
        assertThat(binding, containsString("Extra__ServiceDescriptor"));
        assertThat(Files.exists(root.resolve("build/classes/helidon/main/extra/Extra.class")), is(false));

        var repeated = runner(root, "runApplication", "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertTask(repeated, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":compileExtra", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);
    }

    @Test
    void independentBindingArchive() throws IOException {
        var root = projectsDir.resolve("independent-binding-archive");
        runner(root, "clean").build();
        var result = runner(root, "otherJar", "--configuration-cache").build();
        assertTask(result, ":compileOtherJava", TaskOutcome.SUCCESS);
        assertTask(result, ":generateOtherBinding", TaskOutcome.SUCCESS);
        assertTask(result, ":otherJar", TaskOutcome.SUCCESS);
        assertThat(result.task(":generateApplicationBindings"), nullValue());
        assertThat(result.task(":compileJava"), nullValue());

        var raw = root.resolve("build/classes/java/other/other/OtherBinding.class");
        var completed = root.resolve("build/classes/helidon/generateOtherBinding/other/OtherBinding.class");
        assertThat(Files.exists(raw), is(true));
        assertThat(Files.exists(completed), is(true));
        assertThat(Files.mismatch(raw, completed), not(is(-1L)));
        String binding = Files.readString(root.resolve(
                "build/generated/sources/helidon/generateOtherBinding/other/OtherBinding.java"));
        assertThat(binding, containsString("Main_OtherService__ServiceDescriptor"));
        try (var jar = new ZipFile(root.resolve("build/libs/independent-binding-archive-other.jar").toFile())) {
            assertThat(bindingCount(jar, "other/OtherBinding.class"), is(1L));
            assertThat(jar.getInputStream(jar.getEntry("other/OtherBinding.class")).readAllBytes(),
                       is(Files.readAllBytes(completed)));
            assertThat(jar.getEntry("other/OtherMain.class"), notNullValue());
            assertThat(jar.getEntry("other/Main$OtherService.class"), notNullValue());
        }

        var repeated = runner(root, "otherJar", "--configuration-cache").build();
        assertThat(repeated.getOutput(), containsString("Reusing configuration cache"));
        assertTask(repeated, ":compileOtherJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateOtherBinding", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":otherJar", TaskOutcome.UP_TO_DATE);
        var run = runner(root, "otherRun").build();
        assertThat(run.getOutput(), containsString("OTHER:independent"));
    }

    @Test
    void bindingTaskLaziness() {
        var root = projectsDir.resolve("binding-task-laziness");
        var result = runner(root, "help").build();
        assertTask(result, ":help", TaskOutcome.SUCCESS);
        assertThat(result.task(":generateApplicationBindings"), nullValue());
        assertThat(result.task(":generateTestApplicationBindings"), nullValue());
    }

    @Test
    void sourceSetOutput() throws IOException {
        var root = projectsDir.resolve("source-set-output");
        var result = runner(root, "clean", "runApplication").build();
        assertTask(result, ":compileJava", TaskOutcome.SUCCESS);
        assertTask(result, ":compileExtra", TaskOutcome.SUCCESS);
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertThat(result.getOutput(), containsString("SOURCE_SET:main"));

        String metadata = "META-INF/helidon/unnamed/example/service-registry.json";
        var mainMetadata = root.resolve("build/classes/java/main").resolve(metadata);
        var extraMetadata = root.resolve("build/classes/extra/main").resolve(metadata);
        assertThat(Files.exists(mainMetadata), is(true));
        assertThat(Files.exists(extraMetadata), is(true));
        assertThat(Files.readString(mainMetadata), containsString("Main_MainService__ServiceDescriptor"));
        assertThat(Files.readString(extraMetadata), containsString("Extra__ServiceDescriptor"));
        String binding = Files.readString(root.resolve(
                "build/generated/sources/helidon/main/example/ApplicationBinding.java"));
        assertThat(binding, containsString("Main_MainService__ServiceDescriptor"));
        assertThat(binding, containsString("Extra__ServiceDescriptor"));
        assertThat(Files.exists(root.resolve("build/classes/helidon/main/example/Extra.class")), is(false));
    }

    @Test
    void placeholderExclusion() throws IOException {
        var root = projectsDir.resolve("placeholder-exclusion");
        var compiled = runner(root, "clean", "compileJava").build();
        assertTask(compiled, ":compileJava", TaskOutcome.SUCCESS);
        var raw = root.resolve("build/classes/java/main/example/ApplicationBinding.class");
        byte[] placeholder = Files.readAllBytes(raw);

        var result = runner(root, "jar").build();
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":jar", TaskOutcome.SUCCESS);
        assertThat(Files.readAllBytes(raw), is(placeholder));
        var generated = root.resolve("build/classes/helidon/main/example/ApplicationBinding.class");
        byte[] completed = Files.readAllBytes(generated);
        assertThat(completed, not(is(placeholder)));
        try (var jar = new ZipFile(root.resolve("build/libs/placeholder-exclusion.jar").toFile())) {
            assertThat(bindingCount(jar, "example/ApplicationBinding.class"), is(1L));
            assertThat(jar.getInputStream(jar.getEntry("example/ApplicationBinding.class")).readAllBytes(),
                       is(completed));
        }
    }

    @Test
    void sourceChangeRegeneration() throws IOException {
        var root = projectsDir.resolve("source-change-regeneration");
        var source = root.resolve("src/main/java/example/Main.java");
        var first = runner(root, "clean", "jar").build();
        assertTask(first, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertThat(Files.readString(source), containsString("class Removed"));
        var binding = root.resolve("build/generated/sources/helidon/main/example/ApplicationBinding.java");
        assertThat(Files.readString(binding), containsString("Main_Removed__ServiceDescriptor"));

        var changed = runner(root, "rebuildAfterChange").build();
        assertTask(changed, ":applyUpdatedSources", TaskOutcome.SUCCESS);
        assertTask(changed, ":compileJava", TaskOutcome.SUCCESS);
        assertTask(changed, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(changed, ":jar", TaskOutcome.SUCCESS);
        assertThat(Files.readString(source), containsString("class Retained"));
        assertThat(Files.readString(source), not(containsString("class Removed")));
        assertThat(Files.readString(binding), containsString("Main_Retained__ServiceDescriptor"));
        assertThat(Files.readString(binding), not(containsString("Main_Removed__ServiceDescriptor")));
        assertThat(Files.exists(root.resolve("build/classes/java/main/example/Main$Removed.class")), is(false));
        assertThat(Files.exists(root.resolve(
                "build/classes/java/main/example/Main_Removed__ServiceDescriptor.class")), is(false));
        assertThat(Files.exists(root.resolve(
                "build/generated/sources/annotationProcessor/java/main/example/Main_Removed__ServiceDescriptor.java")),
                   is(false));
        try (var jar = new ZipFile(root.resolve("build/libs/source-change-regeneration.jar").toFile())) {
            assertThat(jar.getEntry("example/Main$Removed.class"), nullValue());
            assertThat(jar.getEntry("example/Main_Removed__ServiceDescriptor.class"), nullValue());
            assertThat(jar.getEntry("example/ApplicationBinding.class"), notNullValue());
        }

        var repeated = runner(root, "rebuildAfterChange").build();
        assertTask(repeated, ":applyUpdatedSources", TaskOutcome.SUCCESS);
        assertTask(repeated, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);

        runner(root, "clean").build();
        assertThat(Files.readString(source), containsString("class Removed"));
        assertThat(Files.readString(source), not(containsString("class Retained")));
        assertThat(Files.exists(root.resolve("build")), is(false));
    }

    @Test
    void classpathMetadataChange() throws IOException {
        var root = projectsDir.resolve("classpath-metadata-change");
        var source = root.resolve("library/src/main/java/library/Main.java");
        var first = runner(root, "clean", "jar").build();
        assertTask(first, ":library:compileJava", TaskOutcome.SUCCESS);
        assertTask(first, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(first, ":jar", TaskOutcome.SUCCESS);
        assertThat(Files.readString(source), containsString("class OriginalService"));
        var binding = root.resolve("build/generated/sources/helidon/main/example/ApplicationBinding.java");
        assertThat(Files.readString(binding), containsString("Main_OriginalService__ServiceDescriptor"));

        var changed = runner(root, "rebuildAfterChange").build();
        assertTask(changed, ":library:applyUpdatedSources", TaskOutcome.SUCCESS);
        assertTask(changed, ":library:compileJava", TaskOutcome.SUCCESS);
        assertTask(changed, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(changed, ":jar", TaskOutcome.SUCCESS);
        assertThat(Files.readString(source), containsString("class ReplacementService"));
        assertThat(Files.readString(source), not(containsString("class OriginalService")));
        assertThat(Files.readString(binding), containsString("Main_ReplacementService__ServiceDescriptor"));
        assertThat(Files.readString(binding), not(containsString("Main_OriginalService__ServiceDescriptor")));
        assertThat(Files.exists(root.resolve(
                "library/build/classes/java/main/library/Main$OriginalService.class")), is(false));
        assertThat(Files.exists(root.resolve(
                "library/build/classes/java/main/library/Main_OriginalService__ServiceDescriptor.class")), is(false));
        assertThat(Files.exists(root.resolve(
                "library/build/classes/java/main/library/Main$ReplacementService.class")), is(true));
        assertThat(Files.exists(root.resolve(
                "library/build/classes/java/main/library/Main_ReplacementService__ServiceDescriptor.class")), is(true));
        assertThat(Files.exists(root.resolve(
                "library/build/generated/sources/annotationProcessor/java/main/library/"
                        + "Main_OriginalService__ServiceDescriptor.java")), is(false));

        var repeated = runner(root, "rebuildAfterChange").build();
        assertTask(repeated, ":library:applyUpdatedSources", TaskOutcome.SUCCESS);
        assertTask(repeated, ":library:compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);

        runner(root, "clean").build();
        assertThat(Files.readString(source), containsString("class OriginalService"));
        assertThat(Files.readString(source), not(containsString("class ReplacementService")));
        assertThat(Files.exists(root.resolve("build")), is(false));
        assertThat(Files.exists(root.resolve("library/build")), is(false));
    }

    @Test
    void bindingNameChange() throws IOException {
        Path root = projectsDir.resolve("binding-name-change");
        var source = root.resolve("src/main/java/example/Main.java");
        var first = runner(root, "clean", "jar").build();
        assertTask(first, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertThat(Files.readString(source), containsString("@Service.GenerateBinding\n"));
        var oldSource = root.resolve("build/generated/sources/helidon/main/example/ApplicationBinding.java");
        assertThat(Files.exists(oldSource), is(true));

        var changed = runner(root, "rebuildAfterChange", "-PbindingName=RenamedBinding").build();
        assertTask(changed, ":applyUpdatedSources", TaskOutcome.SUCCESS);
        assertTask(changed, ":compileJava", TaskOutcome.SUCCESS);
        assertTask(changed, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(changed, ":jar", TaskOutcome.SUCCESS);
        assertThat(Files.readString(source), containsString("@Service.GenerateBinding(\"RenamedBinding\")"));
        var renamedSource = root.resolve("build/generated/sources/helidon/main/example/RenamedBinding.java");
        assertThat(Files.exists(oldSource), is(false));
        assertThat(Files.exists(renamedSource), is(true));
        assertThat(Files.readString(renamedSource), containsString("Main_MainService__ServiceDescriptor"));
        assertThat(Files.exists(root.resolve(
                "build/classes/java/main/example/ApplicationBinding.class")), is(false));
        assertThat(Files.exists(root.resolve(
                "build/classes/helidon/main/example/ApplicationBinding.class")), is(false));
        try (var jar = new ZipFile(root.resolve("build/libs/binding-name-change.jar").toFile())) {
            assertThat(jar.getEntry("example/ApplicationBinding.class"), nullValue());
            assertThat(jar.getEntry("example/RenamedBinding.class"), notNullValue());
        }

        var repeated = runner(root, "rebuildAfterChange", "-PbindingName=RenamedBinding").build();
        assertTask(repeated, ":applyUpdatedSources", TaskOutcome.SUCCESS);
        assertTask(repeated, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);

        runner(root, "clean").build();
        assertThat(Files.readString(source), containsString("@Service.GenerateBinding\n"));
        assertThat(Files.readString(source), not(containsString("RenamedBinding")));
        assertThat(Files.exists(root.resolve("build")), is(false));
    }

    @Test
    void invalidScopeFailure() {
        var root = projectsDir.resolve("invalid-scope-failure");
        var failure = runner(root, "clean", "jar").buildAndFail();
        assertTask(failure, ":generateApplicationBindings", TaskOutcome.FAILED);
        assertThat(failure.getOutput(), containsString("request scoped"));
        assertThat(failure.getOutput(), containsString("RequestScoped"));
        assertThat(failure.getOutput(), containsString("Broken"));
    }

    @Test
    void invalidScopeWarning() throws IOException {
        var root = projectsDir.resolve("invalid-scope-warning");
        runner(root, "clean", "compileJava").build();
        var raw = root.resolve("build/classes/java/main/example/ApplicationBinding.class");
        byte[] placeholder = Files.readAllBytes(raw);
        var result = runner(root, "jar").build();
        assertTask(result, ":generateApplicationBindings", TaskOutcome.SUCCESS);
        assertTask(result, ":jar", TaskOutcome.SUCCESS);
        assertThat(result.getOutput(), containsString("Failed to generate application binding:"));
        assertThat(result.getOutput(), containsString("request scoped"));
        assertThat(result.getOutput(), containsString("RequestScoped"));
        assertThat(result.getOutput(), containsString("Broken"));
        assertThat(Files.readAllBytes(raw), is(placeholder));
        assertThat(Files.exists(root.resolve(
                "build/classes/helidon/main/example/ApplicationBinding.class")), is(false));
        try (var jar = new ZipFile(root.resolve("build/libs/invalid-scope-warning.jar").toFile())) {
            assertThat(bindingCount(jar, "example/ApplicationBinding.class"), is(1L));
            assertThat(jar.getInputStream(jar.getEntry("example/ApplicationBinding.class")).readAllBytes(),
                       is(placeholder));
        }

        var repeated = runner(root, "jar").build();
        assertTask(repeated, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":generateApplicationBindings", TaskOutcome.UP_TO_DATE);
        assertTask(repeated, ":jar", TaskOutcome.UP_TO_DATE);
    }

    GradleRunner runner(Path directory, String... tasks) {
        var arguments = new ArrayList<>(List.of(tasks));
        arguments.addAll(List.of("--stacktrace", "--offline", "--console=plain", "--parallel", "--max-workers=2"));
        var runner = GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withTestKitDir(testKitDir.toFile())
                .withArguments(arguments);
        return runner.withGradleVersion("9.8.1");
    }

    static long bindingCount(ZipFile jar, String path) {
        return jar.stream().filter(entry -> entry.getName().equals(path)).count();
    }

    static void assertTask(BuildResult result, String name, TaskOutcome expected) {
        BuildTask task = result.task(name);
        assertThat("Missing task " + name + " in:\n" + result.getOutput(), task, notNullValue());
        assertThat(name + " in:\n" + result.getOutput(), task.getOutcome(), is(expected));
    }
}
