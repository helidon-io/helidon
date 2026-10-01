/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.testing.junit5.InMemoryLoggingHandler;
import io.helidon.common.testing.junit5.LogRecordMatcher;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
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
    void testSecureClientOverride() {
        String user = "jack";
        String password = "password";
        String base64Encoded = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));

        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder()
                                                  .userStore(login -> Optional.empty())
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
        when(requestContext.getMethod()).thenReturn("GET");
        when(requestContext.getStringHeaders()).thenReturn(new MultivaluedHashMap<>());
        when(requestContext.getHeaders()).thenReturn(jerseyHeaders);
        when(requestContext.getPropertyNames()).thenReturn(List.of(
                ClientSecurity.PROPERTY_CONTEXT,
                ClientSecurity.PROPERTY_PROVIDER,
                EndpointConfig.PROPERTY_OUTBOUND_ID,
                EndpointConfig.PROPERTY_OUTBOUND_SECRET
        ));

        Logger logger = Logger.getLogger(ClientSecurityFilter.class.getName());
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();

        try (InMemoryLoggingHandler handler = InMemoryLoggingHandler.create(logger)) {
            handler.setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);

            ClientSecurityFilter csf = new ClientSecurityFilter();
            csf.filter(requestContext);

            assertThat(jerseyHeaders.getFirst("Authorization"), notNullValue());
            String authorizationHeader = jerseyHeaders.getFirst("Authorization").toString();
            assertThat(authorizationHeader.toLowerCase(), startsWith("basic "));
            assertThat(authorizationHeader, endsWith(base64Encoded));
            assertThat(handler.logRecords(),
                       hasItem(LogRecordMatcher.withMessage(is("Client filter header(s). SIZE: 1"))));
            assertThat(handler.logRecords(),
                       hasItem(LogRecordMatcher.withMessage(is("    + Header: Authorization: <redacted>"))));
            assertThat(handler.logRecords(),
                       not(hasItem(LogRecordMatcher.withMessage(containsString(authorizationHeader)))));
            assertThat(handler.logRecords(),
                       not(hasItem(LogRecordMatcher.withMessage(containsString(base64Encoded)))));
        } finally {
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }

    }
}
