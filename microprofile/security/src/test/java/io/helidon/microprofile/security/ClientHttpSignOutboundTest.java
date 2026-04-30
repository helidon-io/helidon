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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.httpsign.HttpSignProvider;
import io.helidon.security.providers.httpsign.InboundClientDefinition;
import io.helidon.security.providers.httpsign.OutboundTargetDefinition;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientHttpSignOutboundTest {
    private static final String KEY_ID = "mp-client-key";
    private static final String HMAC_SECRET = "MpClientSecret";
    private static final String PROVIDER_NAME = "http-signatures";

    @Test
    void testSignatureUsesRawRequestTarget() {
        URI requestUri = URI.create("http://localhost/raw%2Fresource?scope=a%2Fb");
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));

        HttpSignProvider provider = HttpSignProvider.builder()
                .addInbound(InboundClientDefinition.builder(KEY_ID)
                                    .principalName("mp-client")
                                    .hmacSecret(HMAC_SECRET)
                                    .build())
                .outbound(OutboundConfig.builder()
                                  .addTarget(OutboundTarget.builder("signed")
                                                     .addTransport("http")
                                                     .addHost("localhost")
                                                     .addPath("/raw/resource")
                                                     .customObject(OutboundTargetDefinition.class,
                                                                   OutboundTargetDefinition.builder(KEY_ID)
                                                                           .hmacSecret(HMAC_SECRET)
                                                                           .build())
                                                     .build())
                                  .build())
                .build();
        Security security = Security.builder()
                .addProvider(provider, PROVIDER_NAME)
                .build();
        SecurityContext context = security.createContext(getClass().getName() + ".testSignatureUsesRawRequestTarget()");

        MultivaluedMap<String, Object> requestHeaders = new MultivaluedHashMap<>();
        requestHeaders.add("date", date);
        requestHeaders.add("host", "localhost");

        MultivaluedMap<String, String> stringHeaders = new MultivaluedHashMap<>();
        stringHeaders.add("date", date);
        stringHeaders.add("host", "localhost");

        Configuration configuration = mock(Configuration.class);
        when(configuration.getPropertyNames()).thenReturn(Collections.emptyList());

        ClientRequestContext requestContext = mock(ClientRequestContext.class);
        when(requestContext.getConfiguration()).thenReturn(configuration);
        when(requestContext.getProperty(ClientSecurity.PROPERTY_CONTEXT)).thenReturn(context);
        when(requestContext.getProperty(ClientSecurity.PROPERTY_PROVIDER)).thenReturn(PROVIDER_NAME);
        when(requestContext.getPropertyNames()).thenReturn(List.of(ClientSecurity.PROPERTY_CONTEXT,
                                                                   ClientSecurity.PROPERTY_PROVIDER));
        when(requestContext.getUri()).thenReturn(requestUri);
        when(requestContext.getMethod()).thenReturn("GET");
        when(requestContext.getStringHeaders()).thenReturn(stringHeaders);
        when(requestContext.getHeaders()).thenReturn(requestHeaders);

        new ClientSecurityFilter().filter(requestContext);

        assertThat(requestHeaders.getFirst("Signature"), notNullValue());

        Map<String, List<String>> signedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<Object>> entry : requestHeaders.entrySet()) {
            signedHeaders.put(entry.getKey(), entry.getValue().stream().map(Object::toString).toList());
        }

        ProviderRequest inboundRequest = mock(ProviderRequest.class);
        when(inboundRequest.env()).thenReturn(SecurityEnvironment.builder()
                                             .method("GET")
                                             .path("/raw/resource")
                                             .targetUri(requestUri)
                                             .headers(signedHeaders)
                                             .requestedMethod("GET")
                                             .requestedPath(UriPath.create("/raw%2Fresource"))
                                             .requestedQuery(Optional.of(UriQuery.create("scope=a%2Fb")))
                                             .build());

        AuthenticationResponse response = provider.authenticate(inboundRequest);
        assertThat(response.description().orElse(response.status().toString()),
                   response.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }
}
