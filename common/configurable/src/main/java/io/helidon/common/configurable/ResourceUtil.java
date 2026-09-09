/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Utilities to move private static methods from interface,
 * as javadoc fails when using source 8.
 */
final class ResourceUtil {

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
        return new ResourceImpl(ResourceConfig.builder()
                                        .description(description)
                                        .buildPrototype(),
                                resourceSource,
                                inputStream);
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
        InputStream is = contextClassLoader().getResourceAsStream(resPath);
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

    /**
     * Create input stream for a URI using explicit connection and read timeouts.
     *
     * @param uri resource URI
     * @param timeout connection and read timeout
     * @return stream of that URI
     */
    static InputStream toIs(URI uri, Duration timeout) {
        Objects.requireNonNull(uri, "Resource URI must not be null");
        Objects.requireNonNull(timeout, "Resource URI timeout must not be null");
        try {
            return toIs(uri.toURL().openConnection(), timeout);
        } catch (IOException e) {
            throw new ResourceException("Failed to open stream to configured URI", e);
        }
    }

    /**
     * Create input stream for a URI through a proxy using explicit connection and read timeouts.
     *
     * @param uri resource URI
     * @param proxy HTTP proxy to access the URI
     * @param timeout connection and read timeout
     * @return stream of that URI
     */
    static InputStream toIs(URI uri, Proxy proxy, Duration timeout) {
        Objects.requireNonNull(uri, "Resource URI must not be null");
        Objects.requireNonNull(proxy, "Resource URI proxy must not be null");
        Objects.requireNonNull(timeout, "Resource URI timeout must not be null");
        try {
            return toIs(uri.toURL().openConnection(proxy), timeout);
        } catch (IOException e) {
            throw new ResourceException("Failed to open stream to configured URI", e);
        }
    }

    private static InputStream toIs(URLConnection connection, Duration timeout) throws IOException {
        int timeoutMillis = timeoutMillis(timeout);
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setUseCaches(false);
        try {
            return connection.getInputStream();
        } catch (IOException e) {
            cleanUpFailedHttpConnection(connection, e);
            throw e;
        }
    }

    private static void cleanUpFailedHttpConnection(URLConnection connection, IOException originalException) {
        if (!(connection instanceof HttpURLConnection httpConnection)) {
            return;
        }
        InputStream errorStream = httpConnection.getErrorStream();
        if (errorStream != null) {
            try {
                errorStream.close();
                return;
            } catch (IOException e) {
                originalException.addSuppressed(e);
            }
        }
        httpConnection.disconnect();
    }

    private static int timeoutMillis(Duration timeout) {
        long timeoutMillis;
        try {
            timeoutMillis = timeout.toMillis();
        } catch (ArithmeticException _) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, timeoutMillis));
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? ResourceUtil.class.getClassLoader() : classLoader;
    }
}
