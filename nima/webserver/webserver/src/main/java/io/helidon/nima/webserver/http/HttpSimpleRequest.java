/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http;

import io.helidon.common.http.DirectHandler;
import io.helidon.common.http.Headers;
import io.helidon.common.http.HeadersServerRequest;
import io.helidon.common.http.HeadersWritable;
import io.helidon.common.http.HttpPrologue;

/**
 * Simple request to use with {@link HttpException}.
 */
public class HttpSimpleRequest implements DirectHandler.TransportRequest {
    private final String version;
    private final String method;
    private final String path;
    private final HeadersServerRequest headers;

    private HttpSimpleRequest(String version,
                              String method,
                              String path,
                              HeadersServerRequest headers) {
        this.version = version;
        this.method = method;
        this.path = path;
        this.headers = headers;
    }

    /**
     * Create a new request from as much known information as possible.
     *
     * @param protocolAndVersion protocol with version
     * @param method             method
     * @param path               path
     * @return a new simple request
     */
    public static DirectHandler.TransportRequest create(String protocolAndVersion,
                                                        String method,
                                                        String path) {
        return new HttpSimpleRequest(protocolAndVersion,
                                     method,
                                     path,
                                     HeadersServerRequest.create(HeadersWritable.create()));
    }

    /**
     * Configure a simple request from known prologue and headers.
     *
     * @param prologue parsed prologue
     * @param headers  parsed headers
     * @return a new simple request
     */
    public static DirectHandler.TransportRequest create(HttpPrologue prologue, Headers headers) {
        return new HttpSimpleRequest(prologue.protocol() + "/" + prologue.protocolVersion(),
                                     prologue.method().text(),
                                     prologue.uriPath().rawPathNoParams(),
                                     HeadersServerRequest.create(headers));
    }

    @Override
    public String protocolVersion() {
        return version;
    }

    @Override
    public String method() {
        return method;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public HeadersServerRequest headers() {
        return headers;
    }
}
