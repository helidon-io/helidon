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

package io.helidon.config.tests.module.mappers2;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Priority;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigMapperProvider;

/**
 * Registers Config mappers for {@link Locale}.
 */
@Priority(50) // higher than default priority
public class Mappers2Priority50ConfigMapperProvider implements ConfigMapperProvider {

    @Override
    public Map<Class<?>, Function<Config, ?>> mappers() {
        return Map.of(Locale.class, new LocaleConfigMapper());
    }

}
