/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.se;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.http.Method;
import io.helidon.http.media.MediaSupport;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1ClientProtocolConfig;
import io.helidon.webclient.metrics.WebClientMetrics;
import io.helidon.webclient.spi.WebClientService;

@SuppressWarnings("ALL")
class WebClientSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        WebClient client = WebClient.builder()
                .baseUri("http://localhost")
                .build();
        // end::snippet_1[]
    }

    void snippet_5() {
        // tag::snippet_5[]
        Config config = Config.create();
        WebClient client = WebClient.builder()
                .baseUri("http://localhost")
                .config(config.get("client"))
                .build();
        // end::snippet_5[]
    }

    void snippet_2(WebClient client) {
        // tag::snippet_2[]
        ClientResponseTyped<String> response = client.get()
                .path("/endpoint")
                .request(String.class);
        String entityString = response.entity();
        // end::snippet_2[]
    }

    void snippet_3(WebClient client) {
        // tag::snippet_3[]
        String result = client.get()
                .protocolId("http/1.1")
                .requestEntity(String.class);
        // end::snippet_3[]
    }

    abstract class CustomMediaSupport implements MediaSupport {

        static MediaSupport create() {
            return null;
        }
    }

    void snippet_4() {
        // tag::snippet_4[]
        WebClient.builder()
                .mediaContext(it -> it
                        .addMediaSupport(CustomMediaSupport.create())) // <1>
                .build();
        // end::snippet_4[]
    }

    class Snippet6 {

        static final String PROXY_HOST = "proxy.acmecorp.com";
        static final int PROXY_PORT = 80;

        void snippet_6() {
            // tag::snippet_6[]
            Proxy proxy = Proxy.builder()
                    .type(Proxy.ProxyType.HTTP)
                    .host(PROXY_HOST)
                    .port(PROXY_PORT)
                    .build();
            WebClient.builder()
                    .proxy(proxy)
                    .build();
            // end::snippet_6[]
        }
    }

    class Snippet7 {

        static final String PROXY_HOST = "proxy.acmecorp.com";
        static final String PROXY_PORT = "80";
        static final String TARGET_HOST = "example.com";

        void snippet_7(WebClient client) {
            // tag::snippet_7[]
            Proxy proxy = Proxy.create(); // <1>
            HttpClientResponse response = client.get("/proxiedresource")
                    .proxy(proxy) // <2>
                    .request();
            // end::snippet_7[]
        }
    }

    void snippet_8() {
        // tag::snippet_8[]
        Config config = Config.create(); // <1>
        WebClient.builder()
                .config(config.get("client")) // <2>
                .build();
        // end::snippet_8[]
    }

    void snippet_9() {
        // tag::snippet_9[]
        WebClient.builder()
                .tls(it -> it.trust(t -> t
                        .keystore(k -> k.passphrase("password")
                                .trustStore(true)
                                .keystore(r -> r.resourcePath("client.p12")))))
                .build();
        // end::snippet_9[]
    }

    void snippet_10() {
        // tag::snippet_10[]
        WebClientService clientService = WebClientMetrics.counter()
                .methods(Method.GET)
                .nameFormat("example.metric.%1$s.%2$s")
                .build(); // <1>

        WebClient.builder()
                .addService(clientService) // <2>
                .build();
        // end::snippet_10[]
    }

    void snippet_11() {
        // tag::snippet_11[]
        WebClient.builder()
                .addProtocolConfig(Http1ClientProtocolConfig.builder()
                                           .defaultKeepAlive(false)
                                           .validateRequestHeaders(true)
                                           .validateResponseHeaders(false)
                                           .build())
                .build();
        // end::snippet_11[]
    }

    void snippet_12() {
        WebClient client = WebClient.builder()
                .baseUri("http://localhost")
                .build();
        // tag::snippet_12[]
        client.get()
                .uri("http://example.com") // <1>
                .path("/path") // <2>
                .queryParam("query", "parameter") // <3>
                .fragment("someFragment") // <4>
                .headers(headers -> headers.accept(MediaTypes.APPLICATION_JSON)); // <5>
        // end::snippet_12[]
    }

}
