/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.config.tests.seprofile;

import io.helidon.config.Config;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProfileTest {
    private static Config config;

    @BeforeAll
    public static void setUpClass() {
        System.setProperty("helidon.config.profile", "named");

        config = Services.get(Config.class);
    }

    @Test
    public void testConfigValue() {
        String propertyValue = config.get("property")
                .asString()
                .get();

        assertThat(propertyValue, is("profile-specific"));
    }
}
