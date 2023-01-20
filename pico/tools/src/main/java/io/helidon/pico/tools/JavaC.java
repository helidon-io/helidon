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

package io.helidon.pico.tools;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Simple wrapper for compilation, and capturing diagnostic output.
 */
class JavaC {
    private static final System.Logger LOGGER = System.getLogger(JavaC.class.getName());

    private final List<Path> classpath = new ArrayList<>();
    private final List<Path> sourcepath = new ArrayList<>();
    private final List<Path> modulepath = new ArrayList<>();
    private final List<String> commandLineArgs = new ArrayList<>();
    private String source = AbstractCreator.DEFAULT_SOURCE;
    private String target = AbstractCreator.DEFAULT_TARGET;
    private File outputDirectory;
    private System.Logger logger = LOGGER;
    private Msgr messager;

    /**
     * @return The fluent builder for eventual compilation.
     */
    static Builder builder() {
        JavaC compiler = new JavaC();
        return compiler.new Builder();
    }

    /**
     * Terminates the builder by triggering compilation.
     *
     * @param applicationJavaFile the java file to compile
     * @return the result of the compilation
     */
    Result compile(File applicationJavaFile) {
        return new Result(applicationJavaFile);
    }

    String toClasspath() {
        if (!classpath.isEmpty()) {
            return CommonUtils.toPathString(classpath);
        }
        return null;
    }

    String toSourcepath() {
        if (!sourcepath.isEmpty()) {
            return CommonUtils.toPathString(sourcepath);
        }
        return null;
    }

    String toModulePath() {
        if (!modulepath.isEmpty()) {
            return CommonUtils.toPathString(modulepath);
        }
        return null;
    }


    class Builder {
        private boolean closed;

        private Builder() {
        }

        Builder outputDirectory(File outputDirectory) {
            assert (!closed);
            JavaC.this.outputDirectory = outputDirectory;
            return this;
        }

        Builder classpath(List<Path> classpath) {
            assert (!closed);
            JavaC.this.classpath.clear();
            JavaC.this.classpath.addAll(classpath);
            return this;
        }

        Builder sourcepath(List<Path> sourcepath) {
            assert (!closed);
            JavaC.this.sourcepath.clear();
            JavaC.this.sourcepath.addAll(sourcepath);
            return this;
        }

        Builder modulepath(List<Path> modulepath) {
            assert (!closed);
            JavaC.this.modulepath.clear();
            JavaC.this.modulepath.addAll(modulepath);
            return this;
        }

        Builder source(String source) {
            assert (!closed);
            JavaC.this.source = Objects.isNull(source) ? AbstractCreator.DEFAULT_SOURCE : source;
            return this;
        }

        Builder target(String target) {
            assert (!closed);
            JavaC.this.target = Objects.isNull(target) ? AbstractCreator.DEFAULT_TARGET : target;
            return this;
        }

        Builder commandLineArgs(List<String> commandLineArgs) {
            assert (!closed);
            JavaC.this.commandLineArgs.clear();
            JavaC.this.commandLineArgs.addAll(commandLineArgs);
            return this;
        }

        Builder logger(System.Logger logger) {
            assert (!closed);
            JavaC.this.logger = logger;
            return this;
        }

        Builder messager(Msgr messager) {
            assert (!closed);
            JavaC.this.messager = messager;
            return this;
        }

        JavaC build() {
            assert (outputDirectory == null || outputDirectory.exists());
            closed = true;
            return JavaC.this;
        }
    }

    @SuppressWarnings("rawtypes")
    class Result implements DiagnosticListener {
        private final List<Diagnostic<?>> diagList = new ArrayList<>();
        private boolean isSuccessful = true;
        private boolean hasWarnings = false;

        @SuppressWarnings("unchecked")
        private Result(
                File applicationJavaFile) {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(this, null, null);

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
                optionList.add(outputDirectory.getPath());
            }

            List<File> filesToCompile = new LinkedList<>();
            filesToCompile.add(applicationJavaFile);
            if (!modulepath.isEmpty()) {
                modulepath.forEach(path -> {
                    File pathToPossibleModuleInfo = new File(path.toFile(), ModuleUtils.REAL_MODULE_INFO_JAVA_NAME);
                    if (pathToPossibleModuleInfo.exists()) {
                        filesToCompile.add(pathToPossibleModuleInfo);
                    }
                });
            }

            Iterable<? extends JavaFileObject> compilationUnit = fileManager
                    .getJavaFileObjectsFromFiles(filesToCompile);
            JavaCompiler.CompilationTask task = compiler
                    .getTask(null, fileManager, this, optionList, null, compilationUnit);

            if (messager != null) {
                messager.debug("javac " + CommonUtils.toString(optionList, null, " ") + " " + applicationJavaFile);
            }

            Boolean result = task.call();
            // we do it like this to allow for warnings to be treated as errors
            if (result != null && !result) {
                isSuccessful = false;
            }
        }

        public boolean isSuccessful() {
            return isSuccessful;
        }

        @SuppressWarnings("unused")
        public boolean hasWarnings() {
            return hasWarnings;
        }

        public ToolsException maybeGenerateError() {
            if (!isSuccessful()) {
                return new ToolsException("creator compilation error");
            }
            return null;
        }

        @Override
        public void report(
                Diagnostic diagnostic) {
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

            if (messager == null) {
                logger.log(level, diagnostic);
                return;
            }

            String message = diagnostic.toString();
            if (System.Logger.Level.ERROR == level) {
                messager.error(message, null);
            } else if (System.Logger.Level.WARNING == level) {
                messager.debug(message, null);
            } else {
                messager.debug(message);
            }
        }
    }

}
