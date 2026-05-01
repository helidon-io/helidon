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

package io.helidon.security.providers.httpsign;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link HttpSignProvider} configured from a configuration file.
 */
class CurrentHttpSignProviderConfigTest extends CurrentHttpSignProviderTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static HttpSignProvider instance;

    @BeforeAll
    static void initClass() {
        Config config = Config.create();

        instance = HttpSignProvider.create(config.get("current.http-signatures"));
    }

    @Test
    void testInboundDateValidityCanBeDisabledFromConfig() {
        HttpSignProvider provider = HttpSignProvider.create(disabledDateValidityConfig());
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(STALE_DATE));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(staleTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        headers.put("Signature",
                    List.of(signatureHeader(signingEnv,
                                            OutboundTargetDefinition.builder("myServiceKeyId")
                                                    .hmacSecret("MyPasswordForHmac")
                                                    .signedHeaders(signedHeaders())
                                                    .build(),
                                            false)));

        AuthenticationResponse atnResponse = authenticate(provider, headers);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Override
    HttpSignProvider getProvider() {
        return instance;
    }

    private static Config disabledDateValidityConfig() {
        return Config.builder()
                .sources(ConfigSources.create(Map.of(
                        "inbound-date-validity", "PT0S",
                        "inbound.keys.0.key-id", "myServiceKeyId",
                        "inbound.keys.0.algorithm", "hmac-sha256",
                        "inbound.keys.0.principal-name", "aSetOfTrustedServices",
                        "inbound.keys.0.hmac.secret", "MyPasswordForHmac")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
    }

    private static AuthenticationResponse authenticate(HttpSignProvider provider, Map<String, List<String>> headers) {
        SecurityContext context = mock(SecurityContext.class);
        when(context.executorService()).thenReturn(ForkJoinPool.commonPool());
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        return Single.create(provider.authenticate(request)).await(TIMEOUT);
    }
}
