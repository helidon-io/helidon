/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.common.TokenCredential;
import io.helidon.security.providers.oidc.common.OidcConfig;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link OidcSupport}.
 */
class OidcSupportTest {
    private static final String PARAM_NAME = "my-param-attempts";

    private final OidcConfig oidcConfig = OidcConfig.builder()
            .clientId("id")
            .clientSecret("secret")
            .identityUri(URI.create("http://localhost:7774/identity"))
            .tokenEndpointUri(URI.create("http://localhost:7774/token"))
            .authorizationEndpointUri(URI.create("http://localhost:7774/authorize"))
            .signJwk(JwkKeys.builder().build())
            .oidcMetadataWellKnown(false)
            .build();
    private final OidcConfig oidcConfigCustomParam = OidcConfig.builder()
            .clientId("id")
            .clientSecret("secret")
            .identityUri(URI.create("http://localhost:7774/identity"))
            .tokenEndpointUri(URI.create("http://localhost:7774/token"))
            .authorizationEndpointUri(URI.create("http://localhost:7774/authorize"))
            .signJwk(JwkKeys.builder().build())
            .oidcMetadataWellKnown(false)
            .redirectAttemptParam(PARAM_NAME)
            .build();

    private final OidcSupport oidcSupport = OidcSupport.create(oidcConfig);
    private final OidcSupport oidcSupportCustomParam = OidcSupport.create(oidcConfigCustomParam);
    private final OidcProvider provider = OidcProvider.builder()
            .oidcConfig(oidcConfig)
            .outboundConfig(OutboundConfig.builder()
                                    .addTarget(OutboundTarget.builder("disabled")
                                                       .addHost("www.example.com")
                                                       .config(Config.create(ConfigSources.create(Map.of(
                                                               "propagate",
                                                               "false"))))
                                                       .build())
                                    .build())
            .build();

    @Test
    void testRedirectAttemptNoParams() {
        String state = "http://localhost:7145/test";
        String newState = oidcSupport.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(oidcConfig.redirectAttemptParam() + "=1"));
    }

    @Test
    void testRedirectAttemptNoParamsCustomName() {
        String state = "http://localhost:7145/test";
        String newState = oidcSupportCustomParam.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(PARAM_NAME + "=1"));
    }

    @Test
    void testRedirectAttemptOtherParams() {
        String state = "http://localhost:7145/test?a=first&b=second";
        String newState = oidcSupport.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(oidcConfig.redirectAttemptParam() + "=1"));
    }

    @Test
    void testRedirectAttemptOtherParamsCustomName() {
        String state = "http://localhost:7145/test?a=first&b=second";
        String newState = oidcSupportCustomParam.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(PARAM_NAME + "=1"));
    }

    @Test
    void testRedirectAttemptParams() {
        String state = "http://localhost:7145/test?a=first&b=second&" + oidcConfig.redirectAttemptParam() + "=1";
        String newState = oidcSupport.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(oidcConfig.redirectAttemptParam() + "=2"));
    }

    @Test
    void testRedirectAttemptParamsCustomName() {
        String state = "http://localhost:7145/test?a=first&b=second&" + PARAM_NAME + "=1";
        String newState = oidcSupportCustomParam.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(PARAM_NAME + "=2"));
    }

    @Test
    void testRedirectAttemptParamsInMiddle() {
        String state = "http://localhost:7145/test?a=first&" + oidcConfig.redirectAttemptParam() + "=1&b=second";
        String newState = oidcSupport.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(oidcConfig.redirectAttemptParam() + "=2&b=second"));
    }

    @Test
    void testRedirectAttemptParamsInMiddleCustomName() {
        String state = "http://localhost:7145/test?a=first&" + PARAM_NAME + "=11&b=second";
        String newState = oidcSupportCustomParam.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(PARAM_NAME + "=12&b=second"));
    }

    @Test
    void testOutbound() {
        String tokenContent = "huhahihohyhe";
        TokenCredential tokenCredential = TokenCredential.builder()
                .token(tokenContent)
                .build();

        Subject subject = Subject.builder()
                .addPublicCredential(TokenCredential.class, tokenCredential)
                .build();

        ProviderRequest providerRequest = Mockito.mock(ProviderRequest.class);
        SecurityContext ctx = Mockito.mock(SecurityContext.class);

        when(ctx.user()).thenReturn(Optional.of(subject));
        when(providerRequest.securityContext()).thenReturn(ctx);

        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:7777"))
                .path("/test")
                .build();
        EndpointConfig endpointConfig = EndpointConfig.builder().build();

        OutboundSecurityResponse response = provider.outboundSecurity(providerRequest, outboundEnv, endpointConfig)
                .toCompletableFuture()
                .join();

        List<String> authorization = response.requestHeaders().get("Authorization");
        assertThat("Authorization header", authorization, hasItem("bearer " + tokenContent));
    }

    @Test
    void testOutboundFull() {
        String tokenContent = "huhahihohyhe";
        TokenCredential tokenCredential = TokenCredential.builder()
                .token(tokenContent)
                .build();

        Subject subject = Subject.builder()
                .addPublicCredential(TokenCredential.class, tokenCredential)
                .build();

        ProviderRequest providerRequest = Mockito.mock(ProviderRequest.class);
        SecurityContext ctx = Mockito.mock(SecurityContext.class);

        when(ctx.user()).thenReturn(Optional.of(subject));
        when(providerRequest.securityContext()).thenReturn(ctx);

        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .targetUri(URI.create("http://www.example.com:7777"))
                .path("/test")
                .build();
        EndpointConfig endpointConfig = EndpointConfig.builder().build();

        boolean outboundSupported = provider.isOutboundSupported(providerRequest, outboundEnv, endpointConfig);
        assertThat("Outbound should not be supported by default", outboundSupported, is(false));

        OutboundSecurityResponse response = provider.outboundSecurity(providerRequest, outboundEnv, endpointConfig)
                .toCompletableFuture()
                .join();

        assertThat("Disabled target should have empty headers", response.requestHeaders().size(), is(0));
    }
}