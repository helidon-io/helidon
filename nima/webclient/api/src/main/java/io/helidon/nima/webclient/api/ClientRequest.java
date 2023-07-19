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

package io.helidon.nima.webclient.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpException;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.uri.UriEncoding;
import io.helidon.common.uri.UriFragment;
import io.helidon.nima.common.tls.Tls;

/**
 * Request can be reused within a single thread, but it remembers all explicitly configured headers and URI.
 * Implementation is not expected to be thread safe!
 */
public interface ClientRequest<T extends ClientRequest<T>> {
    /**
     * Configure URI.
     *
     * @param uri uri to resolve against base URI, or to use if absolute
     * @return updated request
     */
    default T uri(String uri) {
        return uri(URI.create(UriEncoding.encodeUri(uri)));
    }

    /**
     * Configure path to call.
     *
     * @param uri path
     * @return updated request
     */
    default T path(String uri) {
        return uri(URI.create(UriEncoding.encodeUri(uri)));
    }

    /**
     * TLS configuration for this specific request.
     *
     * @param tls tls configuration
     * @return updated request
     */
    T tls(Tls tls);

    /**
     * Proxy configuration for this specific request.
     *
     * @param proxy proxy configuration
     * @return updated request
     */
    T proxy(Proxy proxy);

    /**
     * Configure URI.
     *
     * @param uri uri to resolve against base URI, or to use if absolute
     * @return updated request
     */
    T uri(URI uri);

    /**
     * Set an HTTP header.
     *
     * @param header header to set
     * @return updated request
     */
    T header(Http.HeaderValue header);

    /**
     * Set an HTTP header.
     *
     * @param name   header name
     * @param values header values
     * @return updated request
     */
    default T header(Http.HeaderName name, String... values) {
        return header(Http.Header.create(name, true, false, values));
    }

    /**
     * Set an HTTP header with multiple values.
     *
     * @param name   header name
     * @param values header values
     * @return updated request
     */
    default T header(Http.HeaderName name, List<String> values) {
        return header(Http.Header.create(name, values));
    }

    /**
     * Configure headers. Copy all headers from supplied {@link Headers} instance.
     *
     * @param headers to copy
     * @return updated request
     */
    T headers(Headers headers);

    /**
     * Update headers.
     *
     * @param headersConsumer consumer of client request headers
     * @return updated request
     */
    T headers(Consumer<ClientRequestHeaders> headersConsumer);

    /**
     * Accepted media types. Supports quality factor and wildcards.
     *
     * @param accepted media types to accept
     * @return updated request
     */
    default T accept(HttpMediaType... accepted) {
        return headers(it -> it.accept(accepted));
    }

    /**
     * Accepted media types. Supports quality factor and wildcards.
     *
     * @param acceptedTypes media types to accept
     * @return updated request
     */
    default T accept(MediaType... acceptedTypes) {
        return headers(it -> it.accept(acceptedTypes));
    }

    /**
     * Sets the content type of the request.
     *
     * @param contentType content type of the request.
     * @return updated request
     */
    default T contentType(MediaType contentType) {
        return headers(it -> it.contentType(contentType));
    }

    /**
     * Replace a placeholder in URI with an actual value.
     *
     * @param name  name of parameter
     * @param value value of parameter
     * @return updated request
     */
    T pathParam(String name, String value);

    /**
     * Add query parameter.
     *
     * @param name   name of parameter
     * @param values value(s) of parameter
     * @return updated request
     */
    T queryParam(String name, String... values);

    /**
     * Set fragment of the URI.
     *
     * @param fragment fragment
     * @return updated request
     */
    default T fragment(String fragment) {
        return fragment(UriFragment.create(fragment));
    }

    /**
     * Set fragment of the URI.
     *
     * @param fragment fragment
     * @return updated request
     */
    T fragment(UriFragment fragment);

    /**
     * Whether to follow redirects.
     *
     * @param followRedirects follow redirects
     * @return updated request
     */
    T followRedirects(boolean followRedirects);

    /**
     * Max number of the followed redirects.
     *
     * @param maxRedirects max followed redirects
     * @return updated request
     */
    T maxRedirects(int maxRedirects);

    /**
     * Request without an entity.
     *
     * @return response
     */
    default HttpClientResponse request() {
        return submit(BufferData.EMPTY_BYTES);
    }

    /**
     * Get a (mutable) instance of outgoing headers.
     *
     * @return client request headers
     */
    ClientRequestHeaders headers();

    /**
     * Request without sending an entity.
     *
     * @param type type of entity
     * @param <E>  type of entity
     * @return correctly typed response
     * @see #request()
     */
    default <E> ClientResponseTyped<E> request(Class<E> type) {
        HttpClientResponse response = request();
        return new ClientResponseTypedImpl<E>(response, response.as(type));
    }

    /**
     * Request entity without sending a request entity, asking for entity only.
     * This method will fail if the status is not in successful family.
     *
     * @param type type of requested entity
     * @return correctly typed entity
     * @throws io.helidon.common.http.HttpException in case the response status is not success
     */
    default <E> E requestEntity(Class<E> type) throws HttpException {
        ClientResponseTyped<E> typedResponse = request(type);
        if (typedResponse.status().family() == Http.Status.Family.SUCCESSFUL) {
            return typedResponse.entity();
        }
        if (typedResponse.status() == Http.Status.BAD_REQUEST_400) {
            throw new IllegalArgumentException("Failed to read entity, received bad request");
        }
        throw new IllegalStateException(typedResponse.status() + ": Failed to read entity, as response status is not success");
    }

    /**
     * Submit an entity.
     *
     * @param entity request entity
     * @return response
     */
    HttpClientResponse submit(Object entity);

    /**
     * Submit an entity and request a specific type.
     *
     * @param entity        request entity
     * @param requestedType type of response entity
     * @param <T>           type of response entity
     * @return correctly typed response
     */
    default <T> ClientResponseTyped<T> submit(Object entity, Class<T> requestedType) {
        HttpClientResponse response = submit(entity);
        return new ClientResponseTypedImpl<>(response, response.as(requestedType));
    }

    /**
     * Handle output stream and submit the request.
     *
     * @param outputStreamConsumer output stream to write request entity
     * @return response
     */
    HttpClientResponse outputStream(OutputStreamHandler outputStreamConsumer);

    /**
     * Handle output stream and request a specific type.
     *
     * @param outputStreamConsumer output stream consumer to write request entity
     * @param requestedType        type of response entity
     * @param <T>                  type of response entity
     * @return correctly typed response
     */
    default <T> ClientResponseTyped<T> outputStream(OutputStreamHandler outputStreamConsumer, Class<T> requestedType) {
        HttpClientResponse response = outputStream(outputStreamConsumer);
        return new ClientResponseTypedImpl<>(response, response.as(requestedType));
    }

    /**
     * Resolved URI that will be used to invoke this request.
     *
     * @return URI to invoke
     */
    ClientUri resolvedUri();

    /**
     * This method is for explicit connection use by this request.
     *
     * @param connection connection to use for this request
     * @return updated client request
     */
    T connection(ClientConnection connection);

    /**
     * Disable uri encoding.
     *
     * @return updated client request
     */
    default T skipUriEncoding() {
        return skipUriEncoding(true);
    }

    /**
     * Disable uri encoding.
     *
     * @param skip set to {@code true} to disable URI encoding ({@code false} by default)
     * @return updated client request
     */
    T skipUriEncoding(boolean skip);

    /**
     * Add a property to be used by this request.
     *
     * @param propertyName  property name
     * @param propertyValue property value
     * @return updated builder instance
     */
    T property(String propertyName, String propertyValue);

    /**
     * Whether to use keep alive with this request.
     *
     * @param keepAlive use keep alive
     * @return updated client request
     */
    T keepAlive(boolean keepAlive);

    /**
     * Read timeout for this request.
     *
     * @param readTimeout response read timeout
     * @return updated client request
     */
    T readTimeout(Duration readTimeout);

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
