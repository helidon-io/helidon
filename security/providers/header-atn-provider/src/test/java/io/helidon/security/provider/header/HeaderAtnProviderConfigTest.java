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

package io.helidon.security.provider.header;

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Principal;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link HeaderAtnProvider} using configuration.
 */
public class HeaderAtnProviderConfigTest extends HeaderAtnProviderTest {
    private static Config config;

    @BeforeAll
    public static void init() {
        config = Config.create();
    }

    @Override
    HeaderAtnProvider getFullProvider() {
        return HeaderAtnProvider.fromConfig(config.get("full-config"));
    }

    @Override
    HeaderAtnProvider getServiceProvider() {
        return HeaderAtnProvider.fromConfig(config.get("service-config"));
    }

    @Override
    HeaderAtnProvider getNoSecurityProvider() {
        return HeaderAtnProvider.fromConfig(config.get("no-atn-outbound-config"));
    }

    @Test
    public void testProviderService() {
        String username = "username";

        Security security = Security.fromConfig(config);
        SecurityContext context = security.contextBuilder("unit-test")
                .env(SecurityEnvironment.builder()
                             .header("Authorization", "bearer " + username)
                             .build())
                .build();

        AuthenticationResponse response = context.atnClientBuilder().buildAndGet();

        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.getUser(), is(not(Optional.empty())));

        response.getUser()
                .map(Subject::getPrincipal)
                .map(Principal::getName)
                .ifPresent(user -> {
                    assertThat(user, is(username));
                });

        assertThat(response.getService(), is(Optional.empty()));
    }
}
