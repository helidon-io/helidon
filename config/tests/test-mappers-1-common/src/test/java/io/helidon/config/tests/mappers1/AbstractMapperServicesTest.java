/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.CollectionsHelper;
import java.util.Locale;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

/**
 * Abstract test of ConfigMapper implementations available in {@code module-mappers-1-base} module.
 */
public abstract class AbstractMapperServicesTest {

    protected static final String LOGGER_KEY = "my-logger";
    protected static final String LOCALE_KEY = "my-locale";

    protected Config.Builder configBuilder() {
        return Config.builder()
                .sources(ConfigSources.from(CollectionsHelper.mapOf(
                        LOGGER_KEY, this.getClass().getName(),
                        LOCALE_KEY + ".language", "cs",
                        LOCALE_KEY + ".country", "CZ",
                        LOCALE_KEY + ".variant", "Praha")));
    }

    protected Logger getLogger() {
        return configBuilder()
                .build()
                .get(LOGGER_KEY).as(Logger.class);
    }

    protected Locale getLocale() {
        return configBuilder()
                .build()
                .get(LOCALE_KEY).as(Locale.class);
    }

}
