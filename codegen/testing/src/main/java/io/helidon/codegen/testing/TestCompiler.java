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
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.intellij.lang.annotations.Language;

/**
 * Fluent facility to invoke the Java compiler programmatically for testing.
 * <p>
 * <b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 * </p>
 */
@SuppressWarnings("ALL")
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
        this.workDir = builder.workDir;
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
            var dir = workDir;
            if (dir == null) {
                dir = TestPaths.newWorkDir(it -> !it.getDeclaringClass().equals(TestCompiler.class));
            }
            var compiler = ToolProvider.getSystemJavaCompiler();
            var diagnostics = new DiagnosticCollector<>();
            var manager = compiler.getStandardFileManager(diagnostics, null, null);
            var classOutput = dir.resolve("classes");
            if (!Files.exists(classOutput)) {
                Files.createDirectories(classOutput);
            }
            var sourceOuput = dir.resolve("generated-sources");
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
         * Add processors.
         *
         * @param processors processors
         * @return this builder
         */
        @SafeVarargs
        public final Builder processors(Supplier<Processor>... processors) {
            Collections.addAll(this.processors, processors);
            return this;
        }

        /**
         * Add processors.
         *
         * @param processors processors
         * @return this builder
         */
        public Builder processors(Processor... processors) {
            return processors(List.of(processors));
        }

        /**
         * Add processors.
         *
         * @param processors processors
         * @return this builder
         */
        public Builder processors(List<Processor> processors) {
            for (var processor : processors) {
                this.processors.add(() -> processor);
            }
            return this;
        }

        /**
         * Add class-path entries.
         *
         * @param classes classes used to derive locations
         * @return this builder
         */
        public Builder classpath(Class<?>... classes) {
            return classpath(List.of(classes));
        }

        /**
         * Add class-path entries.
         *
         * @param classes classes used to derive locations
         * @return this builder
         */
        public Builder classpath(List<Class<?>> classes) {
            for (var cls : classes) {
                classpath.add(TestPaths.paths(cls));
            }
            return this;
        }

        /**
         * Add class-path entries.
         *
         * @param paths paths
         * @return this builder
         */
        public Builder classpathEntries(Path... paths) {
            return classpathEntries(List.of(paths));
        }

        /**
         * Add class-path entries.
         *
         * @param paths paths
         * @return this builder
         */
        public Builder classpathEntries(List<Path> paths) {
            classpath.addAll(paths);
            return this;
        }

        /**
         * Add module-path entries.
         *
         * @param classes classes used to derive locations
         * @return this builder
         */
        public Builder modulepath(Class<?>... classes) {
            return modulepath(List.of(classes));
        }

        /**
         * Add module-path entries.
         *
         * @param classes classes used to derive locations
         * @return this builder
         */
        public Builder modulepath(List<Class<?>> classes) {
            for (var cls : classes) {
                modulepath.add(TestPaths.paths(cls));
            }
            return this;
        }

        /**
         * Add module-path entries.
         *
         * @param paths paths
         * @return this builder
         */
        public Builder modulepathEntries(Path... paths) {
            return modulepathEntries(List.of(paths));
        }

        /**
         * Add module-path entries.
         *
         * @param paths paths
         * @return this builder
         */
        public Builder modulepathEntries(List<Path> paths) {
            modulepath.addAll(paths);
            return this;
        }

        /**
         * Do annotation processing only.
         *
         * @return this builder
         */
        public Builder procOnly() {
            return opts("-proc:only");
        }

        /**
         * Add {@code --release} for the current runtime version.
         *
         * @return this builder
         */
        public Builder currentRelease() {
            return opts("--release", String.valueOf(Runtime.version().feature()));
        }

        /**
         * Add compiler options.
         *
         * @param opts options
         * @return this builder
         */
        public Builder opts(String... opts) {
            return opts(List.of(opts));
        }

        /**
         * Add compiler options.
         *
         * @param opts options
         * @return this builder
         */
        public Builder opts(List<String> opts) {
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
        public Builder source(String fileName, @Language("java") String code) {
            var uri = URI.create("string:///" + fileName);
            return source(new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
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
         * @return this instace
         */
        public Builder source(JavaFileObject source) {
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
