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

package io.helidon.security.providers.idcs.mapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import io.helidon.common.http.MediaType;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SuppressWarnings("removal")
class IdcsRoleMapperTokenLoggingTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    @Timeout(30)
    void reactiveTokenAcquisitionAndRefreshDoNotLogTokens() {
        try (TokenEndpoint endpoint = new TokenEndpoint();
             LogCapture logging = new LogCapture(IdcsRoleMapperRxProviderBase.class.getName())) {
            endpoint.start();
            WebClient client = WebClient.builder()
                    .addMediaSupport(JsonpSupport.create())
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build();
            IdcsRoleMapperRxProviderBase.AppTokenRx appToken =
                    new IdcsRoleMapperRxProviderBase.AppTokenRx(client,
                                                              endpoint.uri(),
                                                              Duration.ZERO,
                                                              "client-id",
                                                              "client-secret");

            assertTokenLifecycle(() -> appToken.getToken(SecurityTracing.get().roleMapTracing("idcs")).await(TIMEOUT),
                                 endpoint,
                                 logging);
        }
    }

    @Test
    @Timeout(30)
    void blockingTokenAcquisitionAndRefreshDoNotLogTokens() {
        Client client = ClientBuilder.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        try (TokenEndpoint endpoint = new TokenEndpoint();
             LogCapture logging = new LogCapture(IdcsRoleMapperProviderBase.class.getName())) {
            endpoint.start();
            IdcsRoleMapperProviderBase.AppToken appToken =
                    new IdcsRoleMapperProviderBase.AppToken(client.target(endpoint.uri()), "client-id", "client-secret");

            assertTokenLifecycle(() -> appToken.getToken(SecurityTracing.get().roleMapTracing("idcs")),
                                 endpoint,
                                 logging);
        } finally {
            client.close();
        }
    }

    private static void assertTokenLifecycle(Supplier<Optional<String>> tokenSupplier,
                                             TokenEndpoint endpoint,
                                             LogCapture logging) {
        logging.logger.finest("Token logging test capture enabled");
        assertThat(logging.messages, hasItem(containsString("Token logging test capture enabled")));

        // An already-expired first response triggers a refresh without sleeping or inspecting private cache state.
        assertThat(tokenSupplier.get(), is(Optional.of(endpoint.expiredToken)));
        assertThat(tokenSupplier.get(), is(Optional.of(endpoint.refreshedToken)));
        assertThat(tokenSupplier.get(), is(Optional.of(endpoint.refreshedToken)));
        assertThat("The valid token is reused after refresh", endpoint.requests.get(), is(2));

        assertAll(() -> assertThat(logging.messages, everyItem(not(containsString(endpoint.expiredToken)))),
                  () -> assertThat(logging.messages, everyItem(not(containsString(endpoint.refreshedToken)))));
    }

    private static String token(Instant expiration) {
        Jwt jwt = Jwt.builder()
                .algorithm("none")
                .issuer("idcs-token-test")
                .issueTime(Instant.now().minusSeconds(7200))
                .expirationTime(expiration)
                .build();
        return SignedJwt.sign(jwt, Jwk.NONE_JWK).tokenContent();
    }

    private static final class TokenEndpoint implements AutoCloseable {
        private final String expiredToken = token(Instant.now().minusSeconds(3600));
        private final String refreshedToken = token(Instant.now().plusSeconds(3600));
        private final AtomicInteger requests = new AtomicInteger();
        private final WebServer server = WebServer.builder()
                .host("127.0.0.1")
                .routing(Routing.builder()
                                 .post("/oauth2/v1/token", (request, response) -> {
                                     String token = requests.getAndIncrement() == 0 ? expiredToken : refreshedToken;
                                     response.headers().contentType(MediaType.APPLICATION_JSON);
                                     response.send("{\"access_token\":\"" + token + "\"}");
                                 }))
                .build();

        void start() {
            server.start().await(TIMEOUT);
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + server.port() + "/oauth2/v1/token");
        }

        @Override
        public void close() {
            server.shutdown().await(TIMEOUT);
        }
    }

    private static final class LogCapture extends Handler implements AutoCloseable {
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final Logger logger;
        private final Level previousLevel;
        private final boolean previousUseParentHandlers;

        private LogCapture(String loggerName) {
            logger = Logger.getLogger(loggerName);
            previousLevel = logger.getLevel();
            previousUseParentHandlers = logger.getUseParentHandlers();
            setLevel(Level.ALL);
            setFormatter(new SimpleFormatter());
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            messages.add(getFormatter().format(record));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
    }
}
