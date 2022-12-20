/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.config;

import java.util.Arrays;

/**
 * Indicates an error attempting to map a string config value to a specified enum type.
 */
public class ConfigEnumMappingException extends ConfigMappingException {

    private static final long serialVersionUID = -3412301638125266690L;

    /**
     * Create a new config enum mapping exception.
     *
     * @param key       config key for the value which cannot be mapped
     * @param detail    detailed information of the failure (no match, ambiguous, etc.)
     * @param enumType  type of the {@code Enum} to which mapping was attempted
     * @param value     {@code String} config value for which the mapping was attempted
     */
    public ConfigEnumMappingException(Config.Key key, String detail, Class<Enum<?>> enumType, String value) {
        super(key, enumType, String.format("cannot map value '%s' to enum values %s: %s",
                                           value,
                                           Arrays.asList(enumType.getEnumConstants()),
                                           detail));
    }
}
