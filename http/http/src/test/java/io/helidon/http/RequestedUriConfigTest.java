/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.http;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType;
import io.helidon.http.RequestedUriDiscoveryContext.UnsafeRequestedUriSettingsException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.http.RequestedUriDiscoveryContext.Builder.REQUESTED_URI_DISCOVERY_CONFIG_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestedUriConfigTest {

    private static Config config;

    @BeforeAll
    static void initConfig() {
        config = Config.just(ConfigSources.classpath("/requestUriDiscovery.yaml"));
    }

    @Test
    void checkUnsafeDetectionOnEnable() {
        Config c = config.get("test-enabled-no-details.server." + REQUESTED_URI_DISCOVERY_CONFIG_KEY);
        assertThrows(UnsafeRequestedUriSettingsException.class, () ->
                             RequestedUriDiscoveryContext.builder()
                                     .config(c)
                                     .build(),
                     "defaulted non-HOST discovery type with no proxy settings");
    }

    @Test
    void checkExplicitTypesNoDetails() {
        Config c = config.get("test-explicit-types-no-details.server." + REQUESTED_URI_DISCOVERY_CONFIG_KEY);
        assertThrows(UnsafeRequestedUriSettingsException.class, () ->
                             RequestedUriDiscoveryContext.builder()
                                     .config(c)
                                     .build(),
                     "explicit non-HOST discovery types with no proxy settings");
    }

    @Test
    void checkEnum() {
        String v = "x-forwarded";
        Class<?> eClass = RequestedUriDiscoveryType.class;
        if (eClass.isEnum()) {
            RequestedUriDiscoveryType type = null;
            for (Object o : eClass.getEnumConstants()) {
                if (((Enum<?>) o).name().equalsIgnoreCase((v.replace('-', '_')))) {
                    type = (RequestedUriDiscoveryType) o;
                    break;
                }
            }
            assertThat("Mapped string to discovery type",
                       type,
                       allOf(notNullValue(),is(RequestedUriDiscoveryType.X_FORWARDED)));
        }
    }
}
