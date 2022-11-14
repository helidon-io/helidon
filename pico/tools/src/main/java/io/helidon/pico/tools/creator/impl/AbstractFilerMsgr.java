/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.tools.creator.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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

import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.CodeGenPaths;
import io.helidon.pico.tools.processor.TypeTools;
import io.helidon.pico.types.TypeName;

/**
 * Used to abstract processor based filer from direct filer (the later used via maven plugin and other tooling).
 */
public abstract class AbstractFilerMsgr implements Filer, Msgr {

    private static final System.Logger LOGGER = System.getLogger(AbstractFilerMsgr.class.getName());

    private final Filer filerDelegate;
    private final Msgr msgrDelegate;
    private final System.Logger logger;

    protected AbstractFilerMsgr(Filer filerDelegate, Msgr msgr) {
        this.filerDelegate = filerDelegate;
        this.msgrDelegate = msgr;
        this.logger = LOGGER;
    }

    protected AbstractFilerMsgr(System.Logger logger) {
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
    public static AbstractFilerMsgr createAnnotationBasedFiler(ProcessingEnvironment processingEnv, Msgr msgr) {
        return new AbstractFilerMsgr(Objects.requireNonNull(processingEnv.getFiler()), msgr) {};
    }

    /**
     * Create a direct filer, not from annotation processing.
     *
     * @param paths     the code paths
     * @param logger    the logger for messaging
     * @return the filer facade
     */
    public static AbstractFilerMsgr createDirectFiler(CodeGenPaths paths, System.Logger logger) {
        return new DirectFilerMsgr(Objects.requireNonNull(paths), logger) {};
    }

    @Override
    public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
        if (Objects.nonNull(filerDelegate)) {
            return filerDelegate.createSourceFile(name, originatingElements);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
        if (Objects.nonNull(filerDelegate)) {
            return filerDelegate.createClassFile(name, originatingElements);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public FileObject createResource(JavaFileManager.Location location,
                                     CharSequence moduleAndPkg,
                                     CharSequence relativeName,
                                     Element... originatingElements) throws IOException {
        if (Objects.nonNull(filerDelegate)) {
            return filerDelegate.createResource(location, moduleAndPkg, relativeName, originatingElements);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public FileObject getResource(JavaFileManager.Location location,
                                  CharSequence moduleAndPkg,
                                  CharSequence relativeName) throws IOException {
        if (Objects.nonNull(filerDelegate)) {
            return filerDelegate.getResource(location, moduleAndPkg, relativeName);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public void debug(String message, Throwable t) {
        if (Objects.nonNull(msgrDelegate)) {
            msgrDelegate.debug(message, t);
        }
        if (Objects.nonNull(logger)) {
            logger.log(System.Logger.Level.DEBUG, message, t);
        }
    }

    @Override
    public void log(String message) {
        if (Objects.nonNull(msgrDelegate)) {
            msgrDelegate.log(message);
        }
        if (Objects.nonNull(logger)) {
            logger.log(System.Logger.Level.INFO, message);
        }
    }

    @Override
    public void warn(String message, Throwable t) {
        if (Objects.nonNull(msgrDelegate)) {
            msgrDelegate.warn(message, t);
        }
        if (Objects.nonNull(logger)) {
            logger.log(System.Logger.Level.WARNING, message, t);
        }
    }

    @Override
    public void error(String message, Throwable t) {
        if (Objects.nonNull(msgrDelegate)) {
            msgrDelegate.warn(message, t);
        }
        if (Objects.nonNull(logger)) {
            logger.log(System.Logger.Level.ERROR, message, t);
        }
    }


    static class DirectFilerMsgr extends AbstractFilerMsgr {
        private final CodeGenPaths paths;

        DirectFilerMsgr(CodeGenPaths paths, System.Logger logger) {
            super(logger);
            this.paths = paths;
        }

        @Override
        public FileObject getResource(JavaFileManager.Location location,
                                      CharSequence moduleAndPkg,
                                      CharSequence relativeName) throws IOException {
            return getResource(location, moduleAndPkg, relativeName, true);
        }

        private FileObject getResource(JavaFileManager.Location location,
                                       CharSequence ignoreModuleAndPkg,
                                       CharSequence relativeName,
                                       boolean expectedToExist) throws IOException {
            if (StandardLocation.CLASS_OUTPUT != location) {
                throw new UnsupportedEncodingException(location + " is not supported for: " + relativeName);
            }

            final File outDir = new File(Objects.requireNonNull(paths.getOutputPath()));
            final File resourceFile = new File(outDir, relativeName.toString());
            if (expectedToExist && !resourceFile.exists()) {
                throw new NoSuchFileException(resourceFile.getPath());
            }

            return new DirectFileObject(resourceFile);
        }

        @Override
        public FileObject createResource(JavaFileManager.Location location,
                                                                   CharSequence moduleAndPkg,
                                                                   CharSequence relativeName,
                                                                   Element... originatingElements) throws IOException {
            return getResource(location, moduleAndPkg, relativeName, false);
        }

        @Override
        public JavaFileObject createSourceFile(CharSequence name, Element... originatingElement) {
            final File javaFile = toSourcePath(StandardLocation.SOURCE_OUTPUT, name.toString());
            return new DirectJavaFileObject(javaFile);
        }

        public File toSourcePath(JavaFileManager.Location location, String name) {
            return toSourcePath(location, DefaultTypeName.createFromTypeName(name));
        }

        public File toSourcePath(JavaFileManager.Location location, TypeName typeName) {
            String sourcePath;
            if (StandardLocation.SOURCE_PATH == location) {
                sourcePath = paths.getSourcePath();
            } else if (StandardLocation.SOURCE_OUTPUT == location) {
                sourcePath = paths.getGeneratedSourcesPath();
            } else {
                throw new ToolsException("Unable to determine location of " + typeName + " with " + location);
            }

            if (Objects.isNull(sourcePath)) {
                LOGGER.log(System.Logger.Level.WARNING, "sourcepath is not defined in " + paths);
                return null;
            }

            return new File(sourcePath, TypeTools.getFilePath(typeName));
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
            file.getParentFile().mkdirs();
            return new FileOutputStream(file);
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            return new InputStreamReader(openInputStream());
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return Files.readString(file.toPath());
        }

        @Override
        public Writer openWriter() throws IOException {
            return new OutputStreamWriter(openOutputStream());
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
        public boolean isNameCompatible(String simpleName, JavaFileObject.Kind kind) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NestingKind getNestingKind() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Modifier getAccessLevel() {
            throw new UnsupportedOperationException();
        }
    }

}
