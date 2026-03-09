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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.intellij.lang.annotations.Language;

/**
 * Fluent API to invoke the Java compiler programmatically for testing.
 * <p>
 * <b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 * </p>
 */
@SuppressWarnings("ALL")
public class TestCompiler {

    /**
     * Compile result.
     *
     * @param success      success
     * @param classOutput  class output location
     * @param sourceOutput source output location
     * @param diagnostics  rendered diagnostics
     */
    public record Result(boolean success,
                         Path classOutput,
                         Path sourceOutput,
                         List<String> diagnostics) {
    }

    private final List<Supplier<Processor>> processors = new ArrayList<>();
    private final List<Path> classpath = new ArrayList<>();
    private final List<Path> modulepath = new ArrayList<>();
    private final List<String> opts = new ArrayList<>();
    private final List<JavaFileObject> sources = new ArrayList<>();
    private Path workDir;
    private boolean printDiagnostics = true;

    /**
     * Create a new instance.
     */
    public TestCompiler() {
        opts.add("--release");
        opts.add("21");
    }

    /**
     * Copy an existing instance.
     *
     * @param testCompiler instance to copy
     */
    public TestCompiler(TestCompiler testCompiler) {
        this.processors.addAll(testCompiler.processors);
        this.classpath.addAll(testCompiler.classpath);
        this.modulepath.addAll(testCompiler.modulepath);
        this.opts.addAll(testCompiler.opts);
        this.sources.addAll(testCompiler.sources);
        this.workDir = testCompiler.workDir;
        this.printDiagnostics = testCompiler.printDiagnostics;
    }

    /**
     * Add processors.
     *
     * @param processors processors
     * @return this instance
     */
    @SafeVarargs
    public final TestCompiler processors(Supplier<Processor>... processors) {
        Collections.addAll(this.processors, processors);
        return this;
    }

    /**
     * Add processors.
     *
     * @param processors processors
     * @return this instance
     */
    public TestCompiler processors(Processor... processors) {
        return processors(List.of(processors));
    }

    /**
     * Add processors.
     *
     * @param processors processors
     * @return this instance
     */
    public TestCompiler processors(List<Processor> processors) {
        for (var processor : processors) {
            this.processors.add(() -> processor);
        }
        return this;
    }

    /**
     * Add class-path entries.
     *
     * @param classes classes used to derive locations
     * @return this instance
     */
    public TestCompiler classpath(Class<?>... classes) {
        return classpath(List.of(classes));
    }

    /**
     * Add class-path entries.
     *
     * @param classes classes used to derive locations
     * @return this instance
     */
    public TestCompiler classpath(List<Class<?>> classes) {
        for (var cls : classes) {
            classpath.add(paths(cls));
        }
        return this;
    }

    /**
     * Add class-path entries.
     *
     * @param paths paths
     * @return this instance
     */
    public TestCompiler classpathEntries(Path... paths) {
        return classpathEntries(List.of(paths));
    }

    /**
     * Add class-path entries.
     *
     * @param paths paths
     * @return this instance
     */
    public TestCompiler classpathEntries(List<Path> paths) {
        classpath.addAll(paths);
        return this;
    }

    /**
     * Add module-path entries.
     *
     * @param classes classes used to derive locations
     * @return this instance
     */
    public TestCompiler modulepath(Class<?>... classes) {
        return modulepath(List.of(classes));
    }

    /**
     * Add module-path entries.
     *
     * @param classes classes used to derive locations
     * @return this instance
     */
    public TestCompiler modulepath(List<Class<?>> classes) {
        for (var cls : classes) {
            modulepath.add(paths(cls));
        }
        return this;
    }

    /**
     * Add module-path entries.
     *
     * @param paths paths
     * @return this instance
     */
    public TestCompiler modulepathEntries(Path... paths) {
        return modulepathEntries(List.of(paths));
    }

    /**
     * Add module-path entries.
     *
     * @param paths paths
     * @return this instance
     */
    public TestCompiler modulepathEntries(List<Path> paths) {
        modulepath.addAll(paths);
        return this;
    }

    /**
     * Do annotation processing only.
     *
     * @return this instance
     */
    public TestCompiler procOnly() {
        return opts("-proc:only");
    }

    /**
     * Add compiler options.
     *
     * @param opts options
     * @return this instance
     */
    public TestCompiler opts(String... opts) {
        return opts(List.of(opts));
    }

    /**
     * Add compiler options.
     *
     * @param opts options
     * @return this instance
     */
    public TestCompiler opts(List<String> opts) {
        this.opts.addAll(opts);
        return this;
    }

    /**
     * Add a source to compile.
     *
     * @param fileName file name
     * @param code     source code
     * @return this instance
     */
    public TestCompiler source(String fileName, @Language("java") String code) {
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
    public TestCompiler source(JavaFileObject source) {
        this.sources.add(source);
        return this;
    }

    /**
     * Set the working directory automatically based on the caller.
     *
     * @return this instance
     */
    public TestCompiler autoWorkDir() {
        this.workDir = newWorkDir(it -> !it.getDeclaringClass().equals(TestCompiler.class));
        return this;
    }

    /**
     * Set the working directory.
     *
     * @param workDir working directory
     * @return this instance
     */
    public TestCompiler workDir(Path workDir) {
        this.workDir = workDir;
        return this;
    }

    /**
     * Whether to print the diagnostics to STDERR.
     *
     * @param printDiagnostics {@code true} to print the diagnostics to STDERR, {@code false} otherwise
     * @return this instance
     */
    public TestCompiler printDiagnostics(boolean printDiagnostics) {
        this.printDiagnostics = printDiagnostics;
        return this;
    }

    /**
     * Invoke the compiler.
     *
     * @return compile result
     */
    public Result compile() {
        try {
            if (workDir == null) {
                throw new IllegalStateException("workDir is not set");
            }
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
            return new Result(success, classOutput, sourceOuput, messages);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path paths(Class<?> clazz) {
        try {
            return Paths.get(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path newWorkDir(Predicate<StackWalker.StackFrame> predicate) {
        var frame = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(stream -> stream.filter(it -> !it.getDeclaringClass().equals(TestCompiler.class) && predicate.test(it))
                        .findFirst())
                .orElseThrow();

        return newWorkDir(frame.getDeclaringClass(), frame.getMethodName());
    }

    private static Path newWorkDir(Class<?> declaringClass, String methodName) {
        try {
            var classesDir = Paths.get(declaringClass.getProtectionDomain().getCodeSource().getLocation().toURI());
            var targetDir = classesDir.getParent();
            if (targetDir == null) {
                throw new IllegalStateException("Unable to derive target directory");
            }

            // ensure unique directory
            String prefix = dirName(declaringClass.getSimpleName());
            String suffix = dirName(methodName);
            var workDir = targetDir.resolve(prefix).resolve(suffix);
            for (int i = 1; Files.exists(workDir); i++) {
                workDir = targetDir.resolve(prefix + "-" + i).resolve(suffix);
            }
            return Files.createDirectories(workDir);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String dirName(String str) {
        var sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (!sb.isEmpty()) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
