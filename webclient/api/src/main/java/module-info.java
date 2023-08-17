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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.webclient.spi.DnsResolverProvider;
import io.helidon.webclient.spi.HttpClientSpiProvider;
import io.helidon.webclient.spi.ProtocolConfigProvider;
import io.helidon.webclient.spi.SourceHandlerProvider;
import io.helidon.webclient.spi.WebClientServiceProvider;

/**
 * Helidon WebClient API.
 */
@Feature(value = "WebClient",
         description = "WebClient",
         in = HelidonFlavor.SE,
         path = "WebClient"
)
module io.helidon.webclient.api {
    // @Feature
    requires static io.helidon.common.features.api;
    // @ConfiguredOption etc
    requires static io.helidon.config.metadata;
    requires static io.helidon.inject.configdriven.api;
    requires static io.helidon.inject.configdriven.runtime;
    // Injection support
    requires static jakarta.inject;

    // @Builder - interfaces are a runtime dependency
    requires io.helidon.builder.api;

    requires transitive io.helidon.common;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.http;
    requires transitive io.helidon.common.configurable;
    requires transitive io.helidon.common.socket;
    requires transitive io.helidon.common.uri;
    requires transitive io.helidon.common.tls;
    requires transitive io.helidon.http.encoding;
    requires transitive io.helidon.http.media;
    requires transitive io.helidon.common.config;

    exports io.helidon.webclient.api;
    exports io.helidon.webclient.spi;

    uses DnsResolverProvider;
    uses SourceHandlerProvider;
    uses WebClientServiceProvider;
    uses ProtocolConfigProvider;
    uses HttpClientSpiProvider;
}