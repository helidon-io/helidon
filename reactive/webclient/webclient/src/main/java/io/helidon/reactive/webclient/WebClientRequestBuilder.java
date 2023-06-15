/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.context.Context;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.reactive.media.common.MessageBodyReaderContext;
import io.helidon.reactive.media.common.MessageBodyWriterContext;

/**
 * Fluent API builder that is used by {@link WebClient} to create an outgoing request.
 */
public interface WebClientRequestBuilder {

    /**
     * String representation of request uri.
     *
     * Replaces baseUri defined in client builder.
     *
     * @param uri request uri
     * @return updated builder instance
     */
    WebClientRequestBuilder uri(String uri);

    /**
     * Request {@link URL}.
     *
     * Replaces baseUri defined in client builder.
     *
     * @param url request url
     * @return updated builder instance
     */
    WebClientRequestBuilder uri(URL url);

    /**
     * Request {@link URI}.
     *
     * Replaces baseUri defined in client builder.
     *
     * @param uri request uri
     * @return updated builder instance
     */
    WebClientRequestBuilder uri(URI uri);

    /**
     * Disables final uri encoding.
     *
     * This setting skips all parts of {@link URI} from encoding.
     *
     * @return updated builder instance
     */
    WebClientRequestBuilder skipUriEncoding();

    /**
     * Sets if redirects should be followed at this request or not.
     *
     * @param followRedirects follow redirects
     * @return updated builder instance
     */
    WebClientRequestBuilder followRedirects(boolean followRedirects);

    /**
     * Add a property to be used by a {@link io.helidon.reactive.webclient.spi.WebClientService}.
     *
     * @param propertyName  property name
     * @param propertyValue property value
     * @return updated builder instance
     */
    WebClientRequestBuilder property(String propertyName, String propertyValue);

    /**
     * Explicitly configure a context to use.
     * This method is not needed when running within a scope of a Helidon server, such as
     * Web Server, gRPC Server, MicroProfile Server, or when processing a Helidon message consumer.
     *
     * @param context context to be used by the outbound request, to look for security context, parent tracing span and similar
     * @return updated builder instance
     */
    WebClientRequestBuilder context(Context context);

    /**
     * Get a (mutable) instance of outgoing headers.
     *
     * @return client request headers
     */
    WebClientRequestHeaders headers();

    /**
     * Add a query parameter.
     *
     * Appends these query parameters to the query parameters defined in the request uri.
     *
     * @param name   query name
     * @param values query value
     * @return updated builder instance
     */
    WebClientRequestBuilder queryParam(String name, String... values);

    /**
     * Override client proxy configuration.
     *
     * @param proxy request proxy
     * @return updated builder instance
     */
    WebClientRequestBuilder proxy(Proxy proxy);

    /**
     * Configure headers. Copy all headers from supplied {@link io.helidon.common.http.Headers} instance.
     *
     * @param headers to copy
     * @return updated builder instance
     */
    WebClientRequestBuilder headers(Headers headers);

    /**
     * Function from parameter is executed on top of stored headers.
     *
     * This approach is here to allow as to add any header via specialized method while using builder pattern.
     *
     * @param headers function which adds headers
     * @return updated builder instance
     */
    WebClientRequestBuilder headers(Function<WebClientRequestHeaders, Headers> headers);

    /**
     * Adds header values for a specified name.
     *
     * @param name   header name
     * @param values header values
     * @return this instance of {@link WebClientRequestBuilder}
     * @throws NullPointerException if the specified name is null.
     * @see #headers()
     * @see Http.Header header names constants
     */
    default WebClientRequestBuilder addHeader(String name, String... values) {
        headers().add(name, values);
        return this;
    }

    /**
     * Adds header values for a specified name.
     *
     * @param name   header name
     * @param values header values
     * @return this instance of {@link WebClientRequestBuilder}
     * @throws NullPointerException if the specified name is null.
     * @see #headers()
     * @see Http.Header header names constants
     */
    default WebClientRequestBuilder addHeader(String name, List<String> values) {
        addHeader(Http.Header.create(name), values);
        return this;
    }

    /**
     * Adds header value.
     *
     * @param value header value
     * @return updated builder
     */
    default WebClientRequestBuilder addHeader(Http.HeaderValue value) {
        headers().add(value);
        return this;
    }

    /**
     * Adds header values for a specified name.
     *
     * @param name header name
     * @param values  value(s)
     * @return updated builder
     */
    default WebClientRequestBuilder addHeader(Http.HeaderName name, String... values) {
        headers().add(Http.Header.create(name, values));
        return this;
    }

    /**
     * Adds header values for a specified name.
     *
     * @param name header name
     * @param values  value(s)
     * @return updated builder
     */
    default WebClientRequestBuilder addHeader(Http.HeaderName name, List<String> values) {
        headers().add(Http.Header.create(name, values));
        return this;
    }

    /**
     * Copies all of the mappings from the specified {@code parameters} to this response headers instance.
     *
     * @param parameters to copy.
     * @return this instance of {@link WebClientRequestBuilder}
     * @throws NullPointerException if the specified {@code parameters} are null.
     * @see #headers()
     */
    default WebClientRequestBuilder addHeaders(Headers parameters){
        headers().addAll(parameters);
        return this;
    }

    /**
     * Configure query parameters.
     *
     * Appends these query parameters to the query parameters defined in the request uri.
     *
     * Copy all query parameters from supplied {@link io.helidon.common.uri.UriQuery} instance.
     *
     * @param queryParams to copy
     * @return updated builder instance
     */
    WebClientRequestBuilder queryParams(UriQuery queryParams);

    /**
     * Returns reader context of the request builder.
     *
     * @return request reader context
     */
    MessageBodyReaderContext readerContext();

    /**
     * Returns writer context of the request builder.
     *
     * @return request writer context
     */
    MessageBodyWriterContext writerContext();

    /**
     * Sets http version.
     *
     * @param httpVersion http version
     * @return updated builder instance
     */
    WebClientRequestBuilder httpVersion(Http.Version httpVersion);

    /**
     * Sets new connection timeout for this request.
     *
     * @param amount amount of time
     * @param unit   time unit
     * @return updated builder instance
     * @deprecated use {@link #connectTimeout(Duration)} instead
     */
    @Deprecated(since = "4.0.0")
    WebClientRequestBuilder connectTimeout(long amount, TimeUnit unit);

    /**
     * Sets new connection timeout for this request.
     *
     * @param connectionTimeout amount of time
     * @return updated builder instance
     */
    WebClientRequestBuilder connectTimeout(Duration connectionTimeout);

    /**
     * Sets new read timeout for this request.
     *
     * @param amount amount of time
     * @param unit   time unit
     * @return updated builder instance
     * @deprecated use {@link #readTimeout(Duration)} instead
     */
    @Deprecated(since = "4.0.0")
    WebClientRequestBuilder readTimeout(long amount, TimeUnit unit);

    /**
     * Sets new read timeout for this request.
     *
     * @param readTimeout amount of time
     * @return updated builder instance
     */
    WebClientRequestBuilder readTimeout(Duration readTimeout);

    /**
     * Fragment of the request.
     *
     * Replaces fragment defined in the request uri.
     *
     * @param fragment request fragment
     * @return updated builder instance
     */
    WebClientRequestBuilder fragment(String fragment);

    /**
     * Path of the request.
     *
     * Appends this path to the path defined in the request uri.
     *
     * @param path path
     * @return updated builder instance
     */
    WebClientRequestBuilder path(UriPath path);

    /**
     * Path of the request.
     *
     * Appends this path to the path defined in the request uri.
     *
     * @param path path
     * @return updated builder instance
     */
    WebClientRequestBuilder path(String path);

    /**
     * Content type of the request.
     *
     * @param contentType content type
     * @return updated builder instance
     */
    WebClientRequestBuilder contentType(HttpMediaType contentType);

    /**
     * Content type of the request.
     *
     * @param contentType content type
     * @return updated builder instance
     */
    default WebClientRequestBuilder contentType(MediaType contentType) {
        return contentType(HttpMediaType.create(contentType));
    }

    /**
     * Media types which are accepted in the response, support for quality factor and additional parameters.
     *
     * @param mediaTypes media types
     * @return updated builder instance
     */
    WebClientRequestBuilder accept(HttpMediaType... mediaTypes);

    /**
     * Media types which are accepted in the response.
     *
     * @param mediaTypes media types
     * @return updated builder instance
     */
    default WebClientRequestBuilder accept(MediaType... mediaTypes) {
        HttpMediaType[] httpMediaTypes = new HttpMediaType[mediaTypes.length];
        for (int i = 0; i < httpMediaTypes.length; i++) {
            httpMediaTypes[i] = HttpMediaType.create(mediaTypes[i]);

        }
        accept(httpMediaTypes);
        return this;
    }

    /**
     * Whether connection should be kept alive after request.
     *
     * @param keepAlive keep alive
     * @return updated builder instance
     */
    WebClientRequestBuilder keepAlive(boolean keepAlive);

    /**
     * Set new request id. This id is used in logging messages.
     *
     * @param requestId new request id
     * @return updated builder instance
     */
    WebClientRequestBuilder requestId(long requestId);

    /**
     * Whether chunked {@link Http.Header#TRANSFER_ENCODING} should be added to the headers if the entity is chunked.
     *
     * @param allowChunkedEncoding allow chunked encoding to be added
     * @return updated builder instance
     */
    WebClientRequestBuilder allowChunkedEncoding(boolean allowChunkedEncoding);

    /**
     * Performs prepared request and transforms response to requested type.
     *
     * When transformation is done the returned {@link CompletionStage} is notified.
     *
     * @param responseType requested response type
     * @param <T>          response type
     * @return request completion stage
     */
    <T> Single<T> request(Class<T> responseType);

    /**
     * Performs prepared request and transforms response to requested type.
     *
     * When transformation is done the returned {@link CompletionStage} is notified.
     *
     * @param responseType requested response type
     * @param <T>          response type
     * @return request completion stage
     */
    <T> Single<T> request(GenericType<T> responseType);

    /**
     * Performs prepared request without expecting to receive any specific type.
     *
     * Response is not converted and returned {@link CompletionStage} is notified.
     *
     * @return request completion stage
     */
    Single<WebClientResponse> request();

    /**
     * Performs prepared request.
     *
     * When response is received, it is not converted to any other specific type and returned {@link CompletionStage}
     * is notified.
     *
     * @return request completion stage
     */
    Single<WebClientResponse> submit();

    /**
     * Performs prepared request and submitting request entity using {@link Flow.Publisher}.
     *
     * When response is received, it is converted to response type and returned {@link CompletionStage}
     * is notified.
     *
     * @param requestEntity request entity
     * @param responseType  requested response type
     * @param <T>           response type
     * @return request completion stage
     */
    <T> Single<T> submit(Flow.Publisher<DataChunk> requestEntity, Class<T> responseType);

    /**
     * Performs prepared request and submitting request entity.
     *
     * When response is received, it is converted to response type and returned {@link CompletionStage}
     * is notified.
     *
     * @param requestEntity request entity
     * @param responseType  requested response type
     * @param <T>           response type
     * @return request completion stage
     */
    <T> Single<T> submit(Object requestEntity, Class<T> responseType);

    /**
     * Performs prepared request and submitting request entity using {@link Flow.Publisher}.
     *
     * When response is received, it is not converted to any other specific type and returned {@link CompletionStage}
     * is notified.
     *
     * @param requestEntity request entity
     * @return request completion stage
     */
    Single<WebClientResponse> submit(Flow.Publisher<DataChunk> requestEntity);

    /**
     * Performs prepared request and submitting request entity.
     *
     * When response is received, it is not converted to any other specific type and returned {@link CompletionStage}
     * is notified.
     *
     * @param requestEntity request entity
     * @return request completion stage
     */
    Single<WebClientResponse> submit(Object requestEntity);

    /**
     * Performs prepared request and submitting request entity using a marshalling function.
     *
     * When response is received, it is not converted to any other specific type and returned {@link CompletionStage}
     * is notified.
     *
     * @param function marshalling function
     * @return request completion stage
     */
    Single<WebClientResponse> submit(Function<MessageBodyWriterContext, Flow.Publisher<DataChunk>> function);

    /**
     * Request to a server. Contains all information about used request headers, configuration etc.
     */
    interface ClientRequest {
        /**
         * Returns an HTTP request method. See also {@link Http.Method HTTP standard methods} utility class.
         *
         * @return an HTTP method
         * @see Http.Method
         */
        Http.Method method();

        /**
         * Returns an HTTP version from the request line.
         * <p>
         * See {@link Http.Version HTTP Version} enumeration for supported versions.
         * <p>
         * If communication starts as a {@code HTTP/1.1} with {@code h2c} upgrade, then it will be automatically
         * upgraded and this method returns {@code HTTP/2.0}.
         *
         * @return an HTTP version
         */
        Http.Version version();

        /**
         * Returns a Request-URI (or alternatively path) as defined in request line.
         *
         * @return a request URI
         */
        URI uri();

        /**
         * Returns an encoded query string without leading '?' character.
         *
         * @return an encoded query string
         */
        String query();

        /**
         * Returns query parameters.
         *
         * @return an parameters representing query parameters
         */
        UriQuery queryParams();

        /**
         * Returns a path which was accepted by matcher in actual routing. It is path without a context root
         * of the routing.
         * <p>
         * Use {@link io.helidon.common.uri.UriPath#absolute()} method to obtain absolute request URI path representation.
         * <p>
         * Returned {@link io.helidon.common.uri.UriPath} also provides access to path template parameters.
         * An absolute path then provides access to
         * all (including) context parameters if any. In case of conflict between parameter names, most recent value is returned.
         *
         * @return a path
         */
        UriPath path();

        /**
         * Returns a decoded request URI fragment without leading hash '#' character.
         *
         * @return a decoded URI fragment
         */
        String fragment();
        /**
         * Headers which are used in current request.
         *
         * @return request headers
         */
        WebClientRequestHeaders headers();

        /**
         * Request immutable list of properties.
         *
         * @return request properties
         */
        Map<String, String> properties();

        /**
         * Proxy used by current request.
         *
         * @return proxy
         */
        Proxy proxy();

        /**
         * Returns how many times our request has been redirected.
         *
         * @return redirection count
         */
        int redirectionCount();

    }
}
