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

package io.helidon.common.tls;

import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TlsMaterialConfigTest {
    @Test
    void materialReadsConfiguredKeys() {
        TlsMaterial material = TlsMaterial.create(materialConfig());

        assertThat(material.trustAll(), is(true));
        assertThat(material.keyManagerFactoryAlgorithm(), is(Optional.of("test-kmf")));
        assertThat(material.trustManagerFactoryAlgorithm(), is(Optional.of("test-tmf")));
        assertThat(material.internalKeystoreType(), is(Optional.of("test-store")));
    }

    @Test
    void tlsConfigStillReadsInheritedMaterialKeys() {
        TlsConfig tlsConfig = TlsConfig.create(materialConfig());

        assertThat(tlsConfig.trustAll(), is(true));
        assertThat(tlsConfig.keyManagerFactoryAlgorithm(), is(Optional.of("test-kmf")));
        assertThat(tlsConfig.trustManagerFactoryAlgorithm(), is(Optional.of("test-tmf")));
        assertThat(tlsConfig.internalKeystoreType(), is(Optional.of("test-store")));
    }

    private static Config materialConfig() {
        return Config.just(ConfigSources.create(Map.of(
                "trust-all", "true",
                "key-manager-factory-algorithm", "test-kmf",
                "trust-manager-factory-algorithm", "test-tmf",
                "internal-keystore-type", "test-store")));
    }
}
