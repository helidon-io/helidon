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

package io.helidon.microprofile.tyrus;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class TyrusUpgradeProviderConfigTest {

    // Verify that TyrusUpgradeProvider is properly configured from builder.
    // TyrusUpgrader requires CDI so only providefr is tested.
    @Test
    void testUpgraderConfigBuilder() {
        TyrusUpgradeProvider upgrader = TyrusUpgradeProvider.tyrusBuilder()
                .addOrigin("origin1")
                .addOrigin("origin2")
                .build();

        Set<String> origins = upgrader.origins();
        assertThat(origins, containsInAnyOrder("origin1", "origin2"));
    }

}
