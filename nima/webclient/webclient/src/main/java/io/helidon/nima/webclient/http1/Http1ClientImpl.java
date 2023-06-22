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

package io.helidon.nima.webclient.http1;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.common.http.Http;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.LoomClient;
import io.helidon.nima.webclient.UriHelper;
import io.helidon.nima.webclient.spi.WebClientService;
import io.helidon.nima.webclient.spi.WebClientServiceProvider;

class Http1ClientImpl extends LoomClient implements Http1Client {
    private static final Tls EMPTY_TLS = Tls.builder().build();
    private final Http1ClientConfig clientConfig;

    Http1ClientImpl(Http1ClientConfig clientConfig) {
        super(Http1Client.builder()
                      .update(it -> clientConfig.baseUri().ifPresent(it::baseUri))
                      .update(it -> clientConfig.tls().ifPresent(it::tls))
                      .channelOptions(clientConfig.socketOptions())
                      .dnsResolver(clientConfig.dnsResolver())
                      .dnsAddressLookup(clientConfig.dnsAddressLookup()));

        this.clientConfig = Http1ClientConfigDefault.toBuilder(clientConfig)
                .services(Set.of()) // reset services to empty list
                .update(it -> services(clientConfig).forEach(it::addService)) // add all configured services
                .update(it -> it.tls(it.tls().orElse(EMPTY_TLS)))
                .build();
    }

    @Override
    public Http1ClientRequest method(Http.Method method) {
        UriQueryWriteable query = UriQueryWriteable.create();
        UriHelper helper = (uri() == null) ? UriHelper.create() : UriHelper.create(uri(), query);

        return new ClientRequestImpl(clientConfig, method, helper, query, properties());
    }

    Http1ClientConfig clientConfig() {
        return clientConfig;
    }

    private static List<WebClientService> services(Http1ClientConfig clientConfig) {
        Config empty = Config.empty();
        List<WebClientService> services = new ArrayList<>(clientConfig.services());

        services.addAll(HelidonServiceLoader
                                .builder(ServiceLoader.load(WebClientServiceProvider.class))
                                .useSystemServiceLoader(clientConfig.servicesUseServiceLoader())
                                .build()
                                .stream()
                                .map(it -> it.create(empty))
                                .toList());

        return services;
    }
}
