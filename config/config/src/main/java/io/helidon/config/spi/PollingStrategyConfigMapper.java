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

package io.helidon.config.spi;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.OptionalHelper;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigMappers;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;
import io.helidon.config.PollingStrategies;

/**
 * Mapper to convert meta-configuration to a {@link PollingStrategy} instance.
 */
class PollingStrategyConfigMapper {

    private static final Logger LOGGER = Logger.getLogger(PollingStrategyConfigMapper.class.getName());

    private static final String PROPERTIES_KEY = "properties";
    private static final String TYPE_KEY = "type";
    private static final String CLASS_KEY = "class";

    private static final String REGULAR_TYPE = "regular";
    private static final String WATCH_TYPE = "watch";

    static PollingStrategyConfigMapper instance() {
        return SingletonHolder.INSTANCE;
    }

    public <T> Function<T, Supplier<PollingStrategy>> apply(Config config, Class<T> targetType)
            throws ConfigMappingException, MissingValueException {
        Config properties = config.get(PROPERTIES_KEY) // use properties config node
                .node().orElse(Config.empty()); // or empty config node

        return OptionalHelper.from(config.get(TYPE_KEY).asOptionalString() // `type` is specified
                .flatMap(type -> this.builtin(type, properties, targetType))) // return built-in polling strategy
                .or(() -> config.get(CLASS_KEY).asOptional(Class.class) // `class` is specified
                        .flatMap(clazz -> custom(clazz, properties, targetType))) // return custom polling strategy
                .asOptional()
                .orElseThrow(() -> new ConfigMappingException(config.key(), "Uncompleted polling-strategy configuration."));
    }

    private <T> Optional<Function<T, Supplier<PollingStrategy>>> builtin(String type,
                                                                         Config properties,
                                                                         Class<T> targetType) {
        final Function<T, Supplier<PollingStrategy>> pollingStrategy;
        switch (type) {
        case REGULAR_TYPE:
            pollingStrategy = target -> () -> properties.as(PollingStrategies.ScheduledBuilder.class).get();
            break;
        case WATCH_TYPE:
            pollingStrategy = PollingStrategyConfigMapper::watchSupplier;
            break;
        default:
            pollingStrategy = null;
        }
        return Optional.ofNullable(pollingStrategy);
    }

    private static <T> Supplier<PollingStrategy> watchSupplier(T target) {
        if (target instanceof Path) {
            Path path = (Path) target;
            return PollingStrategies.watch(path);
        }
        throw new ConfigException("Incorrect target type ('" + target.getClass().getName()
                                          + "') for WATCH polling strategy. Expected 'Path'.");
    }

    private <T> Optional<Function<T, Supplier<PollingStrategy>>> custom(Class<?> clazz,
                                                                        Config properties,
                                                                        Class<T> targetType) {
        Function<T, Supplier<PollingStrategy>> pollingStrategyFunction;
        if (PollingStrategy.class.isAssignableFrom(clazz)) {
            // set class is PollingStrategy implementation
            try {
                // use public constructor with target parameter
                Constructor<?> constructor = clazz.getConstructor(targetType);
                MethodHandle constructorHandle = MethodHandles.publicLookup().unreflectConstructor(constructor);

                pollingStrategyFunction = customSupplier(constructorHandle);
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                LOGGER.log(Level.FINE, ex, () -> clazz.getName() + " does not have public constructor with single parameter ("
                        + targetType.getName() + "). Generic instance from Config will be used.");
                // use generic mapping as a fallback
                pollingStrategyFunction = target -> (Supplier<PollingStrategy>) properties.as(clazz);
            }
        } else {
            // use builder pattern as a fallback
            pollingStrategyFunction = target -> (Supplier<PollingStrategy>)
                    properties.map(ConfigMappers.from(PollingStrategy.class, clazz));
        }
        return Optional.ofNullable(pollingStrategyFunction);
    }

    private static <T> Function<T, Supplier<PollingStrategy>> customSupplier(MethodHandle constructorHandle) {
        return target -> {
            try {
                return (Supplier<PollingStrategy>) constructorHandle.invoke(target);
            } catch (Throwable ex) {
                throw new ConfigException("Creating new polling strategy instance has failed with exception.", ex);
            }
        };
    }

    /**
     * Singleton holder for {@link PollingStrategyConfigMapper}.
     */
    static class SingletonHolder {
        private static final PollingStrategyConfigMapper INSTANCE = new PollingStrategyConfigMapper();
    }

}
