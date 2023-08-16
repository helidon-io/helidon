/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import java.net.URI;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test security from configuration.
 */
public class SecurityFromConfigTest {
    private Security security;

    @BeforeEach
    public void init() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("security.conf"))
                .build();

        this.security = Security.create(config.get("security"));
    }

    @Test
    public void testSecurityProviderAuthn() {
        SecurityContext context = security.contextBuilder("unitTest").build();

        assertThat(context.isAuthenticated(), is(false));

        SecurityEnvironment.Builder envBuilder = context.env()
                .derive()
                .method("GET")
                .path("/test")
                .addAttribute("resourceType", "TEST")
                .targetUri(URI.create("http://localhost/test"));

        context.env(envBuilder);

        AuthenticationResponse authenticate = context.atnClientBuilder().buildAndGet();

        // current thread should have the correct subject
        assertThat(authenticate.user(), is(Optional.of(SecurityTest.SYSTEM)));

        // should work from context
        assertThat(context.user(), is(Optional.of(SecurityTest.SYSTEM)));

        context.runAs(SecurityContext.ANONYMOUS, () -> assertThat(context.isAuthenticated(), is(false)));

        // and correct once again
        assertThat(context.user(), is(Optional.of(SecurityTest.SYSTEM)));
    }

    @Test
    public void testSecurityProviderAuthz() {
        SecurityContext context = security.contextBuilder("unitTest").build();
        SecurityEnvironment.Builder envBuilder = context.env()
                .derive()
                .method("GET")
                .path("/test")
                .targetUri(URI.create("http://localhost/test"));

        context.env(envBuilder.addAttribute("resourceType", "SECOND_DENY"));
        // default authorizationClient
        AuthorizationResponse response = context.atzClientBuilder().buildAndGet();

        assertThat(response.status().isSuccess(), is(false));

        context.env(envBuilder.addAttribute("resourceType", "PERMIT"));
        response = context.atzClientBuilder().buildAndGet();

        assertThat(response.status().isSuccess(), is(true));

        context.env(envBuilder.addAttribute("resourceType", "DENY"));
        // non-default authorizationClient
        response = context.atzClientBuilder()
                .explicitProvider("FirstInstance")
                .buildAndGet();

        assertThat(response.status().isSuccess(), is(false));

        context.env(envBuilder.addAttribute("resourceType", "SECOND_DENY"));
        response = context.atzClientBuilder()
                .explicitProvider("FirstInstance")
                .buildAndGet();

        assertThat(response.status().isSuccess(), is(true));
    }
}
