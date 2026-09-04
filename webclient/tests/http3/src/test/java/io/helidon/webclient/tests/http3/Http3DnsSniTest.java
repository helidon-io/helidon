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

import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webclient.spi.DnsResolver;
import io.helidon.webserver.http.AltSvc;
import io.helidon.webserver.http1.Http1Config;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class Http3DnsSniTest {
    private static final String FAKE_HOST = "http3.invalid";
    private static final String HELLO = "Hello";

    @Test
    void shouldUseResolverAndExplicitSniForPriorKnowledgeHttp3() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        AtomicReference<String> resolvedHost = new AtomicReference<>();

        try (TestEnvironment environment = TestEnvironment.createSharedListener(
                routing -> routing.get("/hello", (req, res) -> res.send(HELLO)))) {
            DnsResolver resolver = (host, lookup) -> {
                resolvedHost.set(host);
                resolutions.incrementAndGet();
                return InetAddress.getLoopbackAddress();
            };
            Http3Client client = strictClientBuilder()
                    .baseUri(fakeBaseUri(environment))
                    .dnsResolver(resolver)
                    .sni(it -> it.mode(SniMode.EXPLICIT).host("localhost"))
                    .tls(environment.clientTlsHttp3())
                    .build();

            try {
                try (Http3ClientResponse response = client.get("/hello").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.entity().as(String.class), is(HELLO));
                }
            } finally {
                client.closeResource();
            }
        }

        assertThat(resolvedHost.get(), is(FAKE_HOST));
        assertThat(resolutions.get(), greaterThan(0));
    }

    @Test
    void shouldUseResolverAndExplicitRequestSniForAltSvcHttp3() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        AtomicReference<String> resolvedHost = new AtomicReference<>();
        Http1Config http1 = Http1Config.builder()
                .altSvc(AltSvc.builder().build())
                .build();

        try (TestEnvironment environment = TestEnvironment.createSharedListener(
                http1,
                routing -> routing.get("/hello", (req, res) -> res.send(HELLO)))) {
            DnsResolver resolver = (host, lookup) -> {
                resolvedHost.set(host);
                resolutions.incrementAndGet();
                return InetAddress.getLoopbackAddress();
            };
            WebClient client = WebClient.builder()
                    .baseUri(fakeBaseUri(environment))
                    .shareConnectionCache(false)
                    .proxy(Proxy.noProxy())
                    .dnsResolver(resolver)
                    .tls(environment.clientTls())
                    .altSvc(ClientAltSvcConfig.create())
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .addProtocolPreference(Http1Client.PROTOCOL_ID)
                    .addProtocolConfig(Http3ClientProtocolConfig.create())
                    .build();

            try {
                try (HttpClientResponse first = client.get("/hello")
                        .sni(it -> it.mode(SniMode.EXPLICIT).host("localhost"))
                        .header(HeaderNames.HOST, "localhost:" + URI.create(environment.baseUri()).getPort())
                        .request()) {
                    assertThat(first.status(), is(Status.OK_200));
                    assertThat(first.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(first.headers().contains(HeaderNames.ALT_SVC), is(true));
                    assertThat(first.entity().as(String.class), is(HELLO));
                }
                int resolutionsAfterTcp = resolutions.get();
                assertThat(resolvedHost.get(), is(FAKE_HOST));

                try (HttpClientResponse second = client.get("/hello")
                        .sni(it -> it.mode(SniMode.EXPLICIT).host("localhost"))
                        .header(HeaderNames.HOST, "localhost:" + URI.create(environment.baseUri()).getPort())
                        .request()) {
                    assertThat(second.status(), is(Status.OK_200));
                    assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(second.entity().as(String.class), is(HELLO));
                }

                assertThat(resolutions.get(), greaterThan(resolutionsAfterTcp));
            } finally {
                client.closeResource();
            }
        }

        assertThat(resolvedHost.get(), is("localhost"));
    }

    @Test
    void shouldUseFinalHostHeaderForSniAndAuthority() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListener(
                routing -> routing.get("/hello", (req, res) -> res.send(HELLO)))) {
            DnsResolver resolver = (host, lookup) -> InetAddress.getLoopbackAddress();
            int port = URI.create(environment.baseUri()).getPort();
            Http3Client client = strictClientBuilder()
                    .baseUri(fakeBaseUri(environment))
                    .dnsResolver(resolver)
                    .sni(it -> it.mode(SniMode.HOST_HEADER))
                    .tls(environment.clientTlsHttp3())
                    .build();

            try {
                try (Http3ClientResponse response = client.get("/hello")
                        .header(HeaderNames.HOST, "localhost:" + port)
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.entity().as(String.class), is(HELLO));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldLearnAltSvcForFinalHostAuthority() throws Exception {
        AtomicReference<String> resolvedHost = new AtomicReference<>();
        Http1Config http1 = Http1Config.builder()
                .altSvc(AltSvc.builder().build())
                .build();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(
                http1,
                routing -> routing.get("/hello", (req, res) -> res.send(HELLO)))) {
            DnsResolver resolver = (host, lookup) -> {
                resolvedHost.set(host);
                return InetAddress.getLoopbackAddress();
            };
            int port = URI.create(environment.baseUri()).getPort();
            WebClient client = WebClient.builder()
                    .baseUri(fakeBaseUri(environment))
                    .shareConnectionCache(false)
                    .proxy(Proxy.noProxy())
                    .dnsResolver(resolver)
                    .tls(environment.clientTls())
                    .altSvc(ClientAltSvcConfig.create())
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .addProtocolPreference(Http1Client.PROTOCOL_ID)
                    .addProtocolConfig(Http3ClientProtocolConfig.create())
                    .build();

            try {
                try (HttpClientResponse first = client.get("/hello")
                        .sni(it -> it.mode(SniMode.EXPLICIT).host("localhost"))
                        .header(HeaderNames.HOST, "localhost:" + port)
                        .request();
                     HttpClientResponse second = client.get("/hello")
                             .sni(it -> it.mode(SniMode.EXPLICIT).host("localhost"))
                             .header(HeaderNames.HOST, "localhost:" + port)
                             .request()) {
                    assertThat(first.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(second.entity().as(String.class), is(HELLO));
                }
            } finally {
                client.closeResource();
            }
        }

        assertThat(resolvedHost.get(), is("localhost"));
    }

    private static String fakeBaseUri(TestEnvironment environment) {
        return "https://" + FAKE_HOST + ':' + URI.create(environment.baseUri()).getPort();
    }
}
