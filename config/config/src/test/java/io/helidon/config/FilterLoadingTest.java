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

package io.helidon.config;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test checking that auto-loaded filter service and filter provider service works.
 */
public class FilterLoadingTest {

    static final String ORIGINAL_VALUE_SUBJECT_TO_AUTO_FILTERING = "originalValue";
    private static final String ORIGINAL_VALUE_SUBJECT_TO_AUTO_FILTERING_VIA_PROVIDER = "originalValueForProviderTest";

    private static final String UNAFFECTED_KEY = "key1";
    private static final String UNAFFECTED_VALUE = "value1";

    public FilterLoadingTest() {
    }

    @Test
    public void testAutoLoadedFilter() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                UNAFFECTED_KEY, UNAFFECTED_VALUE,
                                AutoLoadedConfigFilter.KEY_SUBJECT_TO_AUTO_FILTERING,
                                  ORIGINAL_VALUE_SUBJECT_TO_AUTO_FILTERING,
                                "name", "Joachim"
                        )))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get(UNAFFECTED_KEY).asString(),
                   is(ConfigValues.simpleValue(UNAFFECTED_VALUE)));
        assertThat(config.get(AutoLoadedConfigFilter.KEY_SUBJECT_TO_AUTO_FILTERING).asString(),
                   is(ConfigValues.simpleValue(AutoLoadedConfigFilter.EXPECTED_FILTERED_VALUE)));
    }

    @Test
    public void testSuppressedAutoLoadedFilter() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                UNAFFECTED_KEY, "value1",
                                AutoLoadedConfigFilter.KEY_SUBJECT_TO_AUTO_FILTERING,
                                  ORIGINAL_VALUE_SUBJECT_TO_AUTO_FILTERING,
                                "name", "Joachim"
                        )))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        assertThat(config.get(UNAFFECTED_KEY).asString(),
                   is(ConfigValues.simpleValue(UNAFFECTED_VALUE)));
        assertThat(config.get(AutoLoadedConfigFilter.KEY_SUBJECT_TO_AUTO_FILTERING).asString(),
                   is(ConfigValues.simpleValue(ORIGINAL_VALUE_SUBJECT_TO_AUTO_FILTERING)));
    }

    @Test
    public void testPrioritizedAutoLoadedConfigFilters() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                             UNAFFECTED_KEY, "value1",
                             AutoLoadedConfigPriority.KEY_SUBJECT_TO_AUTO_FILTERING,
                               ORIGINAL_VALUE_SUBJECT_TO_AUTO_FILTERING,
                             "name", "Joachim"
                             )))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get(UNAFFECTED_KEY).asString(),
                   is(ConfigValues.simpleValue(UNAFFECTED_VALUE)));
        assertThat(config.get(AutoLoadedConfigPriority.KEY_SUBJECT_TO_AUTO_FILTERING).asString(),
                   is(ConfigValues.simpleValue(AutoLoadedConfigHighPriority.EXPECTED_FILTERED_VALUE)));
    }

    @Test
    public void testSuppressedPrioritizedAutoLoadedConfigFilters() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                             UNAFFECTED_KEY, "value1",
                             AutoLoadedConfigPriority.KEY_SUBJECT_TO_AUTO_FILTERING,
                               ORIGINAL_VALUE_SUBJECT_TO_AUTO_FILTERING,
                             "name", "Joachim"
                             )))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        assertThat(config.get(UNAFFECTED_KEY).asString(),
                   is(ConfigValues.simpleValue(UNAFFECTED_VALUE)));
        assertThat(config.get(AutoLoadedConfigPriority.KEY_SUBJECT_TO_AUTO_FILTERING).asString(),
                   is(ConfigValues.simpleValue(ORIGINAL_VALUE_SUBJECT_TO_AUTO_FILTERING)));
    }
}
