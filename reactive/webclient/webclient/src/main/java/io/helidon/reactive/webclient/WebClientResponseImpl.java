/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.reactive.webclient;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.HeadersWritable;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.reactive.Single;
import io.helidon.reactive.media.common.MessageBodyReadableContent;
import io.helidon.reactive.media.common.MessageBodyReaderContext;

/**
 * Immutable implementation of the {@link WebClientResponse}.
 */
final class WebClientResponseImpl implements WebClientResponse {

    private final WebClientResponseHeadersImpl headers;
    private final Flow.Publisher<DataChunk> publisher;
    private final Http.Status status;
    private final Http.Version version;
    private final MessageBodyReaderContext readerContext;
    private final NettyClientHandler.ResponseCloser responseCloser;
    private final URI lastEndpointUri;

    private WebClientResponseImpl(Builder builder) {
        headers = WebClientResponseHeadersImpl.create(builder.headers);
        publisher = builder.publisher;
        status = builder.status;
        version = builder.version;
        readerContext = builder.readerContext;
        responseCloser = builder.responseCloser;
        lastEndpointUri = builder.lastEndpointUri;
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
    public Http.Status status() {
        return status;
    }

    @Override
    public Http.Version version() {
        return version;
    }

    @Override
    public URI lastEndpointURI() {
        return lastEndpointUri;
    }

    @Override
    public Single<Void> close() {
        return responseCloser.close();
    }

    @Override
    public MessageBodyReadableContent content() {
        Optional<HttpMediaType> mediaType = headers.contentType();
        MessageBodyReaderContext readerContext = MessageBodyReaderContext.create(this.readerContext, null, headers, mediaType);
        return MessageBodyReadableContent.create(publisher, readerContext);
    }

    @Override
    public WebClientResponseHeaders headers() {
        return headers;
    }

    /**
     * Builder for {@link WebClientResponseImpl}.
     */
    static class Builder implements io.helidon.common.Builder<Builder, WebClientResponseImpl> {

        private final HeadersWritable<?> headers = HeadersWritable.create();

        private Flow.Publisher<DataChunk> publisher;
        private Http.Status status = Http.Status.INTERNAL_SERVER_ERROR_500;
        private Http.Version version = Http.Version.V1_1;
        private NettyClientHandler.ResponseCloser responseCloser;
        private MessageBodyReaderContext readerContext;
        private URI lastEndpointUri;

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
         * Reader context of the request.
         *
         * @param readerContext message body reader
         * @return updated builder instance
         */
        Builder readerContext(MessageBodyReaderContext readerContext) {
            this.readerContext = readerContext;
            return this;
        }

        /**
         * Sets response status code.
         *
         * @param status response status code
         * @return updated builder instance
         */
        Builder status(Http.Status status) {
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
            this.headers.set(Http.HeaderValue.create(Http.Header.create(name), values));
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

        /**
         * Set last endpoint uri.
         *
         * @param lastEndpointUri endpoint uri
         * @return updated builder instance
         */
        Builder lastEndpointURI(URI lastEndpointUri) {
            this.lastEndpointUri = lastEndpointUri;
            return this;
        }
    }
}
