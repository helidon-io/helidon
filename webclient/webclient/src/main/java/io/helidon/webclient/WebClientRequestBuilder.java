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

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.context.Context;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpRequest;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.webclient.spi.WebClientService;

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
     * Add a property to be used by a {@link WebClientService}.
     *
     * @param propertyName  property name
     * @param propertyValue property value
     * @return updated builder instance
     */
    WebClientRequestBuilder property(String propertyName, String... propertyValue);

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
     * Configure headers. Copy all headers from supplied {@link Headers} instance.
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
     * Configure query parameters.
     *
     * Appends these query parameters to the query parameters defined in the request uri.
     *
     * Copy all query parameters from supplied {@link Parameters} instance.
     *
     * @param queryParams to copy
     * @return updated builder instance
     */
    WebClientRequestBuilder queryParams(Parameters queryParams);

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
    WebClientRequestBuilder path(HttpRequest.Path path);

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
    WebClientRequestBuilder contentType(MediaType contentType);

    /**
     * Media types which are accepted in the response.
     *
     * @param mediaTypes media types
     * @return updated builder instance
     */
    WebClientRequestBuilder accept(MediaType... mediaTypes);

    /**
     * Performs prepared request and transforms response to requested type.
     *
     * When transformation is done the returned {@link CompletionStage} is notified.
     *
     * @param responseType requested response type
     * @param <T>          response type
     * @return request completion stage
     */
    <T> CompletionStage<T> request(Class<T> responseType);

    /**
     * Performs prepared request and transforms response to requested type.
     *
     * When transformation is done the returned {@link CompletionStage} is notified.
     *
     * @param responseType requested response type
     * @param <T>          response type
     * @return request completion stage
     */
    <T> CompletionStage<T> request(GenericType<T> responseType);

    /**
     * Performs prepared request without expecting to receive any specific type.
     *
     * Response is not converted and returned {@link CompletionStage} is notified.
     *
     * @return request completion stage
     */
    default CompletionStage<WebClientResponse> request() {
        return request(WebClientResponse.class);
    }

    /**
     * Performs prepared request.
     *
     * When response is received, it is not converted to any other specific type and returned {@link CompletionStage}
     * is notified.
     *
     * @return request completion stage
     */
    CompletionStage<WebClientResponse> submit();

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
    <T> CompletionStage<T> submit(Flow.Publisher<DataChunk> requestEntity, Class<T> responseType);

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
    <T> CompletionStage<T> submit(Object requestEntity, Class<T> responseType);

    /**
     * Performs prepared request and submitting request entity using {@link Flow.Publisher}.
     *
     * When response is received, it is not converted to any other specific type and returned {@link CompletionStage}
     * is notified.
     *
     * @param requestEntity request entity
     * @return request completion stage
     */
    CompletionStage<WebClientResponse> submit(Flow.Publisher<DataChunk> requestEntity);

    /**
     * Performs prepared request and submitting request entity.
     *
     * When response is received, it is not converted to any other specific type and returned {@link CompletionStage}
     * is notified.
     *
     * @param requestEntity request entity
     * @return request completion stage
     */
    CompletionStage<WebClientResponse> submit(Object requestEntity);

    /**
     * Request to a server. Contains all information about used request headers, configuration etc.
     */
    interface ClientRequest extends HttpRequest {

        /**
         * Headers which are used in current request.
         *
         * @return request headers
         */
        WebClientRequestHeaders headers();

        /**
         * Current request configuration.
         *
         * @return request configuration
         */
        RequestConfiguration configuration();

        Map<String, List<String>> properties();

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
