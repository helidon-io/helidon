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
package io.helidon.webclient.api;

import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import io.helidon.common.tls.Tls;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

@Isolated
class WebClientProtocolInventoryTest {

    @Test
    void rejectsProtocolInventoryAccessDuringConstruction() {
        TestHttpClientSpiProvider.reset();

        WebClient client = WebClient.builder()
                .servicesDiscoverServices(false)
                .protocolPreference(List.of(TestHttpClientSpiProvider.PROTOCOL_ID))
                .build();
        try {
            IllegalStateException failure = TestHttpClientSpiProvider.constructionFailure();
            assertThat(failure, is(notNullValue()));
            assertThat(failure.getMessage(), is("TCP protocol IDs are not available during WebClient construction"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void synchronouslyDispatchesAndIsolatesProtocolNotificationFailures() {
        TestHttpClientSpiProvider.reset();
        WebClient client = WebClient.builder()
                .servicesDiscoverServices(false)
                .protocolPreference(List.of(TestHttpClientSpiProvider.PROTOCOL_ID))
                .build();
        try {
            WebClientProtocolResponse response = protocolResponse();

            client.responseReceived(response);

            assertThat(TestHttpClientSpiProvider.protocolResponse(), sameInstance(response));
            TestHttpClientSpiProvider.failResponseNotification();
            client.responseReceived(response);
            assertThat(TestHttpClientSpiProvider.protocolResponse(), sameInstance(response));
        } finally {
            client.closeResource();
        }
    }

    private static WebClientProtocolResponse protocolResponse() {
        ClientUri uri = ClientUri.create(URI.create("http://origin.example"));
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           Tls.builder().enabled(false).build(),
                                                           (_, _) -> InetAddress.getLoopbackAddress(),
                                                           DnsAddressLookup.IPV4,
                                                           Proxy.noProxy());
        ResolvedClientTarget target = ClientConnectionTarget.create(connectionKey, "http").resolve();
        return WebClientProtocolResponse.create(target,
                                                false,
                                                "http/1.1",
                                                Status.OK_200,
                                                ClientResponseHeaders.create(WritableHeaders.create()),
                                                Instant.parse("2026-08-23T00:01:30Z"));
    }
}
