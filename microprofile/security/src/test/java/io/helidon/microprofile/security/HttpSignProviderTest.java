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

package io.helidon.microprofile.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.Configuration;
import io.helidon.microprofile.testing.Socket;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.security.annotations.Authenticated;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(HttpSignProviderTest.SignedResource.class)
@Configuration(configSources = "http-sign.yaml")
class HttpSignProviderTest {
    private static final String KEY_ID = "mp-test-key";
    private static final String HMAC_SECRET = "MpTestSecret";
    private static final String REQUEST_TARGET = "/raw%2Fresource?";

    private final URI serverUri;

    @Inject
    HttpSignProviderTest(@Socket("@default") URI serverUri) {
        this.serverUri = serverUri;
    }

    @Test
    void testSignatureUsesRawRequestTarget() throws Exception {
        String accepted = request(REQUEST_TARGET, REQUEST_TARGET);
        assertThat(SocketHttpClient.statusFromResponse(accepted), is(Status.OK_200));
        assertThat(SocketHttpClient.entityFromResponse(accepted, true), is("signed"));

        String rejected = request(REQUEST_TARGET, "/raw/resource");
        assertThat(SocketHttpClient.statusFromResponse(rejected), is(Status.UNAUTHORIZED_401));
    }

    private String request(String requestTarget, String signedTarget) throws Exception {
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
        String signedString = "date: " + date + "\n"
                + "(request-target): get " + signedTarget;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "keyId=\"" + KEY_ID + "\",algorithm=\"hmac-sha256\",headers=\"date (request-target)\",signature=\""
                + Base64.getEncoder().encodeToString(mac.doFinal(signedString.getBytes(StandardCharsets.UTF_8)))
                + "\"";

        try (SocketHttpClient client = SocketHttpClient.create(serverUri.getHost(),
                                                               serverUri.getPort(),
                                                               Duration.ofSeconds(5))) {
            return client.sendAndReceive(Method.GET,
                                         requestTarget,
                                         null,
                                         List.of("Date: " + date, "Signature: " + signature));
        }
    }

    @Path("/")
    public static class SignedResource {
        @GET
        @Path("{path: .*}")
        @Authenticated(provider = "http-signatures")
        @Produces(MediaType.TEXT_PLAIN)
        public String signed() {
            return "signed";
        }
    }
}
