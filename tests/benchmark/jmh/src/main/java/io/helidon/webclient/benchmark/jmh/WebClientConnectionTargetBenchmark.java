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

package io.helidon.webclient.benchmark.jmh;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Route;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class WebClientConnectionTargetBenchmark {
    private static final int REQUESTS_PER_INVOCATION = 16;
    private static final int TARGETS_PER_THREAD = 4;
    private static final String PATH = "/target";
    private static final String BODY = "ok";
    private static final String ALTERNATIVE_SOCKET = "alt-svc-h2";
    private static final String CLIENT_ALT_SVC_CONFIG = "io.helidon.webclient.api.ClientAltSvcConfig";
    private static final HeaderName ALT_USED = HeaderNames.create("Alt-Used");
    private static final Status WRONG_SOCKET = Status.create(590, "Wrong benchmark socket");
    private static final Status WRONG_AUTHORITY = Status.create(591, "Wrong benchmark authority");
    private static final Status WRONG_ALT_USED = Status.create(592, "Wrong benchmark Alt-Used");

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1CacheHit(Http1State state, Blackhole blackhole) {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (var response = state.http1Client.get(state.targetUri(0)).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/1.1 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1OneOff(Http1State state, Blackhole blackhole) {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (var response = state.http1Client.get(state.targetUri(0)).keepAlive(false).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/1.1 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1DistinctTargetPerThread(Http1State state, ThreadParams threadParams, Blackhole blackhole) {
        int targetIndex = threadParams.getThreadIndex() * TARGETS_PER_THREAD;
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (var response = state.http1Client.get(state.targetUri(targetIndex)).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/1.1 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1RotatingTargetsPerThread(Http1State state, ThreadParams threadParams, Blackhole blackhole) {
        int firstTargetIndex = threadParams.getThreadIndex() * TARGETS_PER_THREAD;
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            int targetIndex = firstTargetIndex + i % TARGETS_PER_THREAD;
            try (var response = state.http1Client.get(state.targetUri(targetIndex)).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/1.1 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2CacheHit(Http2State state, Blackhole blackhole) {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (var response = state.http2Client.get(state.targetUri(0)).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/2 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void altSvcMatched(AltSvcMatchedState state, Blackhole blackhole) {
        // One request loop keeps the base/head and scenario comparisons mechanically identical.
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            blackhole.consume(state.request());
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2DistinctTargetPerThread(Http2State state, ThreadParams threadParams, Blackhole blackhole) {
        int targetIndex = threadParams.getThreadIndex() * TARGETS_PER_THREAD;
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (var response = state.http2Client.get(state.targetUri(targetIndex)).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/2 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2RotatingTargetsPerThread(Http2State state, ThreadParams threadParams, Blackhole blackhole) {
        int firstTargetIndex = threadParams.getThreadIndex() * TARGETS_PER_THREAD;
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            int targetIndex = firstTargetIndex + i % TARGETS_PER_THREAD;
            try (var response = state.http2Client.get(state.targetUri(targetIndex)).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/2 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    private static String[] targetUris(int targetCount, int serverPort) {
        String[] result = new String[targetCount];
        for (int i = 0; i < targetCount; i++) {
            result[i] = "http://target-" + i + ".example:" + serverPort + PATH;
        }
        return result;
    }

    private static int targetCount(int prefilledTargetCount, BenchmarkParams benchmarkParams) {
        return Math.max(prefilledTargetCount, benchmarkParams.getThreads() * TARGETS_PER_THREAD);
    }

    private static Tls clientTls() {
        return Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
    }

    private static Tls serverTls() {
        Keys keys = Keys.builder()
                .keystore(store -> store
                        .keystore(Resource.create("server.p12"))
                        .passphrase("password"))
                .build();
        return Tls.builder()
                .privateKey(keys)
                .privateKeyCertChain(keys)
                .build();
    }

    private static InetAddress resolve(String host, ProxyMode proxyMode) {
        boolean proxyHost = "proxy.invalid".equals(host);
        if (proxyMode == ProxyMode.HTTP_FORWARD && !proxyHost) {
            throw new IllegalStateException("Origin host must not be resolved through an HTTP forward proxy: " + host);
        }
        if (proxyMode != ProxyMode.HTTP_FORWARD && proxyHost) {
            throw new IllegalStateException("Proxy host must not be resolved: " + host);
        }
        return InetAddress.ofLiteral("127.0.0.1");
    }

    public enum ProxyMode {
        NONE,
        IP_NO_PROXY,
        HTTP_FORWARD;

        Proxy proxy(int serverPort) {
            return switch (this) {
                case NONE -> Proxy.noProxy();
                case IP_NO_PROXY -> Proxy.builder()
                        .type(Proxy.ProxyType.HTTP)
                        .host("proxy.invalid")
                        .port(8080)
                        .addNoProxy(".example")
                        .addNoProxy("127.0.0.1")
                        .build();
                case HTTP_FORWARD -> Proxy.builder()
                        .type(Proxy.ProxyType.HTTP)
                        .host("proxy.invalid")
                        .port(serverPort)
                        .build();
            };
        }
    }

    public enum ExpectedImplementation {
        BASE,
        HEAD
    }

    public enum AltSvcScenario {
        TLS_H1,
        TLS_H2,
        DIRECT_H2_ALTERNATIVE,
        ENABLED_NO_ENTRY,
        ACTIVE,
        DIRECT_H2_ALTERNATIVE_REPEATED_AD,
        ACTIVE_REPEATED_AD,
        EXPIRED_ESTABLISHED_REUSE,
        DISABLED_CAPTURE;

        private boolean headOnly() {
            return this == ACTIVE || this == ACTIVE_REPEATED_AD || this == EXPIRED_ESTABLISHED_REUSE;
        }

        private boolean configuresAltSvc() {
            return this == ENABLED_NO_ENTRY
                    || this == ACTIVE
                    || this == ACTIVE_REPEATED_AD
                    || this == EXPIRED_ESTABLISHED_REUSE
                    || this == DISABLED_CAPTURE;
        }

        private boolean altSvcEnabled() {
            return this != DISABLED_CAPTURE;
        }

        private boolean syntheticAlternative() {
            return this == DIRECT_H2_ALTERNATIVE || this == DIRECT_H2_ALTERNATIVE_REPEATED_AD;
        }
    }

    private enum SocketKind {
        ORIGIN,
        ALTERNATIVE
    }

    private record ExpectedRequest(SocketKind socket, String authority, String altUsed) {
    }

    @State(Scope.Benchmark)
    public static class ServerState {
        private WebServer server;

        @Setup(Level.Trial)
        public void setup() {
            Http2Config http2Config = Http2Config.create();
            server = WebServer.builder()
                    .addProtocol(http2Config)
                    .addConnectionSelector(Http2ConnectionSelector.builder()
                                                   .http2Config(http2Config)
                                                   .build())
                    .host("127.0.0.1")
                    .routing(builder -> builder
                            .route(Http1Route.route(Method.GET, PATH,
                                                    (_, response) -> response.send("ok")))
                            .route(Http2Route.route(Method.GET, PATH,
                                                    (_, response) -> response.send("ok"))))
                    .build()
                    .start();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            server.stop();
        }
    }

    @State(Scope.Benchmark)
    public static class Http1State {
        @Param({"1", "64"})
        public int prefilledTargetCount;

        @Param({"NONE", "IP_NO_PROXY"})
        public ProxyMode proxyMode;

        private Http1Client http1Client;
        private String[] targetUris;

        @Setup(Level.Trial)
        public void setup(ServerState serverState, BenchmarkParams benchmarkParams) {
            http1Client = Http1Client.builder()
                    .shareConnectionCache(false)
                    .servicesDiscoverServices(false)
                    .proxy(proxyMode.proxy(serverState.server.port()))
                    .dnsResolver((host, _) -> resolve(host, proxyMode))
                    .build();
            targetUris = targetUris(targetCount(prefilledTargetCount, benchmarkParams), serverState.server.port());
            for (int i = 0; i < prefilledTargetCount; i++) {
                try (var response = http1Client.get(targetUris[i]).request()) {
                    response.entity().consume();
                    if (response.status().code() != 200) {
                        throw new IllegalStateException("Unexpected HTTP/1.1 status: " + response.status());
                    }
                }
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            http1Client.closeResource();
        }

        private String targetUri(int index) {
            return targetUris[index];
        }
    }

    @State(Scope.Benchmark)
    public static class Http2State {
        @Param({"1", "64"})
        public int prefilledTargetCount;

        @Param({"NONE", "IP_NO_PROXY"})
        public ProxyMode proxyMode;

        private Http2Client http2Client;
        private String[] targetUris;

        @Setup(Level.Trial)
        public void setup(ServerState serverState, BenchmarkParams benchmarkParams) {
            http2Client = Http2Client.builder()
                    .shareConnectionCache(false)
                    .servicesDiscoverServices(false)
                    .proxy(proxyMode.proxy(serverState.server.port()))
                    .dnsResolver((host, _) -> resolve(host, proxyMode))
                    .protocolConfig(builder -> builder.priorKnowledge(proxyMode != ProxyMode.HTTP_FORWARD))
                    .build();
            targetUris = targetUris(targetCount(prefilledTargetCount, benchmarkParams), serverState.server.port());
            for (int i = 0; i < prefilledTargetCount; i++) {
                try (var response = http2Client.get(targetUris[i]).request()) {
                    response.entity().consume();
                    if (response.status().code() != 200) {
                        throw new IllegalStateException("Unexpected HTTP/2 status: " + response.status());
                    }
                    if (i == 0
                            && proxyMode == ProxyMode.HTTP_FORWARD
                            && !Http1Client.PROTOCOL_ID.equals(response.protocolId())) {
                        throw new IllegalStateException(
                                "Unexpected HTTP forward proxy protocol: " + response.protocolId());
                    }
                }
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            http2Client.closeResource();
        }

        private String targetUri(int index) {
            return targetUris[index];
        }
    }

    @State(Scope.Benchmark)
    public static class AltSvcMatchedState {
        @Param({"BASE", "HEAD"})
        public ExpectedImplementation expectedImplementation;

        @Param({"TLS_H1", "TLS_H2"})
        public AltSvcScenario scenario;

        private WebServer server;
        private WebClient webClient;
        private String originUri;
        private String alternativeUri;
        private String requestUri;
        private String originAuthority;
        private String alternativeAuthority;
        private String altSvcValue;
        private String expiredAltSvcValue;
        private String expectedProtocol;
        private volatile ExpectedRequest expectedRequest;
        private volatile String responseAltSvc;

        @Setup(Level.Trial)
        public void setup() {
            Class<?> altSvcConfigClass = expectedAltSvcConfigClass();
            if (scenario.headOnly() && expectedImplementation != ExpectedImplementation.HEAD) {
                throw new IllegalStateException("Scenario " + scenario + " requires the HEAD implementation");
            }

            Tls serverTls = serverTls();
            server = WebServer.builder()
                    .host("localhost")
                    .port(-1)
                    .tls(serverTls)
                    .protocolsDiscoverServices(false)
                    .addConnectionSelector(Http1ConnectionSelector.builder()
                                                   .config(Http1Config.create())
                                                   .build())
                    .putSocket(ALTERNATIVE_SOCKET, socket -> socket
                            .host("localhost")
                            .port(-1)
                            .tls(serverTls)
                            .protocolsDiscoverServices(false)
                            .addConnectionSelector(Http2ConnectionSelector.builder()
                                                           .http2Config(Http2Config.create())
                                                           .build()))
                    .routing(HttpRouting.builder().get(PATH, this::originResponse))
                    .routing(ALTERNATIVE_SOCKET,
                             HttpRouting.builder().get(PATH, this::alternativeResponse))
                    .build()
                    .start();

            originAuthority = "localhost:" + server.port();
            alternativeAuthority = "localhost:" + server.port(ALTERNATIVE_SOCKET);
            originUri = "https://" + originAuthority + PATH;
            alternativeUri = "https://" + alternativeAuthority + PATH;
            altSvcValue = "h2=\":%d\"; ma=3600".formatted(server.port(ALTERNATIVE_SOCKET));
            expiredAltSvcValue = "h2=\":%d\"; ma=0".formatted(server.port(ALTERNATIVE_SOCKET));

            var builder = WebClient.builder()
                    .shareConnectionCache(false)
                    .servicesDiscoverServices(false)
                    .protocolPreference(List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID))
                    .proxy(Proxy.noProxy())
                    .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                    .tls(clientTls());
            if (scenario.syntheticAlternative()) {
                // Synthetic transport control, not an Alt-Svc implementation path: its setup defaults reproduce
                // the logical origin authority and Alt-Used value while the URI connects directly to the H2 socket.
                builder.addHeader(HeaderNames.HOST, originAuthority)
                        .addHeader("Alt-Used", alternativeAuthority);
            }
            if (scenario.configuresAltSvc()) {
                configureAltSvc(builder, altSvcConfigClass, scenario.altSvcEnabled());
            }
            webClient = builder.build();

            switch (scenario) {
                case TLS_H1, ENABLED_NO_ENTRY -> primeOrigin(false);
                case TLS_H2 -> primeAlternative(false, false);
                case DIRECT_H2_ALTERNATIVE -> primeAlternative(true, false);
                case ACTIVE -> {
                    primeOrigin(true);
                    primeActiveAlternative(false);
                }
                case DIRECT_H2_ALTERNATIVE_REPEATED_AD -> primeAlternative(true, true);
                case ACTIVE_REPEATED_AD -> {
                    primeOrigin(true);
                    primeActiveAlternative(true);
                }
                case EXPIRED_ESTABLISHED_REUSE -> {
                    primeOrigin(true);
                    primeActiveAlternative(false);
                    primeActiveAlternative(expiredAltSvcValue);
                    // The ma=0 response is setup-only; measured responses do not mutate Alt-Svc state.
                    responseAltSvc = null;
                }
                case DISABLED_CAPTURE -> {
                    primeAlternative(false, true);
                    primeAlternative(false, true);
                }
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                if (webClient != null) {
                    // Re-check the final protocol, physical socket, authority, and Alt-Used expectation.
                    request();
                }
            } finally {
                if (webClient != null) {
                    webClient.closeResource();
                }
                if (server != null) {
                    server.stop();
                }
            }
        }

        private static void configureAltSvc(Object webClientBuilder,
                                            Class<?> altSvcConfigClass,
                                            boolean enabled) {
            if (altSvcConfigClass == null) {
                return;
            }
            try {
                Object altSvcBuilder = altSvcConfigClass.getMethod("builder").invoke(null);
                altSvcBuilder.getClass().getMethod("enabled", boolean.class).invoke(altSvcBuilder, enabled);
                altSvcBuilder.getClass()
                        .getMethod("addProtocol", String.class)
                        .invoke(altSvcBuilder, Http2Client.PROTOCOL_ID);
                Object altSvcConfig = altSvcBuilder.getClass().getMethod("build").invoke(altSvcBuilder);
                webClientBuilder.getClass()
                        .getMethod("altSvc", altSvcConfigClass)
                        .invoke(webClientBuilder, altSvcConfig);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot configure the HEAD Alt-Svc API", e);
            }
        }

        private Class<?> expectedAltSvcConfigClass() {
            try {
                Class<?> configClass = Class.forName(CLIENT_ALT_SVC_CONFIG);
                if (expectedImplementation == ExpectedImplementation.BASE) {
                    throw new IllegalStateException("BASE benchmark classpath contains " + CLIENT_ALT_SVC_CONFIG);
                }
                return configClass;
            } catch (ClassNotFoundException e) {
                if (expectedImplementation == ExpectedImplementation.HEAD) {
                    throw new IllegalStateException("HEAD benchmark classpath does not contain "
                                                            + CLIENT_ALT_SVC_CONFIG,
                                                    e);
                }
                return null;
            }
        }

        private void primeOrigin(boolean advertiseAltSvc) {
            prime(originUri,
                  new ExpectedRequest(SocketKind.ORIGIN, originAuthority, ""),
                  Http1Client.PROTOCOL_ID,
                  advertiseAltSvc ? altSvcValue : null);
        }

        private void primeAlternative(boolean synthetic, boolean advertiseAltSvc) {
            String expectedAuthority = synthetic ? originAuthority : alternativeAuthority;
            String expectedAltUsed = synthetic ? alternativeAuthority : "";
            prime(alternativeUri,
                  new ExpectedRequest(SocketKind.ALTERNATIVE, expectedAuthority, expectedAltUsed),
                  Http2Client.PROTOCOL_ID,
                  advertiseAltSvc ? altSvcValue : null);
        }

        private void primeActiveAlternative(boolean advertiseAltSvc) {
            primeActiveAlternative(advertiseAltSvc ? altSvcValue : null);
        }

        private void primeActiveAlternative(String advertisedAltSvc) {
            prime(originUri,
                  new ExpectedRequest(SocketKind.ALTERNATIVE, originAuthority, alternativeAuthority),
                  Http2Client.PROTOCOL_ID,
                  advertisedAltSvc);
        }

        private void prime(String uri,
                           ExpectedRequest requestExpectation,
                           String protocol,
                           String advertisedAltSvc) {
            requestUri = uri;
            expectedProtocol = protocol;
            responseAltSvc = advertisedAltSvc;
            expectedRequest = requestExpectation;
            request();
        }

        private int request() {
            var request = webClient.get(requestUri);
            try (var response = request.request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != Status.OK_200_CODE) {
                    throw new IllegalStateException("Unexpected " + scenario + " status: " + response.status());
                }
                if (!expectedProtocol.equals(response.protocolId())) {
                    throw new IllegalStateException("Unexpected " + scenario + " protocol: " + response.protocolId());
                }
                return status;
            }
        }

        private void originResponse(ServerRequest request, ServerResponse response) {
            respond(request, response, SocketKind.ORIGIN);
        }

        private void alternativeResponse(ServerRequest request, ServerResponse response) {
            respond(request, response, SocketKind.ALTERNATIVE);
        }

        private void respond(ServerRequest request, ServerResponse response, SocketKind actualSocket) {
            ExpectedRequest expectation = expectedRequest;
            if (expectation == null || expectation.socket() != actualSocket) {
                response.status(WRONG_SOCKET).send(BODY);
                return;
            }
            if (!expectation.authority().equals(request.requestedUri().authority())) {
                response.status(WRONG_AUTHORITY).send(BODY);
                return;
            }
            if (!expectation.altUsed().equals(request.headers().first(ALT_USED).orElse(""))) {
                response.status(WRONG_ALT_USED).send(BODY);
                return;
            }
            String advertisedAltSvc = responseAltSvc;
            if (advertisedAltSvc != null) {
                // Repeated-ad rows leave this enabled; the expiration row clears its setup-only ma=0 response.
                response.header(HeaderNames.ALT_SVC, advertisedAltSvc);
            }
            response.send(BODY);
        }
    }
}
