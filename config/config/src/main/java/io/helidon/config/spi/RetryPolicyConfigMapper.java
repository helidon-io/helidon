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

import java.util.Optional;

import io.helidon.common.OptionalHelper;
import io.helidon.config.Config;
import io.helidon.config.ConfigMapper;
import io.helidon.config.ConfigMappers;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;
import io.helidon.config.RetryPolicies;

/**
 * Mapper to convert meta-configuration to {@link RetryPolicy} instance.
 */
class RetryPolicyConfigMapper implements ConfigMapper<RetryPolicy> {

    private static final String PROPERTIES_KEY = "properties";
    private static final String TYPE_KEY = "type";
    private static final String CLASS_KEY = "class";

    private static final String REPEAT_TYPE = "repeat";

    static RetryPolicyConfigMapper instance() {
        return SingletonHolder.INSTANCE;
    }

    @Override
    public RetryPolicy apply(Config config) throws ConfigMappingException, MissingValueException {
        Config properties = config.get(PROPERTIES_KEY) // use properties config node
                .node().orElse(Config.empty()); // or empty config node

        return OptionalHelper.from(config.get(TYPE_KEY).asOptionalString() // `type` is specified
                .flatMap(type -> this.builtin(type, properties))) // return built-in retry policy
                .or(() -> config.get(CLASS_KEY).asOptional(Class.class) // `class` is specified
                        .flatMap(clazz -> custom(clazz, properties))) // return custom retry policy
                .asOptional()
                .orElseThrow(() -> new ConfigMappingException(config.key(), "Uncompleted retry-policy configuration."));
    }

    private Optional<RetryPolicy> builtin(String type, Config properties) {
        final RetryPolicy retryPolicy;
        switch (type) {
        case REPEAT_TYPE:
            retryPolicy = properties.as(RetryPolicies.Builder.class).get();
            break;
        default:
            retryPolicy = null;
        }
        return Optional.ofNullable(retryPolicy);
    }

    private Optional<RetryPolicy> custom(Class<?> clazz, Config properties) {
        final RetryPolicy retryPolicy;
        if (RetryPolicy.class.isAssignableFrom(clazz)) {
            retryPolicy = properties.as((Class<RetryPolicy>) clazz);
        } else {
            retryPolicy = properties.map(ConfigMappers.from(RetryPolicy.class, clazz));
        }
        return Optional.of(retryPolicy);
    }

    /**
     * Singleton holder for {@link RetryPolicyConfigMapper}.
     */
    static class SingletonHolder {
        private static final RetryPolicyConfigMapper INSTANCE = new RetryPolicyConfigMapper();
    }

}
