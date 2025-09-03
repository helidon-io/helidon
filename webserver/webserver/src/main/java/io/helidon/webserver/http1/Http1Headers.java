/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import io.helidon.common.mapper.Mappers;
import io.helidon.http.DirectHandler;
import io.helidon.http.Http1HeadersParser;
import io.helidon.http.HttpPrologue;
import io.helidon.http.RequestException;
import io.helidon.http.WritableHeaders;
import io.helidon.service.registry.Services;
import io.helidon.webserver.http.DirectTransportRequest;

/**
 * HTTP/1 headers reader.
 */
public final class Http1Headers {
    private final Mappers mappers;
    private final DataReader reader;
    private final int maxHeadersSize;
    private final boolean validateHeaders;

    /**
     * Create a new instance.
     *
     * @param reader          data reader
     * @param maxHeadersSize  maximal header size
     * @param validateHeaders whether to validate headers
     * @deprecated use #create(io.helidon.common.mapper.Mappers, io.helidon.common.buffers.DataReader, int, boolean) instead,
     *      this constructor will be removed in a future release
     */
    @Deprecated(forRemoval = true)
    public Http1Headers(DataReader reader, int maxHeadersSize, boolean validateHeaders) {
        this(Services.get(Mappers.class), reader, maxHeadersSize, validateHeaders);
    }

    private Http1Headers(Mappers mappers, DataReader reader, int maxHeadersSize, boolean validateHeaders) {
        this.mappers = mappers;
        this.reader = reader;
        this.maxHeadersSize = maxHeadersSize;
        this.validateHeaders = validateHeaders;
    }

    /**
     * Create a new HTTP 1 header reader.
     *
     * @param mappers mappers to use when creating new headers
     * @param reader reader to obtain HTTP data
     * @param maxHeadersSize maximal size of all headers, in bytes
     * @param validateHeaders whether to validate headers
     * @return a new instance to read headers
     */
    public static Http1Headers create(Mappers mappers, DataReader reader, int maxHeadersSize, boolean validateHeaders) {
        return new Http1Headers(mappers, reader, maxHeadersSize, validateHeaders);
    }

    /**
     * Read headers.
     *
     * @param prologue parsed prologue of this request
     * @return writable headers parsed from the request data
     */
    public WritableHeaders<?> readHeaders(HttpPrologue prologue) {
        try {
            return Http1HeadersParser.readHeaders(mappers, reader, maxHeadersSize, validateHeaders);
        } catch (IllegalStateException | IllegalArgumentException | DataReader.IncorrectNewLineException e) {
            throw RequestException.builder()
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .request(DirectTransportRequest.create(prologue, WritableHeaders.create(mappers)))
                    .message(e.getMessage())
                    .cause(e)
                    .build();
        }
    }
}
