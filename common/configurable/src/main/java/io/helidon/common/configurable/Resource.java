/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.OptionalHelper;
import io.helidon.config.Config;

/**
 * A representation of a resource that can be
 * loaded from URL ({@link #from(URI)}), classpath ({@link #from(String)}), filesystem ({@link #from(Path)},
 * {@link #fromPath(String)}), content in config ({@link #from(Config, String)}, input stream({@link #from(InputStream, String)},
 * or direct value ({@link #fromContent(String, byte[])}, {@link #fromContent(String, String)}.
 *
 * The resource bytes can then be accessed by various methods, depending on the type required - either you can access bytes
 * ({@link #getBytes()}, {@link #getStream()}) or String ({@link #getString()}, {@link #getString(Charset)}).
 *
 * This class is not thread safe. If you want to use it across multiple threads,
 * there is an option: call {@link #cacheBytes()} before accessing it by other threads.
 * Note that this stores all the bytes in memory, so use with care!!!
 */
public interface Resource {
    /**
     * Load resource from URI provided.
     * Note that the loading is lazy - this method opens the stream,
     * but byte are ready only once you call {@link #getBytes()} and other
     * content retrieval-methods.
     *
     * @param uri Resource location
     * @return resource instance
     */
    static Resource from(URI uri) {
        return ResourceUtil.from(ResourceUtil.toIs(uri), uri.toString(), Source.URL);
    }

    /**
     * Load resource from URI provided with an explicit proxy server.
     * Note that the loading is lazy - this method opens the stream,
     * but byte are ready only once you call {@link #getBytes()} and other
     * content retrieval-methods.
     *
     * @param uri   Resource location
     * @param proxy HTTP proxy to use when accessing the URI
     * @return resource instance
     */
    static Resource from(URI uri, Proxy proxy) {
        return ResourceUtil.from(ResourceUtil.toIs(uri, proxy), uri.toString(), Source.URL);
    }

    /**
     * Load resource from classpath.
     * Note that the loading is lazy - this method opens the stream,
     * but byte are ready only once you call {@link #getBytes()} and other
     * content retrieval-methods.
     *
     * @param resourcePath classpath path
     * @return resource instance
     */
    static Resource from(String resourcePath) {
        return ResourceUtil.from(ResourceUtil.toIs(resourcePath), resourcePath, Source.CLASSPATH);
    }

    /**
     * Load resource from file system.
     * Note that the loading is lazy - this method opens the stream,
     * but byte are ready only once you call {@link #getBytes()} and other
     * content retrieval-methods.
     *
     * @param fsPath path of file system
     * @return resource instance
     */
    static Resource from(Path fsPath) {
        return ResourceUtil.from(ResourceUtil.toIs(fsPath), fsPath.toAbsolutePath().toString(), Source.FILE);
    }

    /**
     * Helper method for {@link #from(Path)} so you do not have to create the path yourself.
     *
     * @param fsPath String path to file system
     * @return resource instance
     */
    static Resource fromPath(String fsPath) {
        return from(Paths.get(fsPath));
    }

    /**
     * Load resource from binary content.
     *
     * @param description description of this resource (e.g. "keystore")
     * @param bytes       raw bytes of this resource
     * @return resource instance
     */
    static Resource fromContent(String description, byte[] bytes) {
        Objects.requireNonNull(bytes, "Resource bytes must not be null");
        return new ResourceImpl(Source.BINARY_CONTENT, description, bytes);
    }

    /**
     * Load resource from text content (e.g. this must not be base64 - use {@link #fromContent(String, byte[])} for binary).
     *
     * @param description description of this resource (e.g. "JWK-private")
     * @param string      string content of this resource, will be transformed to bytes using UTF-8 encoding
     * @return resource instance
     */
    static Resource fromContent(String description, String string) {
        Objects.requireNonNull(string, "Resource content must not be null");
        return new ResourceImpl(Source.CONTENT, description, string.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Load resource from binary content from an input stream, using {@link Source#UNKNOWN} type.
     *
     * @param inputStream input stream to raw bytes of this resource
     * @param description description of this resource (e.g. "keystore")
     * @return resource instance
     */
    static Resource from(InputStream inputStream, String description) {
        return ResourceUtil.from(inputStream, description, Source.UNKNOWN);
    }

    /**
     * Loads the resource from appropriate location based
     * on configuration and a key prefix.
     *
     * Keys supported (in this order):
     * <ul>
     * <li>prefix-path: File system path</li>
     * <li>prefix-resource-path: Class-path resource</li>
     * <li>prefix-url: URL to resource</li>
     * <li>prefix-content: actual content (base64 encoded bytes)</li>
     * <li>prefix-content-plain: actual content (string)</li>
     * <li>prefix-use-proxy: set to false not to go through a proxy; will only use proxy if it is defined used
     * "proxy-host" and optional "proxy-port" (defaults to 80); ignored unless URL is
     * used</li>
     * </ul>
     *
     * @param config    configuration
     * @param keyPrefix prefix of keys that may contain the location of resource
     * @return a resource ready to load from one of the locations or empty if neither is defined
     */
    static Optional<Resource> from(Config config, String keyPrefix) {
        return OptionalHelper.from(config.get(keyPrefix + "-path")
                                           .asOptionalString()
                                           .map(Paths::get)
                                           .map(Resource::from))
                .or(() -> config.get(keyPrefix + "-resource-path")
                        .asOptionalString()
                        .map(Resource::from))
                .or(() -> config.get(keyPrefix + "-url")
                        .asOptional(URI.class)
                        .map(uri -> config.get("proxy-host").value()
                                .map(proxyHost -> {
                                    if (config.get(keyPrefix + "-use-proxy").asBoolean(true)) {
                                        Proxy proxy = new Proxy(Proxy.Type.HTTP,
                                                                new InetSocketAddress(proxyHost,
                                                                                      config.get("proxy-port").asInt(80)));
                                        return Resource.from(uri, proxy);
                                    } else {
                                        return Resource.from(uri);
                                    }
                                })
                                .orElseGet(() -> Resource.from(uri))))
                .or(() -> config.get(keyPrefix + "-content-plain")
                        .asOptionalString()
                        .map(content -> Resource.fromContent("config:" + keyPrefix + "-content", content)))
                .or(() -> config.get(keyPrefix + "-content")
                        .asOptionalString()
                        .map(Base64.getDecoder()::decode)
                        .map(content -> Resource.fromContent("config:" + keyPrefix + "-content-b64", content)))
                .asOptional();
    }

    /**
     * Get an input stream to this resource.
     * If this method is called first, you actually get "THE" stream
     * to the resource and there will be no buffering done.
     * Once this happens, you cannot call any other method on this instance.
     * If you create the resource with byte content (e.g. from string), the content
     * will be pre-buffered.
     *
     * If you first call another method (such as {@link #getBytes()}, or explicitly buffer
     * this resource {@link #cacheBytes()}, you will get a new input stream to the
     * buffered bytes and may call this method multiple times.
     *
     * @return input stream ready to read bytes
     * @throws IllegalStateException in case the stream was already provided in previous call and was not buffered
     */
    InputStream getStream();

    /**
     * Get bytes of this resource.
     * Buffers the resource bytes in memory.
     *
     * @return bytes of this resource
     * @throws IllegalStateException in case the stream was already provided in previous call and was not buffered
     */
    byte[] getBytes();

    /**
     * Get string content of this resource.
     * Buffers the resource bytes in memory.
     *
     * @return string content of this instance, using UTF-8 encoding to decode bytes
     * @throws IllegalStateException in case the stream was already provided in previous call and was not buffered
     */
    String getString();

    /**
     * Get string content of this resource.
     * Buffers the resource bytes in memory.
     *
     * @param charset Character set (encoding) to use to decode bytes
     * @return string content of this instance, using your encoding to decode bytes
     * @throws IllegalStateException in case the stream was already provided in previous call and was not buffered
     */
    String getString(Charset charset);

    /**
     * Type of this resource, depends on the original source.
     *
     * @return type
     */
    Source getSourceType();

    /**
     * Location (or description) of this resource, depends on original source.
     * Depending on source, this may be:
     * <ul>
     * <li>FILE - absolute path to the file</li>
     * <li>CLASSPATH - resource path</li>
     * <li>URL - string of the URI</li>
     * <li>CONTENT - either config key or description provided to method {@link #fromContent(String, String)}</li>
     * <li>BINARY_CONTENT - either config key or description provided to {@link #fromContent(String, byte[])}</li>
     * <li>UNKNOWN - whatever description was provided to {@link #from(InputStream, String)}</li>
     * </ul>
     *
     * @return location of this resource (or other description of where it comes from)
     */
    String getLocation();

    /**
     * Caches the resource bytes in memory, so they can be repeatedly
     * accessed.
     * Be VERY careful with all methods that cache the bytes, as this may cause
     * a memory issue!
     */
    void cacheBytes();

    /**
     * Source of a {@link Resource}.
     */
    enum Source {
        /**
         * Resource was loaded from a file.
         */
        FILE,
        /**
         * Resource was loaded from classpath.
         */
        CLASSPATH,
        /**
         * Resource was loaded from URL.
         */
        URL,
        /**
         * Resource was created with string content.
         */
        CONTENT,
        /**
         * Resource was created with binary content.
         */
        BINARY_CONTENT,
        /**
         * Resource was created with an input stream without knowledge of type.
         */
        UNKNOWN
    }
}
