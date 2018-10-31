/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ExecutionException;

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

        this.security = Security.fromConfig(config);
    }

    @Test
    public void testSecurityProviderAuthn() throws ExecutionException, InterruptedException {
        SecurityContext context = security.contextBuilder("unitTest").build();

        assertThat(context.isAuthenticated(), is(false));

        SecurityEnvironment.Builder envBuilder = context.getEnv()
                .derive()
                .method("GET")
                .path("/test")
                .addAttribute("resourceType", "TEST")
                .targetUri(URI.create("http://localhost/test"));

        context.setEnv(envBuilder);

        AuthenticationResponse authenticate = context.atnClientBuilder().buildAndGet();

        // current thread should have the correct subject
        assertThat(authenticate.getUser(), is(Optional.of(SecurityTest.SYSTEM)));

        // should work from context
        assertThat(context.getUser(), is(Optional.of(SecurityTest.SYSTEM)));

        context.runAs(SecurityContext.ANONYMOUS, () -> assertThat(context.isAuthenticated(), is(false)));

        // and correct once again
        assertThat(context.getUser(), is(Optional.of(SecurityTest.SYSTEM)));
    }

    @Test
    public void testSecurityProviderAuthz() throws ExecutionException, InterruptedException {
        SecurityContext context = security.contextBuilder("unitTest").build();
        SecurityEnvironment.Builder envBuilder = context.getEnv()
                .derive()
                .method("GET")
                .path("/test")
                .targetUri(URI.create("http://localhost/test"));

        context.setEnv(envBuilder.addAttribute("resourceType", "SECOND_DENY"));
        // default authorizationClient
        AuthorizationResponse response = context.atzClientBuilder().buildAndGet();

        assertThat(response.getStatus().isSuccess(), is(false));

        context.setEnv(envBuilder.addAttribute("resourceType", "PERMIT"));
        response = context.atzClientBuilder().buildAndGet();

        assertThat(response.getStatus().isSuccess(), is(true));

        context.setEnv(envBuilder.addAttribute("resourceType", "DENY"));
        // non-default authorizationClient
        response = context.atzClientBuilder()
                .explicitProvider("FirstInstance")
                .buildAndGet();

        assertThat(response.getStatus().isSuccess(), is(false));

        context.setEnv(envBuilder.addAttribute("resourceType", "SECOND_DENY"));
        response = context.atzClientBuilder()
                .explicitProvider("FirstInstance")
                .buildAndGet();

        assertThat(response.getStatus().isSuccess(), is(true));
    }
}
