/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.codegen.compiler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenLogger;
import io.helidon.codegen.ModuleInfo;

class JavaC {
    private final List<Path> classpath;
    private final List<Path> sourcepath;
    private final List<Path> modulepath;
    private final List<String> commandLineArgs;
    private final String source;
    private final String target;
    private final Path outputDirectory;
    private final CodegenLogger logger;

    private JavaC(CompilerOptions options) {
        this.classpath = options.classpath();
        this.sourcepath = options.sourcepath();
        this.modulepath = options.modulepath();
        this.commandLineArgs = options.commandLineArguments();
        this.source = options.source();
        this.target = options.target();
        this.outputDirectory = options.outputDirectory();
        this.logger = options.logger();
    }

    static JavaC create(CompilerOptions options) {
        return new JavaC(options);
    }

    /**
     * Terminates the builder by triggering compilation.
     *
     * @param sourceFiles the java file(s) to compile
     * @return the result of the compilation
     */
    Result compile(Path... sourceFiles) {
        Result result = new Result();
        doCompile(result, sourceFiles);
        return result;
    }

    String toClasspath() {
        if (!classpath.isEmpty()) {
            return classpath.stream()
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .collect(Collectors.joining(File.pathSeparator));
        }
        return null;
    }

    String toSourcepath() {
        if (!sourcepath.isEmpty()) {
            return sourcepath.stream()
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .collect(Collectors.joining(File.pathSeparator));
        }
        return null;
    }

    String toModulePath() {
        if (!modulepath.isEmpty()) {
            return modulepath.stream()
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .collect(Collectors.joining(File.pathSeparator));
        }
        return null;
    }

    private void doCompile(Result result, Path[] sourceFilesToCompile) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(result,
                                                                              null,
                                                                              StandardCharsets.UTF_8);

        List<String> optionList = new ArrayList<>();
        if (!classpath.isEmpty()) {
            optionList.add("-classpath");
            optionList.add(toClasspath());
        }
        if (!modulepath.isEmpty()) {
            optionList.add("--module-path");
            optionList.add(toModulePath());
        }
        if (!sourcepath.isEmpty()) {
            optionList.add("--source-path");
            optionList.add(toSourcepath());
        }
        if (source != null) {
            optionList.add("--source");
            optionList.add(source);
        }
        if (target != null) {
            optionList.add("--target");
            optionList.add(target);
        }
        optionList.addAll(commandLineArgs);
        if (outputDirectory != null) {
            optionList.add("-d");
            optionList.add(outputDirectory.toAbsolutePath().toString());
        }

        List<Path> filesToCompile = new ArrayList<>(Arrays.asList(sourceFilesToCompile));

        if (!modulepath.isEmpty()) {
            modulepath.forEach(path -> {
                Path pathToPossibleModuleInfo = path.resolve(ModuleInfo.FILE_NAME);
                if (Files.exists(pathToPossibleModuleInfo)) {
                    filesToCompile.add(pathToPossibleModuleInfo);
                }
            });
        }

        Iterable<? extends JavaFileObject> compilationUnit = fileManager
                .getJavaFileObjectsFromPaths(filesToCompile);
        JavaCompiler.CompilationTask task = compiler
                .getTask(null, fileManager, result, optionList, null, compilationUnit);

        if (logger != null) {
            logger.log(System.Logger.Level.DEBUG,
                       "javac "
                               + String.join(" ", optionList)
                               + " "
                               + Stream.of(sourceFilesToCompile).map(Path::toString).collect(Collectors.joining(" ")));
        }

        Boolean taskResult = task.call();
        // we do it like this to allow for warnings to be treated as errors
        if (taskResult != null && !taskResult) {
            result.isSuccessful = false;
        }
    }

    class Result implements DiagnosticListener<JavaFileObject> {
        private final List<Diagnostic<?>> diagList = new ArrayList<>();
        private boolean isSuccessful = true;
        private boolean hasWarnings = false;

        private Result() {
        }

        public boolean isSuccessful() {
            return isSuccessful;
        }

        @SuppressWarnings("unused")
        public boolean hasWarnings() {
            return hasWarnings;
        }

        public void maybeThrowError() {
            if (!isSuccessful()) {
                throw new CodegenException("Compilation error encountered:\n"
                                                   + diagList.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n")));
            }
        }

        @Override
        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            System.Logger.Level level;
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                level = System.Logger.Level.ERROR;
                isSuccessful = false;
            } else if (Diagnostic.Kind.MANDATORY_WARNING == diagnostic.getKind()
                    || Diagnostic.Kind.WARNING == diagnostic.getKind()) {
                level = System.Logger.Level.WARNING;
                hasWarnings = true;
            } else {
                level = System.Logger.Level.INFO;
            }
            diagList.add(diagnostic);

            logger.log(level, diagnostic.toString());
        }
    }

}
