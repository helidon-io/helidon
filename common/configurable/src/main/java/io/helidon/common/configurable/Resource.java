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

package io.helidon.common.configurable;

import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.DeprecatedConfig;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * A representation of a resource that can be
 * loaded from URL ({@link #create(URI)}), classpath ({@link #create(String)}), filesystem ({@link #create(Path)},
 * content in config ({@link #create(Config)}, input stream({@link #create(String, InputStream)},
 * or direct value ({@link #create(String, byte[])}, {@link #create(String, String)}.
 *
 * The resource bytes can then be accessed by various methods, depending on the type required - either you can access bytes
 * ({@link #bytes()}, {@link #stream()}) or String ({@link #string()}, {@link #string(Charset)}).
 *
 * This class is not thread safe. If you want to use it across multiple threads,
 * there is an option: call {@link #cacheBytes()} before accessing it by other threads.
 * Note that this stores all the bytes in memory, so use with care!!!
 */
@Configured
public interface Resource {
    /**
     * Load resource from URI provided.
     * Note that the loading is lazy - this method opens the stream,
     * but byte are ready only once you call {@link #bytes()} and other
     * content retrieval-methods.
     *
     * @param uri Resource location
     * @return resource instance
     */
    static Resource create(URI uri) {
        return ResourceUtil.from(ResourceUtil.toIs(uri), uri.toString(), Source.URL);
    }

    /**
     * Load resource from URI provided with an explicit proxy server.
     * Note that the loading is lazy - this method opens the stream,
     * but byte are ready only once you call {@link #bytes()} and other
     * content retrieval-methods.
     *
     * @param uri   Resource location
     * @param proxy HTTP proxy to use when accessing the URI
     * @return resource instance
     */
    static Resource create(URI uri, Proxy proxy) {
        return ResourceUtil.from(ResourceUtil.toIs(uri, proxy), uri.toString(), Source.URL);
    }

    /**
     * Load resource from classpath.
     * Note that the loading is lazy - this method opens the stream,
     * but byte are ready only once you call {@link #bytes()} and other
     * content retrieval-methods.
     *
     * @param resourcePath classpath path
     * @return resource instance
     */
    static Resource create(String resourcePath) {
        return ResourceUtil.from(ResourceUtil.toIs(resourcePath), resourcePath, Source.CLASSPATH);
    }

    /**
     * Load resource from file system.
     * Note that the loading is lazy - this method opens the stream,
     * but byte are ready only once you call {@link #bytes()} and other
     * content retrieval-methods.
     *
     * @param fsPath path of file system
     * @return resource instance
     */
    static Resource create(Path fsPath) {
        return ResourceUtil.from(ResourceUtil.toIs(fsPath), fsPath.toAbsolutePath().toString(), Source.FILE);
    }

    /**
     * Load resource from binary content.
     *
     * @param description description of this resource (e.g. "keystore")
     * @param bytes       raw bytes of this resource
     * @return resource instance
     */
    @ConfiguredOption(key = "content", description = "Base64 encoded content of the resource")
    static Resource create(String description, byte[] bytes) {
        Objects.requireNonNull(bytes, "Resource bytes must not be null");
        return new ResourceImpl(Source.BINARY_CONTENT, description, bytes);
    }

    /**
     * Load resource from text content (e.g. this must not be base64 - use {@link #create(String, byte[])} for binary).
     *
     * @param description description of this resource (e.g. "JWK-private")
     * @param string      string content of this resource, will be transformed to bytes using UTF-8 encoding
     * @return resource instance
     */
    static Resource create(String description, String string) {
        Objects.requireNonNull(string, "Resource content must not be null");
        return new ResourceImpl(Source.CONTENT, description, string.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Load resource from binary content from an input stream, using {@link Source#UNKNOWN} type.
     *
     * @param description description of this resource (e.g. "keystore")
     * @param inputStream input stream to raw bytes of this resource
     * @return resource instance
     */
    static Resource create(String description, InputStream inputStream) {
        return ResourceUtil.from(inputStream, description, Source.UNKNOWN);
    }

    /**
     * Loads the resource from appropriate location based
     * on configuration.
     *
     * Keys supported (in this order):
     * <ul>
     * <li>path: File system path</li>
     * <li>resource-path: Class-path resource</li>
     * <li>url: URL to resource</li>
     * <li>content: actual content (base64 encoded bytes)</li>
     * <li>content-plain: actual content (string)</li>
     * <li>use-proxy: set to false not to go through a proxy; will only use proxy if it is defined used
     * "proxy-host" and optional "proxy-port" (defaults to 80); ignored unless URL is used</li>
     * </ul>
     *
     * @param resourceConfig    configuration current node must be the node containing the location of the resource, by
     *                          convention in helidon, this should be on key named {@code resource}
     * @return a resource ready to load from one of the locations
     * @throws io.helidon.config.ConfigException in case this config does not define a resource configuration
     */
    @ConfiguredOption(key = "resource-path", description = "Classpath location of the resource.")
    @ConfiguredOption(key = "path", description = "File system path to the resource.")
    @ConfiguredOption(key = "content-plain", description = "Plain text content of the resource")
    @ConfiguredOption(key = "uri", type = URI.class, description = "URI of the resource.")
    @ConfiguredOption(key = "proxy-host", description = "Host of the proxy when using url.")
    @ConfiguredOption(key = "proxy-port", type = Integer.class, description = "Port of the proxy when using url.")
    @ConfiguredOption(key = "use-proxy",
                      type = Boolean.class,
                      description = "Whether to use proxy. Only used if proxy-host is defined as well.",
                      value = "true")
    static Resource create(Config resourceConfig) {
        return ResourceUtil.fromConfigPath(resourceConfig.get("path"))
                .or(() -> ResourceUtil.fromConfigResourcePath(resourceConfig.get("resource-path")))
                .or(() -> ResourceUtil.fromConfigUrl(DeprecatedConfig.get(resourceConfig, "uri", "url")))
                .or(() -> ResourceUtil.fromConfigContent(resourceConfig.get("content-plain")))
                .or(() -> ResourceUtil.fromConfigB64Content(resourceConfig.get("content")))
                .orElseThrow(() -> new ConfigException("Config is not a resource configuration on key: " + resourceConfig.key()
                                                               + ". The config must contain one of "
                                                               + "(path,resource-path,url,content-plain,content)"));
    }

    /**
     * Support for old API and configuration.
     *
     * @param config configuration
     * @param prefix prefix of the resource
     * @return resource if configured
     * @deprecated since 2.0.0 use {@link #create(io.helidon.config.Config)} instead (and change the configuration to use
     *  {@code .resource.type} instead of prefixes
     */
    @Deprecated
    static Optional<Resource> create(Config config, String prefix) {
        Optional<Resource> result = ResourceUtil.fromConfigPath(config.get(prefix + "-path"));
        if (result.isPresent()) {
            ResourceUtil.logPrefixed(config, prefix, "path");
            return result;
        }

        result = ResourceUtil.fromConfigResourcePath(config.get(prefix + "-resource-path"));
        if (result.isPresent()) {
            ResourceUtil.logPrefixed(config, prefix, "resource-path");
            return result;
        }

        result = ResourceUtil.fromConfigUrl(config.get(prefix + "-url"));
        if (result.isPresent()) {
            ResourceUtil.logPrefixed(config, prefix, "url");
            return result;
        }

        result = ResourceUtil.fromConfigContent(config.get(prefix + "-content-plain"));
        if (result.isPresent()) {
            ResourceUtil.logPrefixed(config, prefix, "content-plain");
            return result;
        }

        result = ResourceUtil.fromConfigB64Content(config.get(prefix + "-content"));
        if (result.isPresent()) {
            ResourceUtil.logPrefixed(config, prefix, "content");
            return result;
        }

        return result;
    }

    /**
     * Get an input stream to this resource.
     * If this method is called first, you actually get "THE" stream
     * to the resource and there will be no buffering done.
     * Once this happens, you cannot call any other method on this instance.
     * If you create the resource with byte content (e.g. from string), the content
     * will be pre-buffered.
     *
     * If you first call another method (such as {@link #bytes()}, or explicitly buffer
     * this resource {@link #cacheBytes()}, you will get a new input stream to the
     * buffered bytes and may call this method multiple times.
     *
     * @return input stream ready to read bytes
     * @throws IllegalStateException in case the stream was already provided in previous call and was not buffered
     */
    InputStream stream();

    /**
     * Get bytes of this resource.
     * Buffers the resource bytes in memory.
     *
     * @return bytes of this resource
     * @throws IllegalStateException in case the stream was already provided in previous call and was not buffered
     */
    byte[] bytes();

    /**
     * Get string content of this resource.
     * Buffers the resource bytes in memory.
     *
     * @return string content of this instance, using UTF-8 encoding to decode bytes
     * @throws IllegalStateException in case the stream was already provided in previous call and was not buffered
     */
    String string();

    /**
     * Get string content of this resource.
     * Buffers the resource bytes in memory.
     *
     * @param charset Character set (encoding) to use to decode bytes
     * @return string content of this instance, using your encoding to decode bytes
     * @throws IllegalStateException in case the stream was already provided in previous call and was not buffered
     */
    String string(Charset charset);

    /**
     * Type of this resource, depends on the original source.
     *
     * @return type
     */
    Source sourceType();

    /**
     * Location (or description) of this resource, depends on original source.
     * Depending on source, this may be:
     * <ul>
     * <li>FILE - absolute path to the file</li>
     * <li>CLASSPATH - resource path</li>
     * <li>URL - string of the URI</li>
     * <li>CONTENT - either config key or description provided to method {@link #create(String, String)}</li>
     * <li>BINARY_CONTENT - either config key or description provided to {@link #create(String, byte[])}</li>
     * <li>UNKNOWN - whatever description was provided to {@link #create(String, InputStream)}</li>
     * </ul>
     *
     * @return location of this resource (or other description of where it comes from)
     */
    String location();

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
