/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
package io.helidon.security.providers.httpauth;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.integration.jersey.client.ClientSecurity;
import io.helidon.security.integration.jersey.client.ClientSecurityFilter;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for basic authentication overrides for outbound calls.
 */
public class BasicAuthOutboundOverrideTest {
    @Test
    void testSecureClientOverride() throws IOException {
        String user = "jack";
        String password = "password";
        String base64Encoded = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));

        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder()
                                     .userStore((SecureUserStore) login -> Optional.empty())
                                     .build(), "http-basic-auth")
                .build();
        SecurityContext context = security.createContext(getClass().getName() + ".testSecureClientOverride()");

        MultivaluedMap<String, Object> jerseyHeaders = new MultivaluedHashMap<>();

        ClientRequestContext requestContext = mock(ClientRequestContext.class);

        Configuration configuration = mock(Configuration.class);
        when(configuration.getPropertyNames()).thenReturn(Collections.emptyList());

        when(requestContext.getConfiguration()).thenReturn(configuration);
        when(requestContext.getProperty(ClientSecurity.PROPERTY_CONTEXT)).thenReturn(context);
        when(requestContext.getProperty(ClientSecurity.PROPERTY_PROVIDER)).thenReturn("http-basic-auth");
        when(requestContext.getProperty(EndpointConfig.PROPERTY_OUTBOUND_ID)).thenReturn(user);
        when(requestContext.getProperty(EndpointConfig.PROPERTY_OUTBOUND_SECRET)).thenReturn(password);
        when(requestContext.getUri()).thenReturn(URI.create("http://localhost:7070/test"));
        when(requestContext.getStringHeaders()).thenReturn(new MultivaluedHashMap<>());
        when(requestContext.getHeaders()).thenReturn(jerseyHeaders);
        when(requestContext.getPropertyNames()).thenReturn(List.of(
                ClientSecurity.PROPERTY_CONTEXT,
                ClientSecurity.PROPERTY_PROVIDER,
                EndpointConfig.PROPERTY_OUTBOUND_ID,
                EndpointConfig.PROPERTY_OUTBOUND_SECRET
        ));

        ClientSecurityFilter csf = new ClientSecurityFilter();
        csf.filter(requestContext);

        assertThat(jerseyHeaders.getFirst("Authorization"), notNullValue());
        assertThat(jerseyHeaders.getFirst("Authorization").toString().toLowerCase(), startsWith("basic "));
        assertThat(jerseyHeaders.getFirst("Authorization").toString(), endsWith(base64Encoded));

    }
}
