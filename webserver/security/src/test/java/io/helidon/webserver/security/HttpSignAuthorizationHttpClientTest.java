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

package io.helidon.webserver.security;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.httpsign.HttpSignHeader;
import io.helidon.security.providers.httpsign.HttpSignProvider;
import io.helidon.security.providers.httpsign.InboundClientDefinition;
import io.helidon.security.providers.httpsign.OutboundTargetDefinition;
import io.helidon.security.providers.httpsign.SignedHeadersConfig;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.security.providers.httpsign.SignedHeadersConfig.REQUEST_TARGET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ServerTest
class HttpSignAuthorizationHttpClientTest {
    private static final String DATE = "Sun, 08 Jun 2014 18:32:30 GMT";
    private static final String KEY_ID = "myServiceKeyId";
    private static final String HMAC_SECRET = "MyPasswordForHmac";

    private final Http1Client client;
    private final Http1Client signedClient;

    HttpSignAuthorizationHttpClientTest(Http1Client client, WebServer server) {
        this.client = client;
        this.signedClient = Http1Client.builder()
                .baseUri("http://localhost:" + server.port())
                .addService(WebClientSecurity.create(Security.builder().addProvider(httpSignProvider()).build()))
                .build();
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        server.featuresDiscoverServices(false)
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(Security.builder().addProvider(httpSignProvider()).build())
                                    .defaults(SecurityFeature.authenticate())
                                    .build())
                .routing(r -> r.get("/secured", SecurityFeature.authenticate(), (req, res) -> res.send("ok")));
    }

    @Test
    void rejectsCommaDelimitedAuthorizationValueWithQuotedParameter() {
        String authorization = signedAuthorizationHeader() + ", Digest username=\"u\"";

        try (Http1ClientResponse response = client.get("/secured")
                .header(HeaderNames.DATE, DATE)
                .header(HeaderNames.AUTHORIZATION, authorization)
                .request()) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
        }
    }

    @Test
    void webClientSignaturePreservesEmptyQueryDelimiter() {
        try (Http1ClientResponse response = signedClient.get()
                .uri(URI.create("/secured?"))
                .header(HeaderNames.DATE, DATE)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    private static String signedAuthorizationHeader() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("date", List.of(DATE));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(mock(SecurityContext.class));

        OutboundSecurityResponse response = httpSignProvider().outboundSecurity(
                request,
                SecurityEnvironment.builder()
                        .method("GET")
                        .path("/secured")
                        .targetUri(URI.create("http://localhost/secured"))
                        .headers(headers)
                        .build(),
                EndpointConfig.create());

        assertThat(response.status().isSuccess(), is(true));
        return response.requestHeaders().get("Authorization").getFirst();
    }

    private static HttpSignProvider httpSignProvider() {
        SignedHeadersConfig signedHeaders = SignedHeadersConfig.builder()
                .defaultConfig(SignedHeadersConfig.HeadersConfig.create(List.of("date", REQUEST_TARGET)))
                .build();

        return HttpSignProvider.builder()
                .optional(false)
                .addAcceptHeader(HttpSignHeader.AUTHORIZATION)
                .inboundDateValidity(Duration.ZERO)
                .inboundRequiredHeaders(signedHeaders)
                .addInbound(InboundClientDefinition.builder(KEY_ID)
                                    .principalName("aSetOfTrustedServices")
                                    .hmacSecret(HMAC_SECRET)
                                    .build())
                .outbound(OutboundConfig.builder()
                                  .addTarget(OutboundTarget.builder("default")
                                                     .customObject(OutboundTargetDefinition.class,
                                                                   OutboundTargetDefinition.builder(KEY_ID)
                                                                           .header(HttpSignHeader.AUTHORIZATION)
                                                                           .hmacSecret(HMAC_SECRET)
                                                                           .signedHeaders(signedHeaders)
                                                                           .build())
                                                     .build())
                                  .build())
                .build();
    }
}
