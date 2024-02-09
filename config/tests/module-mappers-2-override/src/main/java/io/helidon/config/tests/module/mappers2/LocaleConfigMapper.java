/*
 * Copyright (c) 2017, 2024 Oracle and/or its affiliates.
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

package io.helidon.config.tests.module.mappers2;

import java.util.Locale;
import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;

/**
 * Config mappers implementation for {@link Locale}.
 */
public class LocaleConfigMapper implements Function<Config, Locale> {

    @Override
    public Locale apply(Config config) throws ConfigMappingException, MissingValueException {
        String language = config.get("language").asString().get();
        String country = config.get("country").asString().orElse("");
        String variant = config.get("variant").asString().orElse("");

        return Locale.of("m2:" + language, "m2:" + country, "m2:" + variant);
    }

}
