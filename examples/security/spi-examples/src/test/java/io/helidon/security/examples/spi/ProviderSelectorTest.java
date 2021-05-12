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

package io.helidon.security.examples.spi;

import io.helidon.security.AuthorizationResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;

import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link ProviderSelector}.
 */
public class ProviderSelectorTest {
    @Test
    public void integrateIt() {
        Security security = Security.builder()
                .providerSelectionPolicy(ProviderSelector::create)
                .addProvider(new AtnProviderSync())
                .addProvider(new AtzProviderSync())
                .build();

        SecurityContext context = security.createContext("unit-test");
        context.env(SecurityEnvironment.builder().path("/public/path"));

        AuthorizationResponse response = context.authorize();

        // if we reached here, the policy worked
    }
}
