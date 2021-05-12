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

package io.helidon.security.providers.httpsign;

import java.nio.file.Paths;
import java.util.List;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.security.SubjectType;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;

import org.junit.jupiter.api.BeforeAll;

/**
 * Unit test for {@link HttpSignProvider} configured through a builder.
 */
public class HttpSignProviderBuilderTest extends HttpSignProviderTest {
    private static HttpSignProvider instance;

    @BeforeAll
    public static void initClass() {
        instance = HttpSignProvider.builder()
                .addAcceptHeader(HttpSignHeader.AUTHORIZATION)
                .addAcceptHeader(HttpSignHeader.SIGNATURE)
                .optional(true)
                .realm("prime")
                .inboundRequiredHeaders(inboundRequiredHeaders(SignedHeadersConfig
                                                                       .REQUEST_TARGET, "host"))
                .addInbound(hmacInbound())
                .addInbound(rsaInbound())
                .outbound(OutboundConfig.builder()
                                  .addTarget(rsaOutbound())
                                  .addTarget(hmacOutbound())
                                  .build())
                .build();
    }

    private static OutboundTarget hmacOutbound() {
        return OutboundTarget.builder("second")
                .addTransport("http")
                .addHost("localhost")
                .addPath("/second/.*")
                .customObject(OutboundTargetDefinition.class,
                              OutboundTargetDefinition
                                      .builder("myServiceKeyId")
                                      .hmacSecret("MyPasswordForHmac")
                                      .build())
                .build();
    }

    private static OutboundTarget rsaOutbound() {
        return OutboundTarget.builder("first")
                .addTransport("http")
                .addHost("example.org")
                .addPath("/my/.*")
                .customObject(OutboundTargetDefinition.class, OutboundTargetDefinition
                        .builder("rsa-key-12345"
                        )
                        .signedHeaders(inboundRequiredHeaders("host", SignedHeadersConfig
                                .REQUEST_TARGET))
                        .privateKeyConfig(KeyConfig.keystoreBuilder()
                                                  .keystore(Resource.create(Paths.get(
                                                          "src/test/resources/keystore"
                                                                  + ".p12")))
                                                  .keystorePassphrase("password"
                                                                              .toCharArray())
                                                  .keyAlias("myPrivateKey")
                                                  .build())
                        .build())
                .build();
    }

    private static InboundClientDefinition rsaInbound() {
        return InboundClientDefinition.builder("rsa-key-12345")
                .principalName("aUser")
                .subjectType(SubjectType.USER)
                .publicKeyConfig(KeyConfig.keystoreBuilder()
                                         .keystore(Resource.create(Paths.get("src/test/resources/keystore.p12")))
                                         .keystorePassphrase("password".toCharArray())
                                         .certAlias("service_cert")
                                         .build())
                .build();
    }

    private static InboundClientDefinition hmacInbound() {
        return InboundClientDefinition.builder("myServiceKeyId")
                .principalName("aSetOfTrustedServices")
                .hmacSecret("MyPasswordForHmac")
                .build();
    }

    private static SignedHeadersConfig inboundRequiredHeaders(String requestTarget, String host) {
        return SignedHeadersConfig.builder()
                .defaultConfig(SignedHeadersConfig.HeadersConfig
                                       .create(List.of("date")))
                .config("get",
                        SignedHeadersConfig.HeadersConfig
                                .create(List.of("date",
                                                                 requestTarget,
                                                                 host),
                                        List.of("authorization")))
                .build();
    }

    @Override
    HttpSignProvider getProvider() {
        return instance;
    }
}
