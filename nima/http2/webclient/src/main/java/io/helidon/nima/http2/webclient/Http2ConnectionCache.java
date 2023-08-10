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

package io.helidon.nima.http2.webclient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.helidon.common.configurable.LruCache;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.ConnectionKey;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;

final class Http2ConnectionCache {
    //todo Gracefully close connections in channel cache
    private static final LruCache<ConnectionKey, Boolean> HTTP2_SUPPORTED = LruCache.<ConnectionKey, Boolean>builder()
            .capacity(1000)
            .build();
    private final LruCache<ConnectionKey, Boolean> http2Supported = LruCache.<ConnectionKey, Boolean>builder()
            .capacity(1000)
            .build();
    private static final Map<ConnectionKey, Http2ClientConnectionHandler> GLOBAL_CACHE = new ConcurrentHashMap<>();
    private final Map<ConnectionKey, Http2ClientConnectionHandler> localCache = new ConcurrentHashMap<>();
    private final Http2ClientImpl http2Client;

    Http2ConnectionCache(Http2ClientImpl http2Client) {
        this.http2Client = http2Client;
    }

    boolean supports(ConnectionKey ck) {
        return selectH2SupportCache().get(ck).isPresent();
    }

    static void clear() {
        HTTP2_SUPPORTED.clear();
        GLOBAL_CACHE.forEach((c, c2) -> c2.close());
    }

    void remove(ConnectionKey connectionKey) {
        selectCache().remove(connectionKey);
        selectH2SupportCache().remove(connectionKey);
    }

    Http2ConnectionAttemptResult newStream(ConnectionKey connectionKey,
                                                  Http2ClientRequestImpl request,
                                                  ClientUri initialUri,
                                                  Function<Http1ClientRequest, Http1ClientResponse> http1EntityHandler) {

        // this statement locks all threads - must not do anything complicated (just create a new instance)
        Http2ConnectionAttemptResult result =
                selectCache().computeIfAbsent(connectionKey, Http2ClientConnectionHandler::new)
                // this statement may block a single connection key
                .newStream(http2Client,
                           request,
                           initialUri,
                           http1EntityHandler);
        if (result.result() == Http2ConnectionAttemptResult.Result.HTTP_2) {
            selectH2SupportCache().put(connectionKey, true);
        }
        return result;
    }

    private Map<ConnectionKey, Http2ClientConnectionHandler> selectCache(){
        return http2Client.clientConfig().shareConnectionCache() ? GLOBAL_CACHE : localCache;
    }

    private LruCache<ConnectionKey, Boolean> selectH2SupportCache(){
        return http2Client.clientConfig().shareConnectionCache() ? HTTP2_SUPPORTED : http2Supported;
    }
}
