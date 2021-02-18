/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.staticcontent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

import io.helidon.common.http.MediaType;
import io.helidon.webserver.Service;

/**
 * Serves 'static content' (files) from filesystem or using a classloader to the {@link io.helidon.webserver.WebServer WebServer}
 * {@link io.helidon.webserver.Routing}. It is possible to
 * {@link io.helidon.webserver.Routing.Builder#register(io.helidon.webserver.Service...) register} it on the routing.
 * <pre>{@code
 * // Serve content of attached '/static/pictures' on '/pics'
 * Routing.builder()
 *        .register("/pics", StaticContentSupport.create("/static/pictures"))
 *        .build()
 * }</pre>
 * <p>
 * Content is served ONLY on HTTP {@code GET} method.
 */
public interface StaticContentSupport extends Service {
    /**
     * Creates new builder with defined static content root as a class-loader resource. Builder provides ability to define
     * more advanced configuration.
     * <p>
     * Current context classloader is used to load static content.
     *
     * @param resourceRoot a root resource path.
     * @return a builder
     * @throws NullPointerException if {@code resourceRoot} attribute is {@code null}
     */
    static ClassPathBuilder builder(String resourceRoot) {
        Objects.requireNonNull(resourceRoot, "Attribute resourceRoot is null!");
        return builder(resourceRoot, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates new builder with defined static content root as a class-loader resource. Builder provides ability to define
     * more advanced configuration.
     *
     * @param resourceRoot a root resource path.
     * @param classLoader a class-loader for the static content
     * @return a builder
     * @throws NullPointerException if {@code resourceRoot} attribute is {@code null}
     */
    static ClassPathBuilder builder(String resourceRoot, ClassLoader classLoader) {
        Objects.requireNonNull(resourceRoot, "Attribute resourceRoot is null!");
        return new ClassPathBuilder()
                .root(resourceRoot)
                .classLoader(classLoader);
    }

    /**
     * Creates new builder with defined static content root as a path to the file system. Builder provides ability to define
     * more advanced configuration.
     *
     * @param root a root path.
     * @return a builder
     * @throws NullPointerException if {@code root} attribute is {@code null}
     */
    static FileSystemBuilder builder(Path root) {
        Objects.requireNonNull(root, "Attribute root is null!");
        return new FileSystemBuilder()
                .root(root);
    }

    /**
     * Creates new instance with defined static content root as a class-loader resource.
     * <p>
     * Current context classloader is used to load static content.
     *
     * @param resourceRoot a root resource path.
     * @return created instance
     * @throws NullPointerException if {@code resourceRoot} attribute is {@code null}
     */
    static StaticContentSupport create(String resourceRoot) {
        return create(resourceRoot, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates new instance with defined static content root as a class-loader resource.
     *
     * @param resourceRoot a root resource path.
     * @param classLoader a class-loader for the static content
     * @return created instance
     * @throws NullPointerException if {@code resourceRoot} attribute is {@code null}
     */
    static StaticContentSupport create(String resourceRoot, ClassLoader classLoader) {
        return builder(resourceRoot, classLoader).build();
    }

    /**
     * Creates new instance with defined static content root as a path to the file system.
     *
     * @param root a root path.
     * @return created instance
     * @throws NullPointerException if {@code root} attribute is {@code null}
     */
    static StaticContentSupport create(Path root) {
        return builder(root).build();
    }

    /**
     * Fluent builder of the StaticContent detailed parameters.
     * @param <B> type of a subclass of a concrete builder
     */
    @SuppressWarnings("unchecked")
    abstract class Builder<B extends Builder<B>> implements io.helidon.common.Builder<StaticContentSupport> {
        private String welcomeFileName;
        private Function<String, String> resolvePathFunction = Function.identity();

        protected Builder() {
        }

        @Override
        public final StaticContentSupport build() {
            return doBuild();
        }

        /**
         * Build the actual instance.
         *
         * @return static content support
         */
        protected abstract StaticContentSupport doBuild();

        /**
         * Sets a name of the "file" which will be returned if directory is requested.
         *
         * @param welcomeFileName a name of the welcome file
         * @return updated builder
         */
        public B welcomeFileName(String welcomeFileName) {
            Objects.requireNonNull(welcomeFileName, "Welcome file cannot be null");
            if (welcomeFileName.isBlank()) {
                throw new IllegalArgumentException("Welcome file cannot be empty");
            }
            this.welcomeFileName = welcomeFileName;
            return (B) this;
        }

        /**
         * Map request path to resource path. Default uses the same path as requested.
         * This can be used to resolve all paths to a single file, or to filter out files.
         *
         * @param resolvePathFunction function
         * @return updated builder
         */
        public B pathMapper(Function<String, String> resolvePathFunction) {
            this.resolvePathFunction = resolvePathFunction;
            return (B) this;
        }

        String welcomeFileName() {
            return welcomeFileName;
        }

        Function<String, String> resolvePathFunction() {
            return resolvePathFunction;
        }
    }

    /**
     * Builder for file based static content supports, such as file based and classpath based.
     * @param <T> type of a subclass of a concrete builder
     */
    @SuppressWarnings("unchecked")
    abstract class FileBasedBuilder<T extends FileBasedBuilder<T>> extends StaticContentSupport.Builder<FileBasedBuilder<T>> {
        private final Map<String, MediaType> specificContentTypes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        /**
         * Maps a filename extension to the response content type.
         * To have a system wide configuration, you can use the service loader SPI
         * {@link io.helidon.common.media.type.spi.MediaTypeDetector}.
         *
         * This method can override {@link io.helidon.common.media.type.MediaTypes} detection
         * for static content handling only.
         *
         * @param filenameExtension a filename extension. The part after the last {code dot '.'} in the name.
         * @param contentType a mapped content type
         * @return updated builder
         * @throws NullPointerException if any parameter is {@code null}
         * @throws IllegalArgumentException if {@code filenameExtension} is empty
         */
        public T contentType(String filenameExtension, MediaType contentType) {
            Objects.requireNonNull(filenameExtension, "Parameter 'filenameExtension' is null!");
            Objects.requireNonNull(contentType, "Parameter 'contentType' is null!");
            filenameExtension = filenameExtension.trim();
            if (filenameExtension.startsWith(".")) {
                filenameExtension = filenameExtension.substring(1);
            }
            if (filenameExtension.isEmpty()) {
                throw new IllegalArgumentException("Parameter 'filenameExtension' cannot be empty!");
            }
            specificContentTypes.put(filenameExtension, contentType);
            return (T) this;
        }

        Map<String, MediaType> specificContentTypes() {
            return specificContentTypes;
        }
    }

    /**
     * Builder for class path based static content.
     */
    class ClassPathBuilder extends StaticContentSupport.FileBasedBuilder<ClassPathBuilder> {
        private String clRoot;
        private ClassLoader classLoader;
        private Path tmpDir;

        protected ClassPathBuilder() {
        }

        @Override
        protected StaticContentSupport doBuild() {
            return new ClassPathContentHandler(this);
        }

        ClassPathBuilder classLoader(ClassLoader cl) {
            this.classLoader = cl;
            return this;
        }

        ClassPathBuilder root(String root) {
            Objects.requireNonNull(root, "Attribute root is null!");
            String cleanRoot = root;
            if (cleanRoot.startsWith("/")) {
                cleanRoot = cleanRoot.substring(1);
            }
            while (cleanRoot.endsWith("/")) {
                cleanRoot = cleanRoot.substring(0, cleanRoot.length() - 1);
            }

            if (cleanRoot.isEmpty()) {
                throw new IllegalArgumentException("Cannot serve full classpath, please configure a classpath prefix");
            }

            this.clRoot = cleanRoot;

            return this;
        }

        /**
         * Sets custom temporary folder for extracting static content from a jar.
         *
         * @param tmpDir custom temporary folder
         * @return updated builder
         */
        public ClassPathBuilder tmpDir(Path tmpDir) {
            this.tmpDir = tmpDir;
            return this;
        }

        String root() {
            return clRoot;
        }

        ClassLoader classLoader() {
            return classLoader;
        }

        Path tmpDir() {
            return tmpDir;
        }
    }

    /**
     * Builder for file system based static content.
     */
    class FileSystemBuilder extends StaticContentSupport.FileBasedBuilder<FileSystemBuilder> {
        private Path root;

        protected FileSystemBuilder() {
        }

        @Override
        protected StaticContentSupport doBuild() {
            return new FileSystemContentHandler(this);
        }

        FileSystemBuilder root(Path root) {
            Objects.requireNonNull(root, "Attribute root is null!");
            this.root = root.toAbsolutePath().normalize();

            if (!(Files.exists(this.root) && Files.isDirectory(this.root))) {
                throw new IllegalArgumentException("Cannot create file system static content, path "
                                                           + this.root
                                                           + " does not exist or is not a directory");
            }
            return this;
        }

        Path root() {
            return root;
        }
    }
}
