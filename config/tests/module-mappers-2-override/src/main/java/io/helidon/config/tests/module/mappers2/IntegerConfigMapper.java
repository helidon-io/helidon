/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;

/**
 * Config mapper implementation for {@link Integer}, but returns a value minus 1.
 */
public class IntegerConfigMapper implements Function<Config, Integer> {

    @Override
    public Integer apply(Config config) throws ConfigMappingException, MissingValueException {
        return Integer.parseInt(config.asString().get()) - 1;
    }

}
