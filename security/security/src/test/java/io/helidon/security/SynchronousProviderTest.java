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

package io.helidon.security;

import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SynchronousProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link SynchronousProvider}.
 * This is (mostly) a compilation based test - as the class does not directly implement the interfaces, I validate in this
 * test that correct method signatures are present in it.
 */
public class SynchronousProviderTest {
    @Test
    public void testSecurity() {
        Security security = Security.builder()
                .addAuthenticationProvider(new Atn())
                .addAuthorizationProvider(new Atz())
                .addOutboundSecurityProvider(new Outbound())
                .build();

        SecurityContext context = security.contextBuilder("unit_test").build();

        AuthenticationResponse authenticationResponse = context.atnClientBuilder().buildAndGet();
        checkResponse(authenticationResponse);
        AuthorizationResponse authorizationResponse = context.atzClientBuilder().buildAndGet();
        checkResponse(authorizationResponse);
        OutboundSecurityResponse outboundSecurityResponse = context.outboundClientBuilder().buildAndGet();
        checkResponse(outboundSecurityResponse);
    }

    private void checkResponse(SecurityResponse response) {
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.ABSTAIN));
        assertThat(response.description().isPresent(), is(true));
        assertThat(response.description().get(), is("unit.test"));
    }

    private class Atn implements AuthenticationProvider, SecurityProvider {
        @Override
        public AuthenticationResponse authenticate(ProviderRequest providerRequest) {
            return AuthenticationResponse.builder()
                    .description("unit.test")
                    .status(SecurityResponse.SecurityStatus.ABSTAIN)
                    .build();
        }
    }

    private class Atz implements AuthorizationProvider, SecurityProvider {
        @Override
        public AuthorizationResponse authorize(ProviderRequest providerRequest) {
            return AuthorizationResponse.builder()
                    .description("unit.test")
                    .status(SecurityResponse.SecurityStatus.ABSTAIN)
                    .build();
        }
    }

    private class Outbound implements OutboundSecurityProvider, SecurityProvider {
        @Override
        public OutboundSecurityResponse outboundSecurity(ProviderRequest providerRequest,
                                                         SecurityEnvironment outEnv,
                                                         EndpointConfig epc) {
            return OutboundSecurityResponse.builder()
                    .description("unit.test")
                    .status(SecurityResponse.SecurityStatus.ABSTAIN)
                    .build();
        }
    }
}
