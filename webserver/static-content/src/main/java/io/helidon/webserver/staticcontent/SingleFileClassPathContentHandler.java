/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.http.Method;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class SingleFileClassPathContentHandler extends ClassPathContentHandler {
    private static final System.Logger LOGGER = System.getLogger(SingleFileClassPathContentHandler.class.getName());
    private final AtomicBoolean populatedInMemoryCache = new AtomicBoolean();
    private final boolean cacheInMemory;
    private final String location;
    private final ClassLoader classLoader;

    SingleFileClassPathContentHandler(ClasspathHandlerConfig config) {
        super(config);

        this.cacheInMemory = config.cachedFiles().contains(".") || config.cachedFiles().contains("/");
        this.location = cleanRoot(config.location());
        this.classLoader = config.classLoader()
                .or(() -> Optional.ofNullable(Thread.currentThread().getContextClassLoader()))
                .orElseGet(SingleFileClassPathContentHandler.class::getClassLoader);
    }

    @Override
    public void beforeStart() {
        try {
            // directly cache in memory
            URL resourceUrl = classLoader.getResource(location);
            if (resourceUrl == null) {
                throw new IllegalArgumentException("Resource " + location + " cannot be added to in memory cache, as it does "
                                                           + "not exist on classpath for single file classpath static content "
                                                           + "handler.");
            }

            if (cacheInMemory && populatedInMemoryCache.compareAndSet(false, true)) {
                addToInMemoryCache(location, resourceUrl);
            } else {
                // cache a handler that loads it from file system
                var handler = cachedHandler(location, resourceUrl);
                if (handler.isEmpty()) {
                    throw new IllegalArgumentException("Resource " + location + " cannot be added to in memory cache, as it does "
                                                               + "not exist on classpath for single file classpath static "
                                                               + "content handler.");
                }
                cacheHandler(location, handler.get());
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "Failed to add classpath resource to in-memory cache, location: " + location,
                       e);
        }
        super.beforeStart();
    }

    @Override
    void releaseCache() {
        populatedInMemoryCache.set(false);
    }

    @Override
    boolean doHandle(Method method, String requestedPath, ServerRequest request, ServerResponse response, boolean mapped)
            throws IOException {

        var handler = cacheHandler(location)
                .orElseThrow(() -> new IllegalStateException("Handler must be cached during startup " + location));

        return handler.handle(handlerCache(), method, request, response, requestedPath);
    }
}
