/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.provider.header;

import java.util.List;
import java.util.Optional;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link HeaderAtnProvider}.
 */
public abstract class HeaderAtnProviderTest {
    abstract HeaderAtnProvider getFullProvider();

    abstract HeaderAtnProvider getServiceProvider();

    abstract HeaderAtnProvider getNoSecurityProvider();

    @Test
    public void testExtraction() {
        String username = "username";

        HeaderAtnProvider provider = getFullProvider();
        SecurityEnvironment env = SecurityEnvironment.builder()
                .header("Authorization", "bearer " + username)
                .build();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getEnv()).thenReturn(env);

        AuthenticationResponse response = provider.syncAuthenticate(request);

        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.getUser(), is(not(Optional.empty())));
        assertThat(response.getService(), is(Optional.empty()));

        response.getUser()
                .map(Subject::getPrincipal)
                .map(Principal::getName)
                .ifPresent(name -> assertThat(name, is(username)));
    }

    @Test
    public void testExtractionNoHeader() {
        HeaderAtnProvider provider = getFullProvider();
        SecurityEnvironment env = SecurityEnvironment.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getEnv()).thenReturn(env);

        AuthenticationResponse response = provider.syncAuthenticate(request);

        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.ABSTAIN));
        assertThat(response.getUser(), is(Optional.empty()));
        assertThat(response.getService(), is(Optional.empty()));
    }

    @Test
    public void testOutbound() {
        HeaderAtnProvider provider = getFullProvider();

        SecurityEnvironment env = SecurityEnvironment.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getEnv()).thenReturn(env);

        SecurityContext sc = mock(SecurityContext.class);
        when(sc.getUser()).thenReturn(Optional.of(Subject.builder().addPrincipal(Principal.create("username")).build()));
        when(sc.getService()).thenReturn(Optional.empty());
        when(request.getContext()).thenReturn(sc);

        SecurityEnvironment outboundEnv = SecurityEnvironment.create();
        EndpointConfig outboundEp = EndpointConfig.create();

        assertThat("Outbound should be supported", provider.isOutboundSupported(request, outboundEnv, outboundEp),
                   is(true));

        OutboundSecurityResponse response = provider.syncOutbound(request, outboundEnv, outboundEp);

        List<String> custom = response.getRequestHeaders().get("Custom");
        assertThat(custom, notNullValue());
        assertThat(custom.size(), is(1));

        String token = custom.get(0);
        assertThat(token, is("bearer username"));
    }

    @Test
    public void testServiceExtraction() {
        HeaderAtnProvider provider = getServiceProvider();

        String username = "service";

        SecurityEnvironment env = SecurityEnvironment.builder()
                .header("Authorization", "bearer " + username)
                .build();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getEnv()).thenReturn(env);

        AuthenticationResponse response = provider.syncAuthenticate(request);

        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.getUser(), is(Optional.empty()));
        assertThat(response.getService(), is(not(Optional.empty())));

        response.getService()
                .map(Subject::getPrincipal)
                .map(Principal::getName)
                .ifPresent(name -> assertThat(name, is(username)));
    }

    @Test
    public void testServiceNoHeaderExtraction() {
        HeaderAtnProvider provider = getServiceProvider();

        SecurityEnvironment env = SecurityEnvironment.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getEnv()).thenReturn(env);

        AuthenticationResponse response = provider.syncAuthenticate(request);

        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.getService(), is(Optional.empty()));
        assertThat(response.getUser(), is(Optional.empty()));
    }

    @Test
    public void testServiceOutbound() {
        HeaderAtnProvider provider = getServiceProvider();

        String username = "service";

        SecurityEnvironment env = SecurityEnvironment.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getEnv()).thenReturn(env);

        SecurityContext sc = mock(SecurityContext.class);
        when(sc.getService()).thenReturn(Optional.of(Subject.builder().addPrincipal(Principal.create(username)).build()));
        when(sc.getUser()).thenReturn(Optional.empty());
        when(request.getContext()).thenReturn(sc);

        SecurityEnvironment outboundEnv = SecurityEnvironment.create();
        EndpointConfig outboundEp = EndpointConfig.create();

        assertThat("Outbound should be supported", provider.isOutboundSupported(request, outboundEnv, outboundEp), is(true));
        OutboundSecurityResponse response = provider.syncOutbound(request, outboundEnv, outboundEp);

        List<String> custom = response.getRequestHeaders().get("Authorization");
        assertThat(custom, notNullValue());
        assertThat(custom.size(), is(1));

        String token = custom.get(0);
        assertThat(token, is("bearer " + username));
    }

    @Test
    public void testNoAtn() {
        String username = "username";

        HeaderAtnProvider provider = getNoSecurityProvider();

        SecurityEnvironment env = SecurityEnvironment.builder()
                .header("Authorization", "bearer " + username)
                .build();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getEnv()).thenReturn(env);

        AuthenticationResponse response = provider.syncAuthenticate(request);

        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.ABSTAIN));
        assertThat(response.getUser(), is(Optional.empty()));
        assertThat(response.getService(), is(Optional.empty()));
    }

    @Test
    public void testNoOutbound() {
        String username = "username";

        HeaderAtnProvider provider = getNoSecurityProvider();

        SecurityEnvironment env = SecurityEnvironment.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getEnv()).thenReturn(env);

        SecurityContext sc = mock(SecurityContext.class);
        when(sc.getUser()).thenReturn(Optional.of(Subject.builder().addPrincipal(Principal.create(username)).build()));
        when(sc.getService()).thenReturn(Optional.empty());
        when(request.getContext()).thenReturn(sc);

        SecurityEnvironment outboundEnv = SecurityEnvironment.create();
        EndpointConfig outboundEp = EndpointConfig.create();

        assertThat("Outbound should not be supported", provider.isOutboundSupported(request, outboundEnv, outboundEp), is(false));
    }
}
