/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.security;

import java.util.concurrent.atomic.AtomicReference;

import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebClientSecurityTest {

    @Test
    void matchesHttpsOutboundTargetUsingRequestScheme() {
        AtomicReference<SecurityEnvironment> outboundEnvironment = new AtomicReference<>();
        Security security = Security.builder()
                .addOutboundSecurityProvider((request, environment, config) -> {
                    outboundEnvironment.set(environment);
                    return OutboundSecurityResponse.abstain();
                })
                .build();
        OutboundConfig outboundConfig = OutboundConfig.builder()
                .addTarget(OutboundTarget.builder("https-target")
                                   .addTransport("https")
                                   .addHost("idcs.example.test")
                                   .addPath("/oauth2/v1/introspect")
                                   .addMethod("POST")
                                   .build())
                .build();
        WebClientService stopBeforeNetwork = (chain, request) -> {
            throw new TestException();
        };
        Http1Client client = Http1Client.builder()
                .baseUri("https://idcs.example.test/oauth2/v1/introspect")
                .servicesDiscoverServices(false)
                .addService(WebClientSecurity.create(security))
                .addService(stopBeforeNetwork)
                .build();

        assertThrows(TestException.class, () -> client.post().request());

        SecurityEnvironment environment = outboundEnvironment.get();
        assertThat(environment.targetUri().getScheme(), is("https"));
        assertThat(environment.transport(), is("https"));
        assertThat(outboundConfig.findTarget(environment).isPresent(), is(true));
    }

    private static final class TestException extends RuntimeException {
    }
}
