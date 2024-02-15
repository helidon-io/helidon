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
package io.helidon.docs.mp.jaxrs;

import java.net.URI;
import java.util.List;

import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.jersey.connector.HelidonConnectorProvider;
import io.helidon.jersey.connector.HelidonProperties;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;

@SuppressWarnings("ALL")
class HelidonConnectorSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.connectorProvider(HelidonConnectorProvider.create());       // Helidon connector
        Client client = ClientBuilder.newClient(clientConfig);
        // end::snippet_1[]
    }

    void snippet_2(ClientConfig clientConfig, Config config) {
        // tag::snippet_2[]
        clientConfig.property(HelidonProperties.CONFIG, config.get("my.webclient"));
        // end::snippet_2[]
    }

    void snippet_3(URI uri) {
        // tag::snippet_3[]
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.connectorProvider(HelidonConnectorProvider.create());
        Client client = ClientBuilder.newClient(clientConfig);

        WebTarget webTarget = client.target(uri)
                .property(HelidonProperties.PROTOCOL_ID, Http2Client.PROTOCOL_ID);      // HTTP2 upgrade
        try (Response response = webTarget.request().get()) {
            // ...
        }
        // end::snippet_3[]
    }

    void snippet_4(URI uri) {
        // tag::snippet_4[]
        Tls tls = Tls.builder()
                .trustAll(true)
                .addApplicationProtocol(Http2Client.PROTOCOL_ID)        // HTTP/2 upgrade
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                .build();

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.connectorProvider(HelidonConnectorProvider.create());
        clientConfig.property(HelidonProperties.TLS, tls);
        Client client = ClientBuilder.newClient(clientConfig);

        WebTarget webTarget = client.target(uri);
        try (Response response = webTarget.request().get()) {
            // ...
        }
        // end::snippet_4[]
    }

    void snippet_5(URI uri) {
        // tag::snippet_5[]
        Tls tls = Tls.builder()
                .trustAll(true)
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                .build();

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.connectorProvider(HelidonConnectorProvider.create());
        clientConfig.property(HelidonProperties.TLS, tls);
        clientConfig.property(HelidonProperties.PROTOCOL_CONFIGS,
                              List.of(Http2ClientProtocolConfig.builder()
                                              .priorKnowledge(true)    // HTTP/2 knowlege
                                              .build()));
        Client client = ClientBuilder.newClient(clientConfig);

        WebTarget webTarget = client.target(uri);
        try (Response response = webTarget.request().get()) {
            // ...
        }
        // end::snippet_5[]
    }

}
