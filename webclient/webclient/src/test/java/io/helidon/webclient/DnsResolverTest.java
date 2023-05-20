/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Unit test for {@link io.helidon.webclient.DnsResolverType}.
 */
class DnsResolverTest {
    @ParameterizedTest
    @EnumSource(DnsResolverType.class)
    void testConfig(DnsResolverType dnsResolverType) {
        WebClientConfiguration webConfig = WebClient.builder()
                .baseUri("http://localhost:80")
                .dnsResolverType(dnsResolverType)
                .configuration();
        assertThat(webConfig.dnsResolverType(), is(dnsResolverType));
    }

    @ParameterizedTest
    @MethodSource("expectedExceptions")
    void testExpectedException(DnsResolverType dnsResolverType, String exeptionClass) {
        WebClient webClient = WebClient.builder()
                .baseUri("http://invalid.site.com")
                .dnsResolverType(dnsResolverType)
                .build();
        WebClientRequestBuilder webClientRequestBuilder = webClient.method("GET");
        ExecutionException exe = assertThrows(ExecutionException.class, () -> {
            webClientRequestBuilder.submit().get();
        });
        String dnsResolverExceptionClass = exe.getCause().getCause().getClass().getName();
        assertThat(dnsResolverExceptionClass, endsWith(exeptionClass));
    }

    private static Stream<Arguments> expectedExceptions() {
        return Stream.of(
                arguments(DnsResolverType.NONE, "java.nio.channels.UnresolvedAddressException"),
                // This can either be io.netty.resolver.dns.DnsResolveContext$SearchDomainUnknownHostException
                // or java.net.UnknownHostException
                arguments(DnsResolverType.ROUND_ROBIN, "UnknownHostException"),
                arguments(DnsResolverType.DEFAULT, "java.net.UnknownHostException")
        );
    }
}
