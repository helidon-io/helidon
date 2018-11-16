/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Priority;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigMapperProvider;

/**
 * Java beans support for configuration.
 */
@Priority(0) // priority should be low to be the last one used
public class BeansConfigMapperProvider implements ConfigMapperProvider {
    Map<Class<?>, Function<Config, ?>> EMPTY_MAP = CollectionsHelper.mapOf();

    @Override
    public Map<Class<?>, Function<Config, ?>> mappers() {
        return EMPTY_MAP;
    }

    @Override
    public <T> Optional<Function<Config, T>> mapper(Class<T> type) {
        // TODO check if class can be automatically created through reflection from config
        return Optional.empty();
    }
}