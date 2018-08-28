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

package io.helidon.config.tests.module.mappers2;

import java.util.Locale;

import io.helidon.config.Config;
import io.helidon.config.ConfigMapper;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;

/**
 * {@link ConfigMapper}s implementation for {@link Locale}.
 */
public class LocaleConfigMapper implements ConfigMapper<Locale> {

    @Override
    public Locale apply(Config config) throws ConfigMappingException, MissingValueException {
        String language = config.get("language").asString();
        String country = config.get("country").asString("");
        String variant = config.get("variant").asString("");

        return new Locale("m2:" + language, "m2:" + country, "m2:" + variant);
    }

}
