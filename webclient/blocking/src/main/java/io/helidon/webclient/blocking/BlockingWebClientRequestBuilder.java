/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.webclient.blocking;

import java.net.URI;
import java.net.URL;
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
import io.helidon.common.http.HttpRequest;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.webclient.Proxy;
import io.helidon.webclient.WebClientRequestHeaders;
import io.helidon.webclient.spi.WebClientService;


/**
 * Fluent API builder that is used by WebClient to create an outgoing request.
 */
public interface BlockingWebClientRequestBuilder {

    /**
     * String representation of request uri.
     * <p>
     * Replaces baseUri defined in client builder.
     *
     * @param uri request uri
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder uri(String uri);

    /**
     * Request {@link URL}.
     * <p>
     * Replaces baseUri defined in client builder.
     *
     * @param url request url
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder uri(URL url);

    /**
     * Request {@link URI}.
     * <p>
     * Replaces baseUri defined in client builder.
     *
     * @param uri request uri
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder uri(URI uri);

    /**
     * Disables final uri encoding.
     * <p>
     * This setting skips all parts of {@link URI} from encoding.
     *
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder skipUriEncoding();

    /**
     * Sets if redirects should be followed at this request or not.
     *
     * @param followRedirects follow redirects
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder followRedirects(boolean followRedirects);

    /**
     * Add a property to be used by a {@link WebClientService}.
     *
     * @param propertyName  property name
     * @param propertyValue property value
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder property(String propertyName, String propertyValue);

    /**
     * Explicitly configure a context to use.
     * This method is not needed when running within a scope of a Helidon server, such as
     * Web Server, gRPC Server, MicroProfile Server, or when processing a Helidon message consumer.
     *
     * @param context context to be used by the outbound request, to look for security context, parent tracing span and similar
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder context(Context context);

    /**
     * Get a (mutable) instance of outgoing headers.
     *
     * @return client request headers
     */
    BlockingWebClientRequestBuilder headers();

    /**
     * Add a query parameter.
     * <p>
     * Appends these query parameters to the query parameters defined in the request uri.
     *
     * @param name   query name
     * @param values query value
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder queryParam(String name, String... values);

    /**
     * Override client proxy configuration.
     *
     * @param proxy request proxy
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder proxy(Proxy proxy);

    /**
     * Configure headers. Copy all headers from supplied {@link Headers} instance.
     *
     * @param headers to copy
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder headers(Headers headers);

    /**
     * Function from parameter is executed on top of stored headers.
     * <p>
     * This approach is here to allow as to add any header via specialized method while using builder pattern.
     *
     * @param headers function which adds headers
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder headers(Function<WebClientRequestHeaders, Headers> headers);

    /**
     * Configure query parameters.
     * <p>
     * Appends these query parameters to the query parameters defined in the request uri.
     * <p>
     * Copy all query parameters from supplied {@link Parameters} instance.
     *
     * @param queryParams to copy
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder queryParams(Parameters queryParams);

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
    BlockingWebClientRequestBuilder httpVersion(Http.Version httpVersion);

    /**
     * Sets new connection timeout for this request.
     *
     * @param amount amount of time
     * @param unit   time unit
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder connectTimeout(long amount, TimeUnit unit);

    /**
     * Sets new read timeout for this request.
     *
     * @param amount amount of time
     * @param unit   time unit
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder readTimeout(long amount, TimeUnit unit);

    /**
     * Fragment of the request.
     * <p>
     * Replaces fragment defined in the request uri.
     *
     * @param fragment request fragment
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder fragment(String fragment);

    /**
     * Path of the request.
     * <p>
     * Appends this path to the path defined in the request uri.
     *
     * @param path path
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder path(HttpRequest.Path path);

    /**
     * Path of the request.
     * <p>
     * Appends this path to the path defined in the request uri.
     *
     * @param path path
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder path(String path);

    /**
     * Content type of the request.
     *
     * @param contentType content type
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder contentType(MediaType contentType);

    /**
     * Media types which are accepted in the response.
     *
     * @param mediaTypes media types
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder accept(MediaType... mediaTypes);

    /**
     * Whether connection should be kept alive after request.
     *
     * @param keepAlive keep alive
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder keepAlive(boolean keepAlive);

    /**
     * Set new request id. This id is used in logging messages.
     *
     * @param requestId new request id
     * @return updated builder instance
     */
    BlockingWebClientRequestBuilder requestId(long requestId);

    /**
     * Performs prepared request and transforms response to requested type.
     *
     * @param responseType requested response type
     * @param <T>          response type
     * @return request completion stage
     */
    <T> T request(Class<T> responseType);

    /**
     * Performs prepared request and transforms response to requested type.
     * <p>
     * When transformation is done the returned {@link CompletionStage} is notified.
     *
     * @param responseType requested response type
     * @param <T>          response type
     * @return request completion stage
     */
    <T> T request(GenericType<T> responseType);

    /**
     * Performs prepared request without expecting to receive any specific type.
     *
     * @return request
     */
    BlockingWebClientResponse request();

    /**
     * Performs prepared request.
     *
     * @return request
     */
    BlockingWebClientResponse submit();

    /**
     * Performs prepared request and submitting request entity using {@link Flow.Publisher}.
     *
     * @param requestEntity request entity
     * @param responseType  requested response type
     * @param <T>           response type
     * @return request
     */
    <T> T submit(Flow.Publisher<DataChunk> requestEntity, Class<T> responseType);

    /**
     * Performs prepared request and submitting request entity.
     *
     * @param requestEntity request entity
     * @param responseType  requested response type
     * @param <T>           response type
     * @return request
     */
    <T> T submit(Object requestEntity, Class<T> responseType);

    /**
     * Performs prepared request and submitting request entity using {@link Flow.Publisher}.
     *
     * @param requestEntity request entity
     * @return request
     */
    BlockingWebClientResponse submit(Flow.Publisher<DataChunk> requestEntity);

    /**
     * Performs prepared request and submitting request entity.
     *
     * @param requestEntity request entity
     * @return request completion stage
     */
    BlockingWebClientResponse submit(Object requestEntity);

    /**
     * Performs prepared request and submitting request entity using a marshalling function.
     *
     * @param function marshalling function
     * @return request completion stage
     */
    BlockingWebClientResponse submit(Function<MessageBodyWriterContext, Flow.Publisher<DataChunk>> function);

    /**
     * Request to a server. Contains all information about used request headers, configuration etc.
     */
    interface BlockingClientRequest extends HttpRequest {

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
