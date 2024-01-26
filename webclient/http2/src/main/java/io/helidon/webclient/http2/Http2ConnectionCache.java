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

package io.helidon.webclient.http2;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.helidon.common.configurable.LruCache;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.spi.ClientConnectionCache;

public final class Http2ConnectionCache extends ClientConnectionCache {
    private static final Http2ConnectionCache SHARED = new Http2ConnectionCache(true);
    private final LruCache<ConnectionKey, Boolean> http2Supported = LruCache.<ConnectionKey, Boolean>builder()
            .capacity(1000)
            .build();
    private final Map<ConnectionKey, Http2ClientConnectionHandler> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private Http2ConnectionCache(boolean shared) {
        super(shared);
    }

    public static Http2ConnectionCache shared() {
        return SHARED;
    }

    public static Http2ConnectionCache create() {
        return new Http2ConnectionCache(false);
    }

    @Override
    public void closeResource() {
        if (!closed.getAndSet(true)) {
            List.copyOf(cache.keySet())
                    .forEach(this::closeAndRemove);
        }
    }

    boolean supports(ConnectionKey ck) {
        return http2Supported.get(ck).isPresent();
    }

    void remove(ConnectionKey connectionKey) {
        if (!closed.get()) {
            closeAndRemove(connectionKey);
        }
    }

    Http2ConnectionAttemptResult newStream(Http2ClientImpl http2Client,
                                           ConnectionKey connectionKey,
                                           Http2ClientRequestImpl request,
                                           ClientUri initialUri,
                                           Function<Http1ClientRequest, Http1ClientResponse> http1EntityHandler) {

        if (closed.get()) {
            throw new IllegalStateException("Connection cache is closed");
        }

        // this statement locks all threads - must not do anything complicated (just create a new instance)
        Http2ConnectionAttemptResult result =
                cache.computeIfAbsent(connectionKey, Http2ClientConnectionHandler::new)
                // this statement may block a single connection key
                .newStream(http2Client,
                           request,
                           initialUri,
                           http1EntityHandler);
        if (result.result() == Http2ConnectionAttemptResult.Result.HTTP_2) {
            http2Supported.put(connectionKey, true);
        }
        return result;
    }

    private void closeAndRemove(ConnectionKey connectionKey){
        Http2ClientConnectionHandler handler = cache.remove(connectionKey);
        if (handler != null) {
            handler.close();
        }
        http2Supported.remove(connectionKey);
    }
}
