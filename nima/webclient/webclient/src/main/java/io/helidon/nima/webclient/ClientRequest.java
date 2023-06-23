/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.function.Function;

import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.uri.UriEncoding;
import io.helidon.nima.common.tls.Tls;

/**
 * Request can be reused within a single thread, but it remembers all explicitly configured headers and URI.
 * Implementation is not expected to be thread safe!
 *
 * @param <B> type of the client request
 * @param <R> type of the client response
 */
public interface ClientRequest<B extends ClientRequest<B, R>, R extends ClientResponse> {
    /**
     * Configure URI.
     *
     * @param uri uri to resolve against base URI, or to use if absolute
     * @return updated request
     */
    default B uri(String uri) {
        return uri(URI.create(UriEncoding.encodeUri(uri)));
    }

    /**
     * Configure path to call.
     *
     * @param uri path
     * @return updated request
     */
    default B path(String uri) {
        return uri(URI.create(UriEncoding.encodeUri(uri)));
    }

    /**
     * TLS configuration for this specific request.
     *
     * @param tls tls configuration
     * @return updated request
     */
    B tls(Tls tls);

    /**
     * Configure URI.
     *
     * @param uri uri to resolve against base URI, or to use if absolute
     * @return updated request
     */
    B uri(URI uri);

    /**
     * Set an HTTP header.
     *
     * @param header header to set
     * @return updated request
     */
    B header(Http.HeaderValue header);

    /**
     * Set an HTTP header.
     *
     * @param name  header name
     * @param values header values
     * @return updated request
     */
    default B header(Http.HeaderName name, String... values) {
        return header(Http.Header.create(name, true, false, values));
    }

    /**
     * Set an HTTP header with multiple values.
     *
     * @param name   header name
     * @param values header values
     * @return updated request
     */
    default B header(Http.HeaderName name, List<String> values) {
        return header(Http.Header.create(name, values));
    }

    /**
     * Update headers.
     *
     * @param headersConsumer consumer of client request headers
     * @return updated request
     */
    B headers(Function<ClientRequestHeaders, WritableHeaders<?>> headersConsumer);

    /**
     * Accepted media types. Supports quality factor and wildcards.
     *
     * @param accepted media types to accept
     * @return updated request
     */
    default B accept(HttpMediaType... accepted) {
        return headers(it -> {
            it.accept(accepted);
            return it;
        });
    }

    /**
     * Accepted media types. Supports quality factor and wildcards.
     *
     * @param acceptedTypes media types to accept
     * @return updated request
     */
    default B accept(MediaType... acceptedTypes) {
        return headers(it -> {
            it.accept(acceptedTypes);
            return it;
        });
    }

    /**
     * Sets the content type of the request.
     *
     * @param contentType content type of the request.
     * @return updated request
     */
    default B contentType(HttpMediaType contentType) {
        return headers(it -> {
            it.contentType(contentType);
            return it;
        });
    }

    /**
     * Sets the content type of the request.
     *
     * @param contentType content type of the request.
     * @return updated request
     */
    default B contentType(MediaType contentType) {
        return headers(it -> {
            it.contentType(contentType);
            return it;
        });
    }

    /**
     * Replace a placeholder in URI with an actual value.
     *
     * @param name  name of parameter
     * @param value value of parameter
     * @return updated request
     */
    B pathParam(String name, String value);

    /**
     * Add query parameter.
     *
     * @param name   name of parameter
     * @param values value(s) of parameter
     * @return updated request
     */
    B queryParam(String name, String... values);

    /**
     * Set fragment of the URI.
     * @param fragment fragment
     * @return updated request
     */
    B fragment(String fragment);

    /**
     * Whether to follow redirects.
     *
     * @param followRedirects follow redirects
     * @return updated request
     */
    B followRedirects(boolean followRedirects);

    /**
     * Max number of the followed redirects.
     *
     * @param maxRedirects max followed redirects
     * @return updated request
     */
    B maxRedirects(int maxRedirects);

    /**
     * Request without an entity.
     *
     * @return response
     */
    R request();

    /**
     * Request without sending an entity, asking for entity only.
     *
     * @param type type of entity
     * @param <T>  type of entity
     * @return correctly typed entity
     * @see #request()
     */
    default <T> T request(Class<T> type) {
        return request().as(type);
    }

    /**
     * Submit an entity.
     *
     * @param entity request entity
     * @return response
     */
    R submit(Object entity);

    /**
     * Handle output stream and submit the request.
     *
     * @param outputStreamConsumer output stream to write request entity
     * @return response
     */
    R outputStream(OutputStreamHandler outputStreamConsumer);

    /**
     * Resolved URI that will be used to invoke this request.
     *
     * @return URI to invoke
     */
    URI resolvedUri();

    /**
     * This method is for explicit connection use by this request.
     *
     * @param connection connection to use for this request
     * @return updated client request
     */
    B connection(ClientConnection connection);

    /**
     * Disable uri encoding.
     *
     * @return updated client request
     */
    B skipUriEncoding();

    /**
     * Add a property to be used by this request.
     *
     * @param propertyName  property name
     * @param propertyValue property value
     * @return updated builder instance
     */
    B property(String propertyName, String propertyValue);

    /**
     * Handle output stream.
     */
    interface OutputStreamHandler {
        /**
         * Handle the output stream.
         *
         * @param out output stream to write data to
         * @throws java.io.IOException in case the write fails
         */
        void handle(OutputStream out) throws IOException;
    }
}
