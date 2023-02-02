/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigValue;

import jakarta.inject.Singleton;

/**
 * The default implementation of {@link ConfigResolver} simply resolves against {@link io.helidon.common.config.Config} directly.
 */
@Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 1)   // allow all other creators to take precedence over us...
public class DefaultConfigResolver implements ConfigResolver, ConfigResolverProvider {

    /**
     * Tag that represents meta information about the attribute. Used in the maps for various methods herein.
     */
    public static final String TAG_META = "__meta";

    /**
     * Default constructor, service loader invoked.
     */
    @Deprecated
    public DefaultConfigResolver() {
    }

    @Override
    public ConfigResolver configResolver() {
        return this;
    }

    @Override
    public <T> Optional<T> of(ResolutionContext ctx,
                              Map<String, Map<String, Object>> meta,
                              ConfigResolverRequest<T> request) {
        Config attrCfg = ctx.config().get(request.configKey());
        return attrCfg.exists()
                ? optionalWrappedConfig(attrCfg, meta, request) : Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<Collection<T>> ofCollection(ResolutionContext ctx,
                                                    Map<String, Map<String, Object>> meta,
                                                    ConfigResolverRequest<T> request) {
        Config attrCfg = ctx.config().get(request.configKey());
        return attrCfg.exists()
                ? (Optional<Collection<T>>) optionalWrappedConfig(attrCfg, meta, request) : Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Optional<Map<K, V>> ofMap(ResolutionContext ctx,
                                            Map<String, Map<String, Object>> meta,
                                            ConfigResolverMapRequest<K, V> request) {
        Config attrCfg = ctx.config().get(request.configKey());
        return attrCfg.exists()
                ? (Optional<Map<K, V>>) optionalWrappedConfig(attrCfg, meta, request) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> optionalWrappedConfig(Config attrCfg,
                                                  Map<String, Map<String, Object>> meta,
                                                  ConfigResolverRequest<T> request) {
        Class<?> componentType = request.valueComponentType().orElse(null);
        Class<?> type = request.valueType();
        final boolean isOptional = Optional.class.equals(type);
        if (isOptional) {
            type = request.valueComponentType().orElseThrow();
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

            return Optional.of(isOptional ? (T) Optional.of(val) : (T) val);
        } catch (Exception e) {
            String typeName = toTypeNameDescription(request.valueType(), componentType);
            String configKey = attrCfg.key().toString();
            throw new IllegalStateException("Failed to convert " + typeName
                                              + " for attribute: " + request.attributeName()
                                                    + " and config key: " + configKey, e);
        }
    }

    private static String toTypeNameDescription(Class<?> type,
                                         Class<?> componentType) {
        return type.getTypeName() + "<" + componentType.getTypeName() + ">";
    }

}
