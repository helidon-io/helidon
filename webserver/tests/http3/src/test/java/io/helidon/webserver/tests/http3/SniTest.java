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

package io.helidon.webserver.tests.http3;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;

import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.quic.QuicClientConnection;
import io.helidon.quic.QuicClientRuntime;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.QuicVersion;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.SniSelectionPolicy;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SniTest {
    private static final String SNI_HOST = "api.example.com";
    private static final String OTHER_HOST = "admin.example.com";

    @Test
    void acceptsMatchingAuthority() throws Exception {
        try (Http3TestSupport.TestEnvironment environment = environment(SniSelectionPolicy.FALLBACK,
                                                                        SniSelectionPolicy.FALLBACK,
                                                                        new AtomicInteger())) {
            Http3Client client = client(environment);
            try {
                try (Http3ClientResponse response = client.get("/")
                        .header(HeaderNames.HOST, SNI_HOST)
                        .sni(sni -> sni.mode(SniMode.EXPLICIT)
                                .host(SNI_HOST))
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.as(String.class), is("ok"));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void exposesSniHosts() throws Exception {
        try (Http3TestSupport.TestEnvironment environment = environment(SniSelectionPolicy.FALLBACK,
                                                                        SniSelectionPolicy.FALLBACK,
                                                                        new AtomicInteger())) {
            Http3Client client = client(environment);
            try {
                try (Http3ClientResponse response = client.get("/sni")
                        .header(HeaderNames.HOST, SNI_HOST)
                        .sni(sni -> sni.mode(SniMode.EXPLICIT)
                                .host(SNI_HOST))
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.as(String.class), is(SNI_HOST + "|" + SNI_HOST));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void rejectsAuthorityMismatchWithoutClosingConnection() throws Exception {
        AtomicInteger routeInvocations = new AtomicInteger();
        try (Http3TestSupport.TestEnvironment environment = environment(SniSelectionPolicy.FALLBACK,
                                                                        SniSelectionPolicy.FALLBACK,
                                                                        routeInvocations);
             Http3TestSupport.LowLevelHttp3Client client = Http3TestSupport.LowLevelHttp3Client.create(
                     environment,
                     List.of(new SNIHostName(SNI_HOST)))) {
            Http3TestSupport.DecodedResponse first = client.request(environment.uri("/socket-id"),
                                                                    "GET",
                                                                    authority(SNI_HOST));
            Http3TestSupport.DecodedResponse rejected = client.request(environment.uri("/socket-id"),
                                                                       "GET",
                                                                       authority(OTHER_HOST));
            Http3TestSupport.DecodedResponse second = client.request(environment.uri("/socket-id"),
                                                                     "GET",
                                                                     authority(SNI_HOST));
            String firstSocketId = new String(first.body(), StandardCharsets.UTF_8);
            String secondSocketId = new String(second.body(), StandardCharsets.UTF_8);

            assertThat(first.status(), is(200));
            assertThat(rejected.status(), is(421));
            assertThat(second.status(), is(200));
            assertThat(secondSocketId, is(firstSocketId));
            assertThat(routeInvocations.get(), is(2));
        }
    }

    @Test
    void rejectsUnmatchedSniDuringTlsHandshake() throws Exception {
        AtomicInteger routeInvocations = new AtomicInteger();
        try (Http3TestSupport.TestEnvironment environment = environment(SniSelectionPolicy.FALLBACK,
                                                                        SniSelectionPolicy.REJECT,
                                                                        routeInvocations);
             RejectedHandshakeClient client = RejectedHandshakeClient.create(environment,
                                                                              List.of(new SNIHostName(OTHER_HOST)))) {
            CompletableFuture<?> handshake = client.connection().startHandshake();
            QuicTermination termination = client.connection()
                    .whenTerminated()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertThat(termination.origin(), is(QuicTermination.Origin.PEER));
            assertThat(termination.kind(), is(QuicTermination.Kind.CONNECTION_CLOSE));
            assertThat(termination.layer(), is(QuicTermination.Layer.TRANSPORT));
            assertThat(termination.errorCode().orElseThrow(), is(0x170L));
            assertThat(handshake.isCompletedExceptionally(), is(true));
            assertThat(routeInvocations.get(), is(0));
        }
    }

    @Test
    void rejectsMissingSniDuringTlsHandshake() throws Exception {
        AtomicInteger routeInvocations = new AtomicInteger();
        try (Http3TestSupport.TestEnvironment environment = environment(SniSelectionPolicy.REJECT,
                                                                        SniSelectionPolicy.FALLBACK,
                                                                        routeInvocations);
             RejectedHandshakeClient client = RejectedHandshakeClient.create(environment, List.of())) {
            CompletableFuture<?> handshake = client.connection().startHandshake();

            assertThrows(TimeoutException.class,
                         () -> client.connection()
                                 .whenTerminated()
                                 .toCompletableFuture()
                                 .get(2, TimeUnit.SECONDS));
            assertThat(handshake.isDone(), is(false));
            assertThat(routeInvocations.get(), is(0));
        }
    }

    private static Http3TestSupport.TestEnvironment environment(SniSelectionPolicy missing,
                                                                SniSelectionPolicy unmatched,
                                                                AtomicInteger routeInvocations) throws Exception {
        Tls tls = Http3TestSupport.serverTls("server.p12");
        return Http3TestSupport.sharedListener(server -> server
                                                       .sni(sni -> sni.missing(missing)
                                                               .unmatched(unmatched))
                                                       .addVirtualHost(virtualHost -> virtualHost
                                                               .host(SNI_HOST)
                                                               .tls(tls)),
                                               routing -> routing
                                                       .get("/", (_, res) -> res.send("ok"))
                                                       .get("/sni", (req, res) -> res.send(
                                                               req.sniRequestedHost().orElse("")
                                                                       + "|"
                                                                       + req.sniMatchedHost().orElse("")))
                                                       .get("/socket-id", (req, res) -> {
                                                           routeInvocations.incrementAndGet();
                                                           res.send(req.socketId());
                                                       }));
    }

    private static Http3Client client(Http3TestSupport.TestEnvironment environment) {
        Http3ClientProtocolConfig.Builder protocolConfig = Http3ClientProtocolConfig.builder()
                .initialResponseTimeout(Duration.ofSeconds(2))
                .handshakeTimeout(Duration.ofSeconds(2));
        return Http3TestSupport.strictClientBuilder(protocolConfig)
                .baseUri(environment.baseUri())
                .tls(Tls.builder()
                             .trust(trust -> trust.keystore(store -> store
                                     .passphrase("password")
                                     .trustStore(true)
                                     .keystore(Resource.create("client.p12"))))
                             .applicationProtocols(List.of(Http3Client.PROTOCOL_ID))
                             .enabledProtocols(List.of("TLSv1.3"))
                             .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                             .build())
                .build();
    }

    private static WritableHeaders<?> authority(String host) {
        return WritableHeaders.create().add(HeaderValues.create(HeaderNames.HOST, host));
    }

    private record RejectedHandshakeClient(ExecutorService executor,
                                           QuicClientRuntime client,
                                           QuicClientConnection connection) implements AutoCloseable {
        @Override
        public void close() {
            try {
                client.close();
            } finally {
                executor.close();
            }
        }

        private static RejectedHandshakeClient create(Http3TestSupport.TestEnvironment environment,
                                                      List<SNIServerName> serverNames) throws Exception {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            QuicClientRuntime client = null;
            try {
                client = QuicClientRuntime.builder()
                        .executor(executor)
                        .quicConfig(QuicConfig.builder()
                                            .availableVersions(List.of(QuicVersion.QUIC_V1))
                                            .buildPrototype())
                        .tls(environment.clientTls())
                        .build();
                String host = environment.uri("/").getHost();
                InetSocketAddress peer = new InetSocketAddress(InetAddress.getByName(host), environment.port());
                QuicClientConnection connection = serverNames.isEmpty()
                        ? client.createConnection(peer,
                                                  peer.getHostString(),
                                                  peer.getPort(),
                                                  new String[] {Http3Client.PROTOCOL_ID})
                        : client.createConnection(peer,
                                                  host,
                                                  environment.port(),
                                                  new String[] {Http3Client.PROTOCOL_ID},
                                                  List.copyOf(serverNames));
                return new RejectedHandshakeClient(executor, client, connection);
            } catch (Exception | Error t) {
                if (client != null) {
                    client.close();
                }
                executor.close();
                throw t;
            }
        }

    }
}
