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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.helidon.common.configurable.LruCache;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;

final class Http2ConnectionCache {
    //todo Gracefully close connections in channel cache
    private static final Http2ConnectionCache SHARED = create();
    private final LruCache<ConnectionKey, Boolean> http2Supported = LruCache.<ConnectionKey, Boolean>builder()
            .capacity(1000)
            .build();
    private final Map<ConnectionKey, Http2ClientConnectionHandler> cache = new ConcurrentHashMap<>();

    static Http2ConnectionCache shared() {
        return SHARED;
    }

    static Http2ConnectionCache create() {
        return new Http2ConnectionCache();
    }

    boolean supports(ConnectionKey ck) {
        return http2Supported.get(ck).isPresent();
    }

    void remove(ConnectionKey connectionKey) {
        cache.remove(connectionKey);
        http2Supported.remove(connectionKey);
    }

    Http2ConnectionAttemptResult newStream(Http2ClientImpl http2Client,
                                           ConnectionKey connectionKey,
                                           Http2ClientRequestImpl request,
                                           ClientUri initialUri,
                                           Function<Http1ClientRequest, Http1ClientResponse> http1EntityHandler) {

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
}
