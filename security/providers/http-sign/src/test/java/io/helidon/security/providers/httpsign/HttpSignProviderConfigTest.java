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

import io.helidon.config.Config;

import org.junit.jupiter.api.BeforeAll;

/**
 * Unit test for {@link HttpSignProvider} configured from a configuration file.
 */
public class HttpSignProviderConfigTest extends HttpSignProviderTest {
    private static HttpSignProvider instance;

    @BeforeAll
    public static void initClass() {
        Config config = Config.create();

        instance = HttpSignProvider.create(config.get("security.providers.0.http-signatures"));
    }

    @Override
    HttpSignProvider getProvider() {
        return instance;
    }
}
