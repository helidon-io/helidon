/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import io.helidon.common.types.TypeName;

/**
 * Used to abstract processor based filer from direct filer (the latter used via maven plugin and other tooling).
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public abstract class AbstractFilerMessager implements Filer, Messager {
    private static final System.Logger LOGGER = System.getLogger(AbstractFilerMessager.class.getName());

    private final Filer filerDelegate;
    private final Messager msgrDelegate;
    private final System.Logger logger;

    AbstractFilerMessager(Filer filerDelegate,
                          Messager msgr) {
        this.filerDelegate = filerDelegate;
        this.msgrDelegate = msgr;
        this.logger = LOGGER;
    }

    AbstractFilerMessager(System.Logger logger) {
        this.filerDelegate = null;
        this.msgrDelegate = null;
        this.logger = logger;
    }

    /**
     * Create an annotation based filer abstraction.
     *
     * @param processingEnv the processing env
     * @param msgr          the messager and error handler
     * @return the filer facade
     */
    public static AbstractFilerMessager createAnnotationBasedFiler(ProcessingEnvironment processingEnv,
                                                                   Messager msgr) {
        return new AbstractFilerMessager(Objects.requireNonNull(processingEnv.getFiler()), msgr) { };
    }

    /**
     * Create a direct filer, not from annotation processing.
     *
     * @param paths     the code paths
     * @param logger    the logger for messaging
     * @return the filer facade
     */
    public static AbstractFilerMessager createDirectFiler(CodeGenPaths paths,
                                                          System.Logger logger) {
        return new DirectFilerMessager(Objects.requireNonNull(paths), logger) { };
    }

    System.Logger logger() {
        return logger;
    }

    @Override
    public JavaFileObject createSourceFile(CharSequence name,
                                           Element... originatingElements) throws IOException {
        if (filerDelegate != null) {
            return filerDelegate.createSourceFile(name, originatingElements);
        }
        throw new IllegalStateException();
    }

    @Override
    public JavaFileObject createClassFile(CharSequence name,
                                          Element... originatingElements) throws IOException {
        if (filerDelegate != null) {
            return filerDelegate.createClassFile(name, originatingElements);
        }
        throw new IllegalStateException();
    }

    @Override
    public FileObject createResource(JavaFileManager.Location location,
                                     CharSequence moduleAndPkg,
                                     CharSequence relativeName,
                                     Element... originatingElements) throws IOException {
        if (filerDelegate != null) {
            return filerDelegate.createResource(location, moduleAndPkg, relativeName, originatingElements);
        }
        throw new IllegalStateException();
    }

    @Override
    public FileObject getResource(JavaFileManager.Location location,
                                  CharSequence moduleAndPkg,
                                  CharSequence relativeName) throws IOException {
        if (filerDelegate != null) {
            return filerDelegate.getResource(location, moduleAndPkg, relativeName);
        }
        throw new IllegalStateException();
    }

    @Override
    public void debug(String message) {
        if (msgrDelegate != null) {
            msgrDelegate.debug(message);
        }
        if (logger != null) {
            logger.log(System.Logger.Level.DEBUG, message);
        }
    }

    @Override
    public void debug(String message,
                      Throwable t) {
        if (msgrDelegate != null) {
            msgrDelegate.debug(message, t);
        }
        if (logger != null) {
            logger.log(System.Logger.Level.DEBUG, message, t);
        }
    }

    @Override
    public void log(String message) {
        if (msgrDelegate != null) {
            msgrDelegate.log(message);
        }
        if (logger != null) {
            logger.log(System.Logger.Level.INFO, message);
        }
    }

    @Override
    public void warn(String message) {
        if (msgrDelegate != null) {
            msgrDelegate.warn(message);
        }
        if (logger != null) {
            logger.log(System.Logger.Level.WARNING, message);
        }
    }

    @Override
    public void warn(String message,
                     Throwable t) {
        if (msgrDelegate != null) {
            msgrDelegate.warn(message, t);
        }
        if (logger != null) {
            logger.log(System.Logger.Level.WARNING, message, t);
        }
    }

    @Override
    public void error(String message,
                      Throwable t) {
        if (msgrDelegate != null) {
            msgrDelegate.warn(message, t);
        }
        if (logger != null) {
            logger.log(System.Logger.Level.ERROR, message, t);
        }
    }

    static class DirectFilerMessager extends AbstractFilerMessager {
        private final CodeGenPaths paths;

        DirectFilerMessager(CodeGenPaths paths,
                            System.Logger logger) {
            super(logger);
            this.paths = paths;
        }

        CodeGenPaths codeGenPaths() {
            return paths;
        }

        @Override
        public FileObject getResource(JavaFileManager.Location location,
                                      CharSequence moduleAndPkg,
                                      CharSequence relativeName) throws IOException {
            return getResource(location, moduleAndPkg, relativeName, false);
        }

        @Override
        public FileObject createResource(JavaFileManager.Location location,
                                         CharSequence moduleAndPkg,
                                         CharSequence relativeName,
                                         Element... originatingElements) throws IOException {
            return getResource(location, moduleAndPkg, relativeName, false);
        }

        @Override
        public JavaFileObject createSourceFile(CharSequence name,
                                               Element... originatingElement) {
            Path javaFilePath = Objects.requireNonNull(toSourcePath(StandardLocation.SOURCE_OUTPUT, name.toString()));
            return new DirectJavaFileObject(javaFilePath.toFile());
        }

        Path toSourcePath(JavaFileManager.Location location,
                          String name) {
            return toSourcePath(location, TypeName.create(name));
        }

        Path toSourcePath(JavaFileManager.Location location,
                          TypeName typeName) {
            String sourcePath;
            if (StandardLocation.SOURCE_PATH == location) {
                sourcePath = paths.sourcePath().orElse(null);
            } else if (StandardLocation.SOURCE_OUTPUT == location) {
                sourcePath = paths.generatedSourcesPath().orElse(null);
            } else {
                throw new ToolsException("Unable to determine location of " + typeName + " with " + location);
            }

            if (sourcePath == null) {
                LOGGER.log(System.Logger.Level.DEBUG, "sourcepath is not defined in " + paths);
                return null;
            }

            return new File(sourcePath, TypeTools.toFilePath(typeName)).toPath();
        }

        private FileObject getResource(JavaFileManager.Location location,
                                       CharSequence ignoreModuleAndPkg,
                                       CharSequence relativeName,
                                       boolean expectedToExist) throws IOException {
            if (StandardLocation.CLASS_OUTPUT != location) {
                throw new IllegalStateException(location + " is not supported for: " + relativeName);
            }

            File outDir = new File(paths.outputPath().orElseThrow());
            File resourceFile = new File(outDir, relativeName.toString());
            if (expectedToExist && !resourceFile.exists()) {
                throw new NoSuchFileException(resourceFile.getPath());
            }

            return new DirectFileObject(resourceFile);
        }
    }

    static class DirectFileObject implements FileObject {
        private final File file;

        DirectFileObject(File file) {
            this.file = file;
        }

        @Override
        public URI toUri() {
            return file.toURI();
        }

        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return new FileInputStream(file);
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            Path parent = Paths.get(file.getParent());
            Files.createDirectories(parent);
            return new FileOutputStream(file);
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            return new InputStreamReader(openInputStream(), StandardCharsets.UTF_8);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        }

        @Override
        public Writer openWriter() throws IOException {
            return new OutputStreamWriter(openOutputStream(), StandardCharsets.UTF_8);
        }

        @Override
        public long getLastModified() {
            return file.lastModified();
        }

        @Override
        public boolean delete() {
            return file.delete();
        }

        @Override
        public String toString() {
            return String.valueOf(file);
        }
    }

    static class DirectJavaFileObject extends DirectFileObject implements JavaFileObject {
        DirectJavaFileObject(File javaFile) {
            super(javaFile);
        }

        @Override
        public JavaFileObject.Kind getKind() {
            return JavaFileObject.Kind.SOURCE;
        }

        @Override
        public boolean isNameCompatible(String simpleName,
                                        JavaFileObject.Kind kind) {
            throw new IllegalStateException();
        }

        @Override
        public NestingKind getNestingKind() {
            throw new IllegalStateException();
        }

        @Override
        public Modifier getAccessLevel() {
            throw new IllegalStateException();
        }
    }

}
