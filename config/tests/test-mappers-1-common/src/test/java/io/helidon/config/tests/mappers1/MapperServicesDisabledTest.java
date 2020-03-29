/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.tests.mappers1;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Same test as {@link MapperServicesEnabledTest} with {@link Config.Builder#disableMapperServices()}.
 */
public class MapperServicesDisabledTest extends AbstractMapperServicesTest {

    @Override
    protected Config.Builder configBuilder() {
        return super.configBuilder()
                .disableMapperServices();
    }

    @Test
    public void testLogger() {
        ConfigMappingException cme = assertThrows(ConfigMappingException.class, this::getLogger);
        assertThat(cme.getMessage(), containsString(LOGGER_KEY));
    }

    @Test
    public void testLocale() {
        ConfigMappingException cme = assertThrows(ConfigMappingException.class, this::getLocale);
        assertThat(cme.getMessage(), containsString(LOCALE_KEY));
    }

}
