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

package io.helidon.webclient.tests.http3;

import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientConfig;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;

final class Http3ClientTestSupport {

    private Http3ClientTestSupport() {
    }

    static Http3ClientConfig.Builder strictClientBuilder() {
        return strictClientBuilder(Http3ClientProtocolConfig.builder());
    }

    static Http3ClientConfig.Builder strictClientBuilder(Http3ClientProtocolConfig.Builder protocolConfig) {
        return Http3Client.builder()
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .protocolConfig(protocolConfig.priorKnowledge(true).build());
    }

    static WebClientConfig.Builder strictWebClientBuilder() {
        return WebClient.builder()
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .addProtocolConfig(Http3ClientProtocolConfig.builder()
                                           .priorKnowledge(true)
                                           .build());
    }
}
