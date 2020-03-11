/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReadableContent;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyReaderContext;

/**
 * Immutable implementation of the {@link WebClientResponse}.
 */
final class WebClientResponseImpl implements WebClientResponse {

    private static final Logger LOGGER = Logger.getLogger(NettyClientHandler.class.getName());

    private final WebClientResponseHeadersImpl headers;
    private final Flow.Publisher<DataChunk> publisher;
    private final Http.ResponseStatus status;
    private final Http.Version version;
    private final MediaSupport mediaSupport;
    private final Set<MessageBodyReader<?>> requestReaders;
    private final NettyClientHandler.ResponseCloser responseCloser;

    private WebClientResponseImpl(Builder builder) {
        headers = WebClientResponseHeadersImpl.create(builder.headers);
        publisher = builder.publisher;
        status = builder.status;
        version = builder.version;
        mediaSupport = builder.mediaSupport;
        requestReaders = builder.requestReaders;
        responseCloser = builder.responseCloser;
    }

    /**
     * Creates builder for {@link WebClientResponseImpl}.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Http.ResponseStatus status() {
        return status;
    }

    @Override
    public Http.Version version() {
        return version;
    }

    @Override
    public CompletionStage<Void> close() {
        CompletableFuture<Void> toReturn = new CompletableFuture<>();
        responseCloser.close().addListener(future -> {
            LOGGER.finest("Response has been closed.");
            toReturn.complete(null);
        });
        return toReturn;
    }

    @Override
    public MessageBodyReadableContent content() {
        Optional<MediaType> mediaType = headers.contentType();
        MessageBodyReaderContext readerContext = MessageBodyReaderContext.create(mediaSupport, null, headers, mediaType);
        requestReaders.forEach(readerContext::registerReader);
        return MessageBodyReadableContent.create(publisher, readerContext);
    }

    @Override
    public WebClientResponseHeaders headers() {
        return headers;
    }

    /**
     * Builder for {@link WebClientResponseImpl}.
     */
    static class Builder implements io.helidon.common.Builder<WebClientResponseImpl> {

        private final Map<String, List<String>> headers = new HashMap<>();

        private MediaSupport mediaSupport;
        private Flow.Publisher<DataChunk> publisher;
        private Http.ResponseStatus status = Http.Status.INTERNAL_SERVER_ERROR_500;
        private Http.Version version = Http.Version.V1_1;
        private Set<MessageBodyReader<?>> requestReaders;
        private NettyClientHandler.ResponseCloser responseCloser;

        @Override
        public WebClientResponseImpl build() {
            return new WebClientResponseImpl(this);
        }

        /**
         * Sets content publisher to the response.
         *
         * @param publisher content publisher
         * @return updated builder instance
         */
        Builder contentPublisher(Flow.Publisher<DataChunk> publisher) {
            this.publisher = publisher;
            return this;
        }

        /**
         * Registered request body readers.
         *
         * @param requestReaders registered request readers
         * @return updated builder instance
         */
        Builder requestBodyReaders(Set<MessageBodyReader<?>> requestReaders) {
            this.requestReaders = requestReaders;
            return this;
        }

        /**
         * Media support of the request.
         *
         * @param mediaSupport media support
         * @return updated builder instance
         */
        Builder mediaSupport(MediaSupport mediaSupport) {
            this.mediaSupport = mediaSupport;
            return this;
        }

        /**
         * Sets response status code.
         *
         * @param status response status code
         * @return updated builder instance
         */
        Builder status(Http.ResponseStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Http version of the response.
         *
         * @param version response http version
         * @return updated builder instance
         */
        Builder httpVersion(Http.Version version) {
            this.version = version;
            return this;
        }

        /**
         * Adds header to the response.
         *
         * @param name   header name
         * @param values header value
         * @return updated builder instance
         */
        Builder addHeader(String name, List<String> values) {
            this.headers.put(name, values);
            return this;
        }

        /**
         * Sets objects which helps to close response.
         *
         * @param responseCloser object closer object
         * @return updated builder instance
         */
        Builder responseCloser(NettyClientHandler.ResponseCloser responseCloser) {
            this.responseCloser = responseCloser;
            return this;
        }
    }
}
