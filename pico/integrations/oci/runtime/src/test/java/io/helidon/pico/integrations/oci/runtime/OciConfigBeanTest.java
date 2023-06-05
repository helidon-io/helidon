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

package io.helidon.pico.integrations.oci.runtime;

import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MapConfigSource;
import io.helidon.pico.api.PicoServicesConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class OciConfigBeanTest {

    @Test
    void potentialAuthStrategies() {
        Config config = createTestConfig(ociAuthConfigStrategies(null));
        OciConfigBean cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.potentialAuthStrategies(),
                   contains("config", "config-file", "instance-principals", "resource-principals"));

        config = createTestConfig(ociAuthConfigStrategies("auto"));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.potentialAuthStrategies(),
                   contains("config", "config-file", "instance-principals", "resource-principals"));

        config = createTestConfig(ociAuthConfigStrategies(null, "a", "b", "auto"));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.potentialAuthStrategies(),
                   contains("config", "config-file", "instance-principals", "resource-principals"));

        config = createTestConfig(ociAuthConfigStrategies(null, "a", "b"));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.potentialAuthStrategies(),
                   contains("a", "b"));

        config = createTestConfig(ociAuthConfigStrategies("c", "a", "b"));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.potentialAuthStrategies(),
                   contains("c"));

        config = createTestConfig(ociAuthConfigStrategies("c", "a", "b", "auto"));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.potentialAuthStrategies(),
                   contains("c"));

        config = createTestConfig(ociAuthConfigStrategies("auto", "a", "b"));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.potentialAuthStrategies(),
                   contains("a", "b"));

        config = createTestConfig(ociAuthConfigStrategies("", ""));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.potentialAuthStrategies(),
                   contains("config", "config-file", "instance-principals", "resource-principals"));
    }

    @Test
    void fileConfigIsPresent() {
        Config config = createTestConfig(ociAuthConfigFile("path", "profile"));
        OciConfigBean cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.fileConfigIsPresent(),
                   is(true));
        assertThat(cfg.configProfile().orElseThrow(),
                   equalTo("profile"));

        config = createTestConfig(ociAuthConfigFile("path", null));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.fileConfigIsPresent(), is(true));
        assertThat(cfg.configProfile().orElseThrow(),
                   equalTo("DEFAULT"));

        config = createTestConfig(ociAuthConfigFile("", ""));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.fileConfigIsPresent(),
                   is(false));

        config = createTestConfig(ociAuthConfigFile(null, null));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.fileConfigIsPresent(),
                   is(false));
        assertThat(cfg.configProfile().orElseThrow(),
                   equalTo("DEFAULT"));
    }

    @Test
    void simpleConfigIsPresent() {
        Config config = createTestConfig(ociAuthSimpleConfig("tenant", "user", "phrase", "fp", "pk", "pkp", "region"));
        OciConfigBean cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.simpleConfigIsPresent(),
                   is(true));

        config = createTestConfig(ociAuthSimpleConfig("tenant", "user", "phrase", "fp", "pk", "pkp", null));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.simpleConfigIsPresent(),
                   is(true));

        config = createTestConfig(ociAuthSimpleConfig(null, "user", "phrase", "fp", "pk", "pkp", null));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.simpleConfigIsPresent(),
                   is(false));

        config = createTestConfig(ociAuthSimpleConfig("tenant", null, "phrase", "fp", "pk", "pkp", null));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.simpleConfigIsPresent(),
                   is(false));

        config = createTestConfig(ociAuthSimpleConfig("tenant", "user", null, "fp", "pk", "pkp", null));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.simpleConfigIsPresent(),
                   is(false));

        config = createTestConfig(ociAuthSimpleConfig("tenant", "user", "phrase", null, "pk", "pkp", null));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.simpleConfigIsPresent(),
                   is(false));

        config = createTestConfig(ociAuthSimpleConfig("tenant", "user", "phrase", "fp", null, "pkp", null));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.simpleConfigIsPresent(),
                   is(true));

        config = createTestConfig(ociAuthSimpleConfig("tenant", "user", "phrase", "fp", "pk", null, "region"));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.simpleConfigIsPresent(),
                   is(true));

        config = createTestConfig(ociAuthSimpleConfig("tenant", "user", "phrase", "fp", null, null, "region"));
        cfg = OciConfigBeanDefault.toBuilder(config).build();
        assertThat(cfg.simpleConfigIsPresent(),
                   is(false));
    }

    static Config createTestConfig(MapConfigSource.Builder... builders) {
        return Config.builder(builders)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
    }

    static MapConfigSource.Builder basicTestingConfigSource() {
        return ConfigSources.create(
                Map.of(
                        PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_PERMITS_DYNAMIC, "true",
                        PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_ACTIVATION_LOGS, "true"
                ), "config-basic");
    }

    static MapConfigSource.Builder ociAuthConfigStrategies(String strategy,
                                                           String... strategies) {
        Map<String, String> map = new HashMap<>();
        if (strategy != null) {
            map.put("auth-strategy", strategy);
        }
        if (strategies != null) {
            map.put("auth-strategies", String.join(",", strategies));
        }
        return ConfigSources.create(map, "config-oci-auth-strategies");
    }

    static MapConfigSource.Builder ociAuthConfigFile(String configPath,
                                                     String profile) {
        Map<String, String> map = new HashMap<>();
        if (configPath != null) {
            map.put("config.path", configPath);
        }
        if (profile != null) {
            map.put("config.profile", String.join(",", profile));
        }
        return ConfigSources.create(map, "config-oci-auth-config");
    }

    static MapConfigSource.Builder ociAuthSimpleConfig(String tenantId,
                                                       String userId,
                                                       String passPhrase,
                                                       String fingerPrint,
                                                       String privateKey,
                                                       String privateKeyPath,
                                                       String region) {
        Map<String, String> map = new HashMap<>();
        if (tenantId != null) {
            map.put("auth.tenant-id", tenantId);
        }
        if (userId != null) {
            map.put("auth.user-id", userId);
        }
        if (passPhrase != null) {
            map.put("auth.passphrase", passPhrase);
        }
        if (fingerPrint != null) {
            map.put("auth.fingerprint", fingerPrint);
        }
        if (privateKey != null) {
            map.put("auth.private-key", privateKey);
        }
        if (privateKeyPath != null) {
            map.put("auth.private-key-path", privateKeyPath);
        }
        if (region != null) {
            map.put("auth.region", region);
        }
        return ConfigSources.create(map, "config-oci-auth-simple");
    }
}
