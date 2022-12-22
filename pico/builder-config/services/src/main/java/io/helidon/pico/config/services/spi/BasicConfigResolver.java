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

package io.helidon.pico.builder.config.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;
import io.helidon.common.config.ConfigValue;

import jakarta.inject.Singleton;

/**
 * Basic implementation of {@link io.helidon.pico.builder.config.spi.ConfigResolver} that just resolves against
 * {@link io.helidon.common.config.Config} directly.
 */
@Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 1)
public class BasicConfigResolver implements io.helidon.pico.builder.config.spi.ConfigResolver {

    /**
     * Tag that represents meta information about the attribute. Used in the maps for various methods herein.
     */
    public static final String TAG_META = "__meta";

    /**
     * Tag that represents the component type.
     */
    protected static final String TAG_COMPONENT_TYPE = "componentType";

    @Override
    public <T> Optional<T> of(Config config,
                              String configKey,
                              String attrName,
                              Class<T> type,
                              Class<?> configBeanType,
                              Map<String, Map<String, Object>> metaAttributes) {
        if (Objects.isNull(config)) {
            return Optional.empty();
        }

        return get(config, configKey, attrName, type, toComponentType(attrName, metaAttributes));
    }

    @Override
    public <T, V> Optional<Collection<V>> ofCollection(Config config,
                                                       String configKey,
                                                       String attrName,
                                                       Class<T> type,
                                                       Class<V> componentType,
                                                       Class<?> configBeanType,
                                                       Map<String, Map<String, Object>> metaAttributes) {
        return (Optional<Collection<V>>) get(config, configKey, attrName, type, componentType);
    }

    @Override
    public <K, T, V> Optional<Map<K, V>> ofMap(Config config,
                                               String configKey,
                                               String attrName,
                                               Class<K> keyType,
                                               Class<?> keyComponentType,
                                               Class<T> type,
                                               Class<V> componentType,
                                               Class<?> configBeanType,
                                               Map<String, Map<String, Object>> metaAttributes) {
        return (Optional<Map<K, V>>) get(config, configKey, attrName, type, componentType);
    }

    /**
     * Extracts the component type from the meta attributes provided for a particular bean attribute name.
     *
     * @param attrName          the attribute name (of the bean method)
     * @param metaAttributes    the meta attributes
     * @return the component type
     * @param <T> the component type
     */
    public static <T> Class<T> toComponentType(String attrName,
                                               Map<String, Map<String, Object>> metaAttributes) {
        Map<String, Object> meta = Objects.isNull(metaAttributes) ? null : metaAttributes.get(attrName);
        return Objects.isNull(meta) ? null : (Class<T>) meta.get(TAG_COMPONENT_TYPE);
    }

    /**
     * Uses the config to get a config key provided its type and optionally its component/generic type parameter.
     *
     * @param config        the config
     * @param configKey     the config key
     * @param attrName      the attribute name (of the bean method)
     * @param type          the attribute type (of the bean method)
     * @param componentType the attribute component type argument of the attribute type
     * @return              the optional config value
     * @param <T>           the attribute type
     * @param <V>           the attribute component type
     */
    public <T, V> Optional<T> get(Config config,
                                      String configKey,
                                      String attrName,
                                      Class<T> type,
                                      Class<V> componentType) {
        Config attrCfg = Objects.isNull(config) ? null : config.get(Objects.requireNonNull(configKey));
        if (Objects.nonNull(attrCfg) && attrCfg.exists()) {
            Optional result = optionalWrappedConfig(attrCfg, attrName, type, componentType);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * Create an optionally wrapped value where the value is sourced from the underlying config sub-system.
     *
     * @param attrCfg   the
     * @param attrName
     * @param origType
     * @param componentType
     * @return
     */
    protected Optional<?> optionalWrappedConfig(Config attrCfg,
                                             String attrName,
                                             Class<?> origType,
                                             Class<?> componentType) {
        final boolean isOptional = (Optional.class.equals(origType));
        final boolean ignoredIsList = (List.class.equals(origType));
        final boolean ignoredIsSet = (Set.class.equals(origType));
        Class<?> type = origType;
        if (isOptional) {
            type = Objects.requireNonNull(componentType);
        }
        final boolean isCharArray = (type.isArray() && char.class == type.getComponentType());
        if (isCharArray) {
            type = String.class;
        }

        try {
            ConfigValue<?> attrVal = attrCfg.as(type);
            Object val = attrVal.get();
            if (isCharArray) {
                val = ((String) val).toCharArray();
            }

            return Optional.of(isOptional ? Optional.of(val) : val);
        } catch (Exception e) {
            String typeName = toTypeNameDescription(origType, componentType);
            String configKey = attrCfg.key().toString();
            throw new ConfigException("Failed to convert " + typeName
                                              + " for attribute: " + attrName + " and config key: " + configKey);
        }
    }

    /**
     * Provide a better description for the generic type.
     *
     * @param type          the type
     * @param componentType the optional component type
     * @return the description (e.g., Type<ComponentType>)
     */
    protected String toTypeNameDescription(Class<?> type,
                                           Class<?> componentType) {
        if (Objects.isNull(type)) {
            return null;
        }

        if (Objects.nonNull(componentType)) {
            return type.getTypeName() + "<" + componentType.getTypeName() + ">";
        }

        return type.getTypeName();
    }

}
