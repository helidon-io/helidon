/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.config.encryption;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for config encryption filter configured through configuration itself.
 */
public class SecureConfigFromConfigTest extends AbstractSecureConfigTest {
    private static Config config;
    private static Config configRequiresEncryption;

    @BeforeAll
    public static void initClass() {
        config = Config.create().get("aes-current");

        configRequiresEncryption = Config.builder()
                .addSource(ConfigSources.create(Map.of(ConfigProperties.REQUIRE_ENCRYPTION_CONFIG_KEY, "true")))
                .addSource(ConfigSources.classpath("application.yaml"))
                .build().get("aes-current");

        assertThat("We must have the correct configuration file", config.get("pwd1").type().isLeaf());
        assertThat("We must have the correct configuration file", configRequiresEncryption.get("pwd1").type().isLeaf());
    }

    @Override
    Config getConfig() {
        return config;
    }

    @Override
    Config getConfigRequiresEncryption() {
        return configRequiresEncryption;
    }

    @Test
    public void testSymmetricNoPassword() throws Exception {
        // these are expected not decrypted, as master password was not provided!
        testPassword(getConfigRequiresEncryption(),
                     "pwd4",
                     "${GCM=bQPPs+e2i4Sry7xB3MJWUPCnGWzx69Rgs6kskDIq8LRSJFonZ1NDlLhVhZBhtwpVdNlDa9qQSw==}"
        );
        testPassword(getConfigRequiresEncryption(),
                     "pwd6",
                     "${GCM=p5GPxwagVVPyM3E7BvEy798rmQJojLuu957jvEZrqOKuJKeiCXyU7jONvU4=}"
        );
    }

    @Test
    public void testWrongSymmetric() {
        testPassword(getConfig(), "pwd9", "${GCM=not really encrypted}");
    }
}
