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

package io.helidon.pico.config.services;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;
import io.helidon.common.config.ConfigValue;
import io.helidon.pico.builder.config.spi.ConfigResolver;
import io.helidon.pico.builder.config.spi.ConfigResolverMapRequest;
import io.helidon.pico.builder.config.spi.ConfigResolverRequest;
import io.helidon.pico.builder.config.spi.ResolutionContext;

/**
 * Basic implementation of {@link io.helidon.pico.builder.config.spi.ConfigResolverProvider} that just resolves against
 * {@link io.helidon.common.config.Config} directly, instead of "full" {@link io.helidon.config.Config}.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class BasicConfigResolver implements ConfigResolver {

    /**
     * Tag that represents meta information about the attribute. Used in the maps for various methods herein.
     */
    static final String TAG_META = "__meta";

    /**
     * Tag that represents the component type.
     */
    static final String TAG_COMPONENT_TYPE = "componentType";

    BasicConfigResolver() {
    }

    @Override
    public <T> Optional<T> of(
            ResolutionContext ctx,
            Map<String, Map<String, Object>> meta,
            ConfigResolverRequest<T> request) {
        return get(ctx, meta, request);
    }

    @Override
    public <T> Optional<Collection<T>> ofCollection(
            ResolutionContext ctx,
            Map<String, Map<String, Object>> meta,
            ConfigResolverRequest<T> request) {
        return (Optional<Collection<T>>) get(ctx, meta, request);
    }

    @Override
    public <K, V> Optional<Map<K, V>> ofMap(
            ResolutionContext ctx,
            Map<String, Map<String, Object>> meta,
            ConfigResolverMapRequest<K, V> request) {
        return (Optional<Map<K, V>>) get(ctx, meta, request);
    }

    /**
     * Extracts the component type from the meta attributes provided for a particular bean attribute name.
     */
    static <T> Class<T> toComponentType(
            Map<String, Map<String, Object>> meta,
            String attrName) {
        Map<String, Object> theMeta = (meta == null) ? null : meta.get(attrName);
        return (theMeta == null) ? null : (Class<T>) theMeta.get(TAG_COMPONENT_TYPE);
    }

    /**
     * Uses the config to get a config key provided its type and optionally its component/generic type parameter.
     * Handles everything except {@code Map} types.
     */
    <T> Optional<T> get(
            ResolutionContext ctx,
            Map<String, Map<String, Object>> meta,
            ConfigResolverRequest<T> request) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(meta);
        Objects.requireNonNull(request);
        Config config = ctx.config();
        Config attrCfg = (config == null) ? null : config.get(request.configKey());
        if (attrCfg != null && attrCfg.exists()) {
            Optional result = optionalWrappedConfig(attrCfg, request);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * Create an optionally wrapped value where the value is sourced from the underlying config sub-system.
     */
    Optional<?> optionalWrappedConfig(
            Config attrCfg,
            ConfigResolverRequest<?> request) {
        boolean isOptional = Optional.class.equals(request.valueType());
        Class<?> type = request.valueType();
        if (isOptional) {
            type = request.valueComponentType().orElseThrow();
        }
        boolean isCharArray = (type.isArray() && char.class == type.getComponentType());
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
            String typeName = toTypeNameDescription(request);
            String configKey = attrCfg.key().toString();
            throw new ConfigException("Failed to convert " + typeName
                                              + " for attribute: " + request.attributeName() + " and config key: " + configKey);
        }
    }

    /**
     * Provide a better description for the generic type.
     */
    String toTypeNameDescription(
            ConfigResolverRequest<?> request) {
        String typeName = Objects.requireNonNull(request.valueType()).getTypeName();
        Class<?> componentType = request.valueComponentType().orElse(null);

        if (componentType != null) {
            return typeName + "<" + componentType.getTypeName() + ">";
        }

        return typeName;
    }

}
