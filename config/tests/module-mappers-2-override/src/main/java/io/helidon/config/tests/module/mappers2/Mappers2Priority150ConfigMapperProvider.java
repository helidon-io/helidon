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

import java.math.BigInteger;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;

import javax.annotation.Priority;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigMapperProvider;

/**
 * Registers config mapper for {@link OptionalInt}, {@link Integer} and {@link java.math.BigInteger}.
 */
@Priority(150) // lower than default priority
public class Mappers2Priority150ConfigMapperProvider implements ConfigMapperProvider {

    @Override
    public Map<Class<?>, Function<Config, ?>> mappers() {
        return Map.of(OptionalInt.class, new OptionalIntConfigMapper(),
                      Integer.class, new IntegerConfigMapper(),
                      BigInteger.class, new BigIntegerConfigMapper());
    }

}
