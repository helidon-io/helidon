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
package io.helidon.codegen.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import io.helidon.common.Api;

import org.intellij.lang.annotations.Language;

/**
 * Fluent facility to invoke the Java compiler programmatically for testing.
 * <p>
 * <b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 * </p>
 */
@Api.Internal
public final class TestCompiler {

    private final List<Supplier<Processor>> processors;
    private final List<Path> classpath;
    private final List<Path> modulepath;
    private final List<String> opts;
    private final List<JavaFileObject> sources;
    private final Path workDir;
    private final boolean printDiagnostics;

    private TestCompiler(Builder builder) {
        this.processors = List.copyOf(builder.processors);
        this.classpath = List.copyOf(builder.classpath);
        this.modulepath = List.copyOf(builder.modulepath);
        this.opts = List.copyOf(builder.opts);
        this.sources = List.copyOf(builder.sources);
        if (builder.workDir == null) {
            this.workDir = TestPaths.newWorkDir(it -> {
                var cls = it.getDeclaringClass();
                return !cls.equals(TestCompiler.Builder.class)  && !cls.equals(TestCompiler.class);
            });
        } else {
            this.workDir = builder.workDir;
        }
        this.printDiagnostics = builder.printDiagnostics;
    }

    /**
     * Create a new builder.
     *
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Invoke the compiler.
     *
     * @return compile result
     */
    public Result compile() {
        try {
            var compiler = ToolProvider.getSystemJavaCompiler();
            var diagnostics = new DiagnosticCollector<>();
            var manager = compiler.getStandardFileManager(diagnostics, null, null);
            var classOutput = workDir.resolve("classes");
            if (!Files.exists(classOutput)) {
                Files.createDirectories(classOutput);
            }
            var sourceOuput = workDir.resolve("generated-sources");
            if (!Files.exists(sourceOuput)) {
                Files.createDirectories(sourceOuput);
            }
            manager.setLocationFromPaths(StandardLocation.CLASS_PATH, classpath);
            manager.setLocationFromPaths(StandardLocation.MODULE_PATH, modulepath);
            manager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutput));
            manager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(sourceOuput));
            var task = compiler.getTask(null, manager, diagnostics, opts, null, sources);
            var processors = this.processors.stream()
                    .map(Supplier::get)
                    .toList();
            task.setProcessors(processors);
            var success = task.call();
            var messages = new ArrayList<String>();
            for (var diagnostic : diagnostics.getDiagnostics()) {
                var msg = diagnostic.toString();
                if (printDiagnostics) {
                    System.err.println(msg);
                }
                messages.add(msg);
            }
            return new ResultImpl(success, classOutput, sourceOuput, messages);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Test compilation result.
     */
    public interface Result {

        /**
         * Whether the compilation task was successful.
         *
         * @return {@code true} if successful, {@code false} otherwise
         */
        boolean success();

        /**
         * Get the class output location.
         *
         * @return location
         */
        Path classOutput();

        /**
         * Get the source output location.
         *
         * @return location
         */
        Path sourceOutput();

        /**
         * Get the rendered diagnostics.
         *
         * @return list of diagnostic messages
         */
        List<String> diagnostics();
    }

    /**
     * Fluent build for {@link TestCompiler}.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, TestCompiler> {

        private final List<Supplier<Processor>> processors = new ArrayList<>();
        private final List<Path> classpath = new ArrayList<>();
        private final List<Path> modulepath = new ArrayList<>();
        private final List<String> opts = new ArrayList<>();
        private final List<JavaFileObject> sources = new ArrayList<>();

        private Path workDir;
        private boolean printDiagnostics = true;

        /**
         * Populate this builder from a compiler instance.
         *
         * @param compiler compiler instance
         * @return this builder
         */
        public Builder from(TestCompiler compiler) {
            this.processors.addAll(compiler.processors);
            this.classpath.addAll(compiler.classpath);
            this.modulepath.addAll(compiler.modulepath);
            this.opts.addAll(compiler.opts);
            this.sources.addAll(compiler.sources);
            this.workDir = compiler.workDir;
            this.printDiagnostics = compiler.printDiagnostics;
            return this;
        }

        /**
         * Processors to use during compilation.
         *
         * @param processors processors to replace current configured value
         * @return this builder
         */
        public Builder processors(List<Processor> processors) {
            this.processors.clear();
            return addProcessors(processors);
        }

        /**
         * Add processor.
         *
         * @param processor processor
         * @return this builder
         */
        public Builder addProcessor(Processor processor) {
            Objects.requireNonNull(processor, "processor is null");

            this.processors.add(() -> processor);
            return this;
        }

        /**
         * Add processor.
         *
         * @param processor processor supplier
         * @return this builder
         */
        public Builder addProcessor(Supplier<Processor> processor) {
            Objects.requireNonNull(processor, "processor is null");

            this.processors.add(processor);
            return this;
        }

        /**
         * Add processors.
         *
         * @param processors processors
         * @return this builder
         */
        public Builder addProcessors(List<Processor> processors) {
            for (var processor : processors) {
                this.processors.add(() -> processor);
            }
            return this;
        }

        /**
         * Class-path entries.
         *
         * @param classes classes used to derive locations to replace configured value
         * @return this builder
         * @see #classpathEntries(java.util.List)
         */
        public Builder classpath(List<Class<?>> classes) {
            this.classpath.clear();
            return addClasspath(classes);
        }

        /**
         * Add class-path entry.
         *
         * @param clazz class used to derive location
         * @return this builder
         */
        public Builder addClasspath(Class<?> clazz) {
            Objects.requireNonNull(clazz, "clazz is null");

            classpath.add(TestPaths.paths(clazz));
            return this;
        }

        /**
         * Add class-path entries.
         *
         * @param classes classes used to derive locations
         * @return this builder
         */
        public Builder addClasspath(List<Class<?>> classes) {
            for (var cls : classes) {
                classpath.add(TestPaths.paths(cls));
            }
            return this;
        }

        /**
         * Class-path entries.
         *
         * @param paths paths to replace configured value
         * @return this builder
         * @see #classpath(java.util.List)
         */
        public Builder classpathEntries(List<Path> paths) {
            classpath.clear();
            return addClasspathEntries(paths);
        }

        /**
         * Add class-path entry.
         *
         * @param path path
         * @return this builder
         */
        public Builder addClasspathEntry(Path path) {
            Objects.requireNonNull(path, "path is null");

            this.classpath.add(path);
            return this;
        }

        /**
         * Add class-path entries.
         *
         * @param paths paths
         * @return this builder
         */
        public Builder addClasspathEntries(List<Path> paths) {
            Objects.requireNonNull(paths, "paths is null");

            classpath.addAll(paths);
            return this;
        }

        /**
         * Add module-path entry.
         *
         * @param clazz class used to derive locations
         * @return this builder
         */
        public Builder addModulepath(Class<?> clazz) {
            Objects.requireNonNull(clazz, "clazz is null");

            modulepath.add(TestPaths.paths(clazz));
            return this;
        }

        /**
         * Module-path entries.
         *
         * @param classes classes used to derive locations
         * @return this builder
         */
        public Builder addModulepath(List<Class<?>> classes) {
            for (var cls : classes) {
                modulepath.add(TestPaths.paths(cls));
            }
            return this;
        }

        /**
         * Add module-path entries.
         *
         * @param classes classes used to derive locations to replace configured value
         * @return this builder
         * @see #modulepathEntries(java.util.List)
         */
        public Builder modulepath(List<Class<?>> classes) {
            modulepath.clear();
            return addModulepath(classes);
        }

        /**
         * Add module-path entry.
         *
         * @param path path
         * @return this builder
         */
        public Builder addModulepathEntry(Path path) {
            Objects.requireNonNull(path, "path is null");

            this.modulepath.add(path);
            return this;
        }

        /**
         * Add module-path entries.
         *
         * @param paths paths
         * @return this builder
         */
        public Builder addModulepathEntries(List<Path> paths) {
            Objects.requireNonNull(paths, "paths is null");

            modulepath.addAll(paths);
            return this;
        }

        /**
         * Module-path entries.
         *
         * @param paths paths to replace configured value
         * @return this builder
         * @see #modulepath(java.util.List)
         */
        public Builder modulepathEntries(List<Path> paths) {
            modulepath.clear();
            return addModulepathEntries(paths);
        }

        /**
         * Do annotation processing only.
         * <p>
         * This is a helper method that adds {@code -proc:only} option.
         *
         * @return this builder
         */
        public Builder procOnly() {
            return addOption("-proc:only");
        }

        /**
         * Add {@code --release} for the current runtime version.
         * <p>
         * This is a helper method that adds {@code --release} option and an option with the current Java version.
         *
         * @return this builder
         */
        public Builder currentRelease() {
            return addOption("--release", String.valueOf(Runtime.version().feature()));
        }

        /**
         * Add an option.
         *
         * @param option to add, must not contain spaces (otherwise it is multiple options)
         * @return this builder
         */
        public Builder addOption(String option) {
            Objects.requireNonNull(option, "option is null");

            opts.add(option);
            return this;
        }

        /**
         * Add an option with a name and a value (i.e. {@code --release 26}).
         *
         * @param option option name
         * @param value  option value
         * @return this builder
         */
        public Builder addOption(String option, String value) {
            Objects.requireNonNull(option, "option is null");
            Objects.requireNonNull(option, "value is null");

            opts.add(option);
            opts.add(value);
            return this;
        }

        /**
         * Compiler options.
         *
         * @param opts options to replace configured value
         * @return this builder
         */
        public Builder options(List<String> opts) {
            this.opts.clear();
            return addOptions(opts);
        }

        /**
         * Add compiler options.
         *
         * @param opts options
         * @return this builder
         */
        public Builder addOptions(List<String> opts) {
            Objects.requireNonNull(opts, "opts is null");

            this.opts.addAll(opts);
            return this;
        }

        /**
         * Add a source to compile.
         *
         * @param fileName file name
         * @param code     source code
         * @return this builder
         */
        public Builder addSource(String fileName, @Language("java") String code) {
            Objects.requireNonNull(fileName, "fileName is null");
            Objects.requireNonNull(fileName, "code is null");

            var uri = URI.create("string:///" + fileName);
            return addSource(new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return code;
                }
            });
        }

        /**
         * Add a source to compile.
         *
         * @param source source code
         * @return this instance
         */
        public Builder addSource(JavaFileObject source) {
            Objects.requireNonNull(source, "source is null");

            this.sources.add(source);
            return this;
        }

        /**
         * Set the working directory.
         *
         * @param workDir working directory
         * @return this builder
         */
        public Builder workDir(Path workDir) {
            Objects.requireNonNull(workDir, "workDir is null");

            this.workDir = workDir;
            return this;
        }

        /**
         * Whether to print the diagnostics to STDERR.
         *
         * @param printDiagnostics {@code true} to print the diagnostics to STDERR, {@code false} otherwise
         * @return this builder
         */
        public Builder printDiagnostics(boolean printDiagnostics) {
            this.printDiagnostics = printDiagnostics;
            return this;
        }

        @Override
        public TestCompiler build() {
            return new TestCompiler(this);
        }
    }

    private record ResultImpl(boolean success,
                              Path classOutput,
                              Path sourceOutput,
                              List<String> diagnostics) implements Result {
    }
}
