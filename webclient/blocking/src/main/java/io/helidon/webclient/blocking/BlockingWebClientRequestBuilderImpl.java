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
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientRequestHeaders;



class BlockingWebClientRequestBuilderImpl implements BlockingWebClientRequestBuilder {
    private final WebClientRequestBuilder builder;

    BlockingWebClientRequestBuilderImpl(WebClientRequestBuilder builder) {
        this.builder = builder;
    }

    static BlockingWebClientRequestBuilderImpl create(WebClientRequestBuilder webClientRequestBuilder) {
        return new BlockingWebClientRequestBuilderImpl(webClientRequestBuilder);
    }

    @Override
    public BlockingWebClientRequestBuilder uri(String uri) {
        builder.uri(uri);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder uri(URL url) {
        builder.uri(url);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder uri(URI uri) {
        builder.uri(uri);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder skipUriEncoding() {
        builder.skipUriEncoding();
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder followRedirects(boolean followRedirects) {
        builder.followRedirects(followRedirects);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder property(String propertyName, String propertyValue) {
        builder.property(propertyName, propertyValue);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder context(Context context) {
        builder.context(context);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder headers() {
        builder.headers();
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder queryParam(String name, String... values) {
        builder.queryParam(name, values);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder proxy(Proxy proxy) {
        builder.proxy(proxy);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder headers(Headers headers) {
        builder.headers(headers);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder headers(Function<WebClientRequestHeaders, Headers> headers) {
        builder.headers(headers);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder queryParams(Parameters queryParams) {
        builder.queryParams(queryParams);
        return this;
    }

    @Override
    public MessageBodyReaderContext readerContext() {
        return builder.readerContext();
    }

    @Override
    public MessageBodyWriterContext writerContext() {
        return builder.writerContext();
    }

    @Override
    public BlockingWebClientRequestBuilder httpVersion(Http.Version httpVersion) {
        builder.httpVersion(httpVersion);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder connectTimeout(long amount, TimeUnit unit) {
        builder.connectTimeout(amount, unit);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder readTimeout(long amount, TimeUnit unit) {
        builder.readTimeout(amount, unit);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder fragment(String fragment) {
        builder.fragment(fragment);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder path(HttpRequest.Path path) {
        builder.path(path);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder path(String path) {
        builder.path(path);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder contentType(MediaType contentType) {
        builder.contentType((contentType));
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder accept(MediaType... mediaTypes) {
        builder.accept(mediaTypes);
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder keepAlive(boolean keepAlive) {
        builder.keepAlive((keepAlive));
        return this;
    }

    @Override
    public BlockingWebClientRequestBuilder requestId(long requestId) {
        builder.requestId(requestId);
        return this;
    }

    @Override
    public <T> T request(Class<T> responseType) {
        return builder.request(responseType).await();
    }

    @Override
    public <T> T request(GenericType<T> responseType) {
        return builder.request(responseType).await();
    }

    @Override
    public BlockingWebClientResponse request() {
        return new BlockingWebClientResponseImpl(builder.request().await());
    }

    @Override
    public BlockingWebClientResponse submit() {
        return new BlockingWebClientResponseImpl(builder.submit().await());
    }

    @Override
    public <T> T submit(Flow.Publisher<DataChunk> requestEntity, Class<T> responseType) {
        return builder.submit(requestEntity, responseType).await();
    }

    @Override
    public <T> T submit(Object requestEntity, Class<T> responseType) {
        return builder.submit(requestEntity, responseType).await();
    }

    @Override
    public BlockingWebClientResponse submit(Flow.Publisher<DataChunk> requestEntity) {
        return new BlockingWebClientResponseImpl(builder.submit(requestEntity).await());
    }

    @Override
    public BlockingWebClientResponse submit(Object requestEntity) {
        return new BlockingWebClientResponseImpl(builder.submit(requestEntity).await());
    }

    @Override
    public BlockingWebClientResponse submit(Function<MessageBodyWriterContext, Flow.Publisher<DataChunk>> function) {
        return new BlockingWebClientResponseImpl(builder.submit(function).await());
    }
}
