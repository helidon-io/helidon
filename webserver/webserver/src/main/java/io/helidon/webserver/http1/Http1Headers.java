/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.http1;

import io.helidon.common.buffers.DataReader;
import io.helidon.http.DirectHandler;
import io.helidon.http.Http1HeadersParser;
import io.helidon.http.HttpPrologue;
import io.helidon.http.RequestException;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.http.DirectTransportRequest;

/**
 * HTTP/1 headers reader.
 */
public final class Http1Headers {

    private final DataReader reader;
    private final int maxHeadersSize;
    private final boolean validateHeaders;

    /**
     * Create a new instance.
     *
     * @param reader          data reader
     * @param maxHeadersSize  maximal header size
     * @param validateHeaders whether to validate headers
     */
    public Http1Headers(DataReader reader, int maxHeadersSize, boolean validateHeaders) {
        this.reader = reader;
        this.maxHeadersSize = maxHeadersSize;
        this.validateHeaders = validateHeaders;
    }

    /**
     * Read headers.
     *
     * @param prologue parsed prologue of this request
     * @return writable headers parsed from the request data
     */
    public WritableHeaders<?> readHeaders(HttpPrologue prologue) {
        try {
            return Http1HeadersParser.readHeaders(reader, maxHeadersSize, validateHeaders);
        } catch (IllegalStateException | IllegalArgumentException | DataReader.IncorrectNewLineException e) {
            throw RequestException.builder()
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .request(DirectTransportRequest.create(prologue, WritableHeaders.create()))
                    .message(e.getMessage())
                    .cause(e)
                    .build();
        }
    }
}
