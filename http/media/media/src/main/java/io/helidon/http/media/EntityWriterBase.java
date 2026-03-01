/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.media;

import java.nio.charset.Charset;
import java.util.Optional;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

/**
 * Base class for entity writers that care about charsets ands content types.
 *
 * @param <T> type of the supported object
 */
public abstract class EntityWriterBase<T> extends EntityIoBase implements EntityWriter<T> {
    private final MediaSupportConfig config;
    private final Optional<Charset> defaultCharset;
    private final Header contentTypeHeader;

    /**
     * Provide the media support config with default content type, and supported accepted media types.
     *
     * @param config Media Support configuration
     */
    protected EntityWriterBase(MediaSupportConfig config) {
        this.config = config;
        this.defaultCharset = config.contentType()
                .charset()
                .map(EntityIoBase::charset);
        this.contentTypeHeader = HeaderValues.create(HeaderNames.CONTENT_TYPE,
                                                     config.contentType().text());
    }

    /**
     * Configure server response content type and return charset to use.
     * <p>
     * Server response content type is handled as follows:
     * <ul>
     *     <li>If a content type is explicitly set, use it, and return its charset if configured</li>
     *     <li>Check if there is an {@code Accept-Charset} header specified, if it is, set the
     *     default header with this charset, and return it</li>
     *     <li>If the default content type contains charset, set it, and return its charset</li>
     *     <li>Set the default content type and return an empty optional</li>
     * </ul>
     *
     * @param serverRequestHeaders  server request headers (to get Accept-Charset header)
     * @param serverResponseHeaders server response headers (to configure content type)
     * @return charset to use to write the response, if it could be guessed
     */
    protected Optional<Charset> serverResponseContentTypeAndCharset(Headers serverRequestHeaders,
                                                                    WritableHeaders<?> serverResponseHeaders) {
        var existingContentType = serverResponseHeaders.contentType();
        if (existingContentType.isPresent()) {
            // the user did not configure charset, use whatever is default for the support
            return existingContentType.get()
                    .charset()
                    .map(EntityIoBase::charset);
        }

        var acceptCharset = serverRequestHeaders.find(HeaderNames.ACCEPT_CHARSET)
                .map(Header::getString);

        if (acceptCharset.isPresent()) {
            var responseContentType = config.contentType()
                    .withCharset(acceptCharset.get());
            serverResponseHeaders.contentType(responseContentType);
            return acceptCharset.map(EntityIoBase::charset);
        }

        // now we always use the default content type as is
        serverResponseHeaders.set(contentTypeHeader);
        return this.defaultCharset;
    }

    /**
     * Configure client request content type and return the charset to use.
     *
     * @param clientRequestHeaders client request header (to get or configure content type)
     * @return charset to use to write the request, if configured
     */
    protected Optional<Charset> clientRequestContentTypeAndCharset(WritableHeaders<?> clientRequestHeaders) {
        var existingContentType = clientRequestHeaders.contentType();

        if (existingContentType.isPresent()) {
            // the user did not configure charset, use whatever is default for the support
            return existingContentType.get()
                    .charset()
                    .map(EntityIoBase::charset);
        }

        // now we always use the default content type as is
        clientRequestHeaders.set(contentTypeHeader);
        return this.defaultCharset;
    }
}
