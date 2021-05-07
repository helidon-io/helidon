/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.examples.spi;

import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link AtzProviderSync}.
 */
public class AtzProviderSyncTest {
    @Test
    public void testPublic() {
        SecurityEnvironment se = SecurityEnvironment.builder()
                .path("/public/some/path")
                .build();
        EndpointConfig ep = EndpointConfig.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(se);
        when(request.endpointConfig()).thenReturn(ep);

        AtzProviderSync provider = new AtzProviderSync();

        AuthorizationResponse response = provider.syncAuthorize(request);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    public void testAbstain() {
        SecurityEnvironment se = SecurityEnvironment.create();
        EndpointConfig ep = EndpointConfig.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(se);
        when(request.endpointConfig()).thenReturn(ep);

        AtzProviderSync provider = new AtzProviderSync();

        AuthorizationResponse response = provider.syncAuthorize(request);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.ABSTAIN));
    }

    @Test
    public void testDenied() {
        SecurityContext context = mock(SecurityContext.class);
        when(context.isAuthenticated()).thenReturn(false);

        SecurityEnvironment se = SecurityEnvironment.builder()
                .path("/private/some/path")
                .build();
        EndpointConfig ep = EndpointConfig.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(se);
        when(request.endpointConfig()).thenReturn(ep);

        AtzProviderSync provider = new AtzProviderSync();

        AuthorizationResponse response = provider.syncAuthorize(request);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
    }

    @Test
    public void testPermitted() {
        SecurityContext context = mock(SecurityContext.class);
        when(context.isAuthenticated()).thenReturn(true);

        SecurityEnvironment se = SecurityEnvironment.builder()
                .path("/private/some/path")
                .build();
        EndpointConfig ep = EndpointConfig.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(se);
        when(request.endpointConfig()).thenReturn(ep);

        AtzProviderSync provider = new AtzProviderSync();

        AuthorizationResponse response = provider.syncAuthorize(request);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
    }
}
