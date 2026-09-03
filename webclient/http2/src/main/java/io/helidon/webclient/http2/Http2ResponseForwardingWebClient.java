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

package io.helidon.webclient.http2;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import io.helidon.http.Method;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.api.WebClientProtocolResponse;
import io.helidon.webclient.spi.Protocol;
import io.helidon.webclient.spi.ProtocolConfig;

final class Http2ResponseForwardingWebClient implements WebClient {
    private final WebClient delegate;
    private final Consumer<WebClientProtocolResponse> responsePublisher;

    Http2ResponseForwardingWebClient(WebClient delegate,
                                     Consumer<WebClientProtocolResponse> responsePublisher) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.responsePublisher = Objects.requireNonNull(responsePublisher, "responsePublisher");
    }

    @Override
    public HttpClientRequest method(Method method) {
        return delegate.method(method);
    }

    @Override
    public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol, C protocolConfig) {
        return delegate.client(protocol, protocolConfig);
    }

    @Override
    public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol) {
        return delegate.client(protocol);
    }

    @Override
    public List<String> tcpProtocolIds() {
        return delegate.tcpProtocolIds();
    }

    @Override
    public void responseReceived(WebClientProtocolResponse response) {
        responsePublisher.accept(Http1FallbackService.response(response));
    }

    @Override
    public ExecutorService executor() {
        return delegate.executor();
    }

    @Override
    public WebClientCookieManager cookieManager() {
        return delegate.cookieManager();
    }

    @Override
    public WebClientConfig prototype() {
        return delegate.prototype();
    }

    @Override
    public void releaseResource() {
        delegate.releaseResource();
    }

    @Override
    public void closeResource() {
        delegate.closeResource();
    }
}
