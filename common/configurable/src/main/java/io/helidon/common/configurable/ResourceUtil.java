/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.configurable;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import io.helidon.config.Config;

/**
 * Utilities to move private static methods from interface,
 * as javadoc fails when using source 8.
 */
final class ResourceUtil {
    private static final int DEFAULT_PROXY_PORT = 80;

    private ResourceUtil() {
    }

    /**
     * Load resource from binary content from an input stream.
     *
     * @param inputStream    input stream to raw bytes of this resource
     * @param description    description of this resource (e.g. "keystore")
     * @param resourceSource type of this resource to provide more helpful error messages
     * @return resource instance
     */
    static Resource from(InputStream inputStream, String description, Resource.Source resourceSource) {
        return new ResourceImpl(resourceSource, description, inputStream);
    }

    /**
     * Create input stream for a Path.
     *
     * @param fsPath path on file system
     * @return stream to that path
     */
    static InputStream toIs(Path fsPath) {
        Objects.requireNonNull(fsPath, "Resource file system path must not be null");
        try {
            return Files.newInputStream(fsPath);
        } catch (IOException e) {
            throw new ResourceException("Resource on path: " + fsPath.toAbsolutePath() + " does not exist", e);
        }
    }

    /**
     * Create input stream for a resource on classpath.
     *
     * @param resPath resource path
     * @return stream to that resource
     */
    static InputStream toIs(String resPath) {
        Objects.requireNonNull(resPath, "Resource path must not be null");
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resPath);
        Objects.requireNonNull(is, "Resource path does not exist: " + resPath);
        return is;
    }

    /**
     * Create input stream for a URI.
     *
     * @param uri resource URI
     * @return stream of that URI
     */
    static InputStream toIs(URI uri) {
        try {
            return uri.toURL().openStream();
        } catch (IOException e) {
            throw new ResourceException("Failed to open stream to uri: " + uri, e);
        }
    }

    /**
     * Create input stream for a URI through a proxy.
     *
     * @param uri   resource URI
     * @param proxy HTTP proxy to access the URI
     * @return stream of that URI
     */
    static InputStream toIs(URI uri, Proxy proxy) {
        try {
            return uri.toURL().openConnection(proxy).getInputStream();
        } catch (IOException e) {
            throw new ResourceException("Failed to open stream to uri: " + uri, e);
        }
    }

    static Optional<Resource> fromConfigPath(Config config, String keyPrefix) {
        return config.get(keyPrefix + "-path")
                .asString()
                .map(Paths::get)
                .map(Resource::create);
    }

    static Optional<Resource> fromConfigB64Content(Config config, String keyPrefix) {
        return config.get(keyPrefix + "-content")
                .asString()
                .map(Base64.getDecoder()::decode)
                .map(content -> Resource.create("config:" + keyPrefix + "-content-b64", content));
    }

    static Optional<Resource> fromConfigContent(Config config, String keyPrefix) {
        return config.get(keyPrefix + "-content-plain")
                .asString()
                .map(content -> Resource.create("config:" + keyPrefix + "-content", content));
    }

    static Optional<Resource> fromConfigUrl(Config config, String keyPrefix) {
        return config.get(keyPrefix + "-url")
                .as(URI.class)
                .map(uri -> config.get("proxy-host").asString()
                        .map(proxyHost -> {
                            if (config.get(keyPrefix + "-use-proxy").asBoolean().orElse(true)) {
                                Proxy proxy = new Proxy(Proxy.Type.HTTP,
                                                        new InetSocketAddress(proxyHost,
                                                                              config.get("proxy-port").asInt().orElse(
                                                                                      DEFAULT_PROXY_PORT)));
                                return Resource.create(uri, proxy);
                            } else {
                                return Resource.create(uri);
                            }
                        })
                        .orElseGet(() -> Resource.create(uri)));
    }

    static Optional<Resource> fromConfigResourcePath(Config config, String keyPrefix) {
        return config.get(keyPrefix + "-resource-path")
                .asString()
                .map(Resource::create);
    }
}
