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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.json.JsonObject;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.JwtHeaders;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class IdcsAccessTokenLogTest {
    private static final String TOKEN_PATH = "/oauth2/v1/token";
    private static final String ACCESS_TOKEN = signedToken();
    private static final String INVALID_ACCESS_TOKEN_HEADER = "secret=idcsHeader";
    private static final String INVALID_ACCESS_TOKEN_PAYLOAD = "secret=idcsPayload";
    private static final String INVALID_ACCESS_TOKEN_SIGNATURE = "secret=idcsSignature";
    private static final String INVALID_ACCESS_TOKEN = INVALID_ACCESS_TOKEN_HEADER
            + "." + INVALID_ACCESS_TOKEN_PAYLOAD
            + "." + INVALID_ACCESS_TOKEN_SIGNATURE;
    private static final String INVALID_HEADER_CLAIM_SECRET = "secret-idcs-header-claim";
    private static final String INVALID_HEADER_CLAIM_ACCESS_TOKEN =
            base64Url("{\"alg\":{\"secret\":\"" + INVALID_HEADER_CLAIM_SECRET + "\"}}") + ".payload.signature";
    private static final String INVALID_PAYLOAD_CLAIM_SECRET = "secret+idcs+at+hash";
    private static final String INVALID_PAYLOAD_CLAIM_ACCESS_TOKEN =
            base64Url(JwtHeaders.builder().algorithm("none").build().headerJsonObject().toString())
                    + "." + base64Url("{\"at_hash\":\"" + INVALID_PAYLOAD_CLAIM_SECRET + "\"}") + ".";
    private static final String INVALID_ZONE_INFO_SECRET = "secret-idcs-zone";
    private static final String INVALID_ZONE_INFO_ACCESS_TOKEN =
            base64Url(JwtHeaders.builder().algorithm("none").build().headerJsonObject().toString())
                    + "." + base64Url("{\"zoneinfo\":\"" + INVALID_ZONE_INFO_SECRET + "\"}") + ".";

    private final WebClient webClient;

    IdcsAccessTokenLogTest(WebClient webClient) {
        this.webClient = webClient;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.route(Method.POST, TOKEN_PATH, IdcsAccessTokenLogTest::tokenResponse);
    }

    @Test
    void accessTokenIsNotLoggedAtTrace() {
        Logger logger = Logger.getLogger(IdcsRoleMapperProviderBase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        Level originalLevel = logger.getLevel();
        boolean originalUseParentHandlers = logger.getUseParentHandlers();

        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);

        try {
            IdcsRoleMapperProviderBase.AppToken appToken =
                    new IdcsRoleMapperProviderBase.AppToken(webClient, URI.create(TOKEN_PATH), Duration.ZERO);

            Optional<String> token = appToken.getToken(SecurityTracing.get().roleMapTracing("idcs"));

            assertThat(token.orElseThrow(), is(ACCESS_TOKEN));

            var messages = handler.messages();
            assertThat(messages, hasItem("IDCS application access token obtained; received token had "
                                                 + ACCESS_TOKEN.length() + " characters"));
            assertThat(String.join(System.lineSeparator(), messages), not(containsString(ACCESS_TOKEN)));
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(originalUseParentHandlers);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void malformedAccessTokenIsNotLoggedOnWarning() {
        assertInvalidAccessTokenIsNotLogged("?malformed=true", INVALID_ACCESS_TOKEN);
    }

    @Test
    void invalidHeaderClaimAccessTokenIsNotLoggedOnWarning() {
        assertInvalidAccessTokenIsNotLogged("?invalidHeaderClaim=true",
                                            INVALID_HEADER_CLAIM_ACCESS_TOKEN,
                                            INVALID_HEADER_CLAIM_SECRET);
    }

    @Test
    void invalidPayloadClaimAccessTokenIsNotLoggedOnWarning() {
        assertInvalidAccessTokenIsNotLogged("?invalidPayloadClaim=true",
                                            INVALID_PAYLOAD_CLAIM_ACCESS_TOKEN,
                                            INVALID_PAYLOAD_CLAIM_SECRET);
    }

    @Test
    void invalidMaterializedClaimAccessTokenIsNotLoggedOnWarning() {
        assertInvalidAccessTokenIsNotLogged("?invalidZoneInfo=true",
                                            INVALID_ZONE_INFO_ACCESS_TOKEN,
                                            INVALID_ZONE_INFO_SECRET);
    }

    private void assertInvalidAccessTokenIsNotLogged(String queryString, String invalidAccessToken, String... secrets) {
        Logger logger = Logger.getLogger(IdcsRoleMapperProviderBase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        Level originalLevel = logger.getLevel();
        boolean originalUseParentHandlers = logger.getUseParentHandlers();

        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);

        try {
            IdcsRoleMapperProviderBase.AppToken appToken =
                    new IdcsRoleMapperProviderBase.AppToken(webClient,
                                                            URI.create(TOKEN_PATH + queryString),
                                                            Duration.ZERO);

            Optional<String> token = appToken.getToken(SecurityTracing.get().roleMapTracing("idcs"));

            assertThat(token.isEmpty(), is(true));

            var messages = handler.messages();
            assertThat(messages, hasItem("IDCS application access token obtained; received token had "
                                                 + invalidAccessToken.length() + " characters"));
            assertThat(messages, hasItem("Failed to obtain access token for application to read "
                                                 + "groups from IDCS. Access token is not a valid JWT; "
                                                 + "received token had " + invalidAccessToken.length() + " characters"));
            assertThat(handler.thrown().isEmpty(), is(false));
            assertTokenIsNotLogged(messages, invalidAccessToken);
            assertValuesAreNotLogged(messages, secrets);
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(originalUseParentHandlers);
            logger.setLevel(originalLevel);
        }
    }

    private static void tokenResponse(ServerRequest request, ServerResponse response) {
        String accessToken = ACCESS_TOKEN;
        if (request.query().first("malformed").isPresent()) {
            accessToken = INVALID_ACCESS_TOKEN;
        } else if (request.query().first("invalidHeaderClaim").isPresent()) {
            accessToken = INVALID_HEADER_CLAIM_ACCESS_TOKEN;
        } else if (request.query().first("invalidPayloadClaim").isPresent()) {
            accessToken = INVALID_PAYLOAD_CLAIM_ACCESS_TOKEN;
        } else if (request.query().first("invalidZoneInfo").isPresent()) {
            accessToken = INVALID_ZONE_INFO_ACCESS_TOKEN;
        }

        response.header(HeaderValues.CONTENT_TYPE_JSON)
                .send(JsonObject.builder()
                              .set("access_token", accessToken)
                              .build()
                              .toString());
    }

    private static String signedToken() {
        Instant issueTime = Instant.now();
        Jwt jwt = Jwt.builder()
                .algorithm("none")
                .issuer("unit-test")
                .subject("idcs-access-token-log")
                .issueTime(issueTime)
                .expirationTime(issueTime.plusSeconds(3600))
                .build();

        return SignedJwt.sign(jwt, Jwk.NONE_JWK).tokenContent();
    }

    private static void assertTokenIsNotLogged(Iterable<String> messages, String token) {
        String joinedMessages = String.join(System.lineSeparator(), messages);
        assertThat(joinedMessages, not(containsString(token)));
        for (String segment : token.split("\\.")) {
            assertThat(joinedMessages, not(containsString(segment)));
        }
    }

    private static void assertValuesAreNotLogged(Iterable<String> messages, String... values) {
        String joinedMessages = String.join(System.lineSeparator(), messages);
        for (String value : values) {
            assertThat(joinedMessages, not(containsString(value)));
        }
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class CapturingHandler extends Handler {
        private final CopyOnWriteArrayList<String> messages = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<Throwable> thrown = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
            Throwable thrown = record.getThrown();
            if (thrown != null) {
                this.thrown.add(thrown);
            }
            while (thrown != null) {
                messages.add(thrown.toString());
                thrown = thrown.getCause();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            messages.clear();
            thrown.clear();
        }

        private CopyOnWriteArrayList<String> messages() {
            return messages;
        }

        private CopyOnWriteArrayList<Throwable> thrown() {
            return thrown;
        }
    }
}
