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

import java.util.List;
import java.util.Optional;

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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link OutboundProviderSync}.
 */
public class OutboundProviderSyncTest {
    @Test
    public void testAbstain() {
        SecurityContext context = mock(SecurityContext.class);
        when(context.user()).thenReturn(Optional.empty());
        when(context.service()).thenReturn(Optional.empty());

        SecurityEnvironment se = SecurityEnvironment.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(se);

        OutboundProviderSync ops = new OutboundProviderSync();
        OutboundSecurityResponse response = ops.syncOutbound(request, SecurityEnvironment.create(), EndpointConfig.create());

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.ABSTAIN));
    }

    @Test
    public void testSuccess() {
        String username = "aUser";
        Subject subject = Subject.create(Principal.create(username));

        SecurityContext context = mock(SecurityContext.class);
        when(context.user()).thenReturn(Optional.of(subject));
        when(context.service()).thenReturn(Optional.empty());

        SecurityEnvironment se = SecurityEnvironment.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(se);

        OutboundProviderSync ops = new OutboundProviderSync();
        OutboundSecurityResponse response = ops.syncOutbound(request, SecurityEnvironment.create(), EndpointConfig.create());

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.requestHeaders().get("X-AUTH-USER"), is(List.of(username)));
    }
}
