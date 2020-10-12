/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;

/**
 * Serves 'static content' (files) from filesystem or using a classloader to the {@link WebServer WebServer}
 * {@link Routing}. It is possible to {@link Routing.Builder#register(Service...) register} it on the routing.
 * <pre>{@code
 * // Serve content of attached '/static/pictures' on '/pics'
 * Routing.builder()
 *        .register("/pics", StaticContentSupport.create("/static/pictures"))
 *        .build()
 * }</pre>
 * <p>
 * Content is served ONLY on HTTP {@code GET} method.
 */
public class StaticContentSupport implements Service {

    private final StaticContentHandler handler;

    private int webServerCounter = 0;

    /**
     * Creates new instance.
     *
     * @param handler an handler to use
     */
    StaticContentSupport(StaticContentHandler handler) {
        this.handler = handler;
    }

    @Override
    public void update(Routing.Rules routing) {
        routing.onNewWebServer(new Consumer<WebServer>() {
            @Override
            public void accept(WebServer ws) {
                webServerStarted();
                ws.whenShutdown().thenRun(() -> webServerStopped());
            }
        });
        routing.get((req, res) -> handler.handle(Http.Method.GET, req, res));
        routing.head((req, res) -> handler.handle(Http.Method.HEAD, req, res));
    }

    private synchronized void webServerStarted() {
        webServerCounter++;
    }

    private synchronized void webServerStopped() {
        webServerCounter--;
        if (webServerCounter <= 0) {
            webServerCounter = 0;
            handler.releaseCache();
        }
    }

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
    public static Builder builder(String resourceRoot) {
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
    public static Builder builder(String resourceRoot, ClassLoader classLoader) {
        Objects.requireNonNull(resourceRoot, "Attribute resourceRoot is null!");
        return new Builder(resourceRoot, classLoader);
    }

    /**
     * Creates new builder with defined static content root as a path to the file system. Builder provides ability to define
     * more advanced configuration.
     *
     * @param root a root path.
     * @return a builder
     * @throws NullPointerException if {@code root} attribute is {@code null}
     */
    public static Builder builder(Path root) {
        Objects.requireNonNull(root, "Attribute root is null!");
        return new Builder(root);
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
    public static StaticContentSupport create(String resourceRoot) {
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
    public static StaticContentSupport create(String resourceRoot, ClassLoader classLoader) {
        return builder(resourceRoot, classLoader).build();
    }

    /**
     * Creates new instance with defined static content root as a path to the file system.
     *
     * @param root a root path.
     * @return created instance
     * @throws NullPointerException if {@code root} attribute is {@code null}
     */
    public static StaticContentSupport create(Path root) {
        return builder(root).build();
    }

    /**
     * Fluent builder of the StaticContent detailed parameters.
     */
    public static class Builder implements io.helidon.common.Builder<StaticContentSupport> {

        private final Path fsRoot;
        private final String clRoot;
        private final ClassLoader classLoader;

        private final Map<String, MediaType> specificContentTypes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private String welcomeFileName;
        private Path tmpDir;

        Builder(Path fsRoot) {
            Objects.requireNonNull(fsRoot, "Attribute fsRoot is null!");
            this.fsRoot = fsRoot;
            this.clRoot = null;
            this.classLoader = null;
        }

        Builder(String clRoot, ClassLoader classLoader) {
            Objects.requireNonNull(clRoot, "Attribute clRoot is null!");
            if (clRoot.startsWith("/")) {
                clRoot = clRoot.substring(1);
            }
            this.clRoot = clRoot;
            this.classLoader = classLoader;
            this.fsRoot = null;
        }

        /**
         * Sets a name of the "file" which will be returned if directory is requested.
         *
         * @param welcomeFileName a name of the welcome file
         * @return updated builder
         */
        public Builder welcomeFileName(String welcomeFileName) {
            this.welcomeFileName = welcomeFileName;
            return this;
        }

        /**
         * Sets custom temporary folder for extracting static content from a jar.
         *
         * @param tmpDir custom temporary folder
         * @return updated builder
         */
        public Builder tmpDir(Path tmpDir) {
            this.tmpDir = tmpDir;
            return this;
        }

        /**
         * Maps a filename extension to the response content type.
         *
         * @param filenameExtension a filename extension. The part after the last {code dot '.'} in the name.
         * @param contentType a mapped content type
         * @return updated builder
         * @throws NullPointerException if any parameter is {@code null}
         * @throws IllegalArgumentException if {@code filenameExtension} is empty
         */
        public Builder contentType(String filenameExtension, MediaType contentType) {
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
            return this;
        }

        /**
         * Builds new {@link StaticContentSupport} instance.
         *
         * @return a new instance
         */
        @Override
        public StaticContentSupport build() {
            ContentTypeSelector selector = new ContentTypeSelector(specificContentTypes);
            StaticContentHandler handler;
            if (fsRoot != null) {
                handler = FileSystemContentHandler.create(welcomeFileName, selector, fsRoot);
            } else if (clRoot != null) {
                handler = ClassPathContentHandler.create(welcomeFileName, selector, clRoot, tmpDir, classLoader);
            } else {
                throw new IllegalArgumentException("Builder was created without specified static content root!");
            }
            return new StaticContentSupport(handler);
        }

    }
}
