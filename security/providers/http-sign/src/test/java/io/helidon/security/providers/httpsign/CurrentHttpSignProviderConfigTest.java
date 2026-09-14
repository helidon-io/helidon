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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;
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
    private static HttpSignProvider instance;

    @BeforeAll
    static void initClass() {
        Config config = Config.create();

        instance = HttpSignProvider.create(config.get("current.http-signatures"));
    }

    @Test
    void testInboundDateValidityCanBeDisabledFromConfig() {
        String configText = """
                http-signatures:
                  inbound-date-validity: "PT0S"
                  inbound:
                    keys:
                      - key-id: "myServiceKeyId"
                        algorithm: "hmac-sha256"
                        principal-name: "aSetOfTrustedServices"
                        hmac.secret: "MyPasswordForHmac"
                """;
        HttpSignProvider provider = HttpSignProvider.create(Config.just(configText, MediaTypes.APPLICATION_YAML)
                                                                  .get("http-signatures"));
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(STALE_DATE));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(staleTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        headers.put("Signature",
                    List.of(signatureHeader(signingEnv,
                                            OutboundTargetDefinition.builder("myServiceKeyId")
                                                    .hmacSecret("MyPasswordForHmac")
                                                    .signedHeaders(signedHeadersNoAuthorization())
                                                    .build(),
                                            false)));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = provider.authenticate(request);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void testInboundDateValidityCanBeExtendedFromConfig() {
        String configText = """
                http-signatures:
                  inbound-date-validity: "PT10M"
                  inbound:
                    keys:
                      - key-id: "myServiceKeyId"
                        algorithm: "hmac-sha256"
                        principal-name: "aSetOfTrustedServices"
                        hmac.secret: "MyPasswordForHmac"
                """;
        HttpSignProvider provider = HttpSignProvider.create(Config.just(configText, MediaTypes.APPLICATION_YAML)
                                                                  .get("http-signatures"));
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(STALE_DATE));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(staleTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        headers.put("Signature",
                    List.of(signatureHeader(signingEnv,
                                            OutboundTargetDefinition.builder("myServiceKeyId")
                                                    .hmacSecret("MyPasswordForHmac")
                                                    .signedHeaders(signedHeadersNoAuthorization())
                                                    .build(),
                                            false)));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = provider.authenticate(request);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Override
    HttpSignProvider getProvider() {
        return instance;
    }
}
