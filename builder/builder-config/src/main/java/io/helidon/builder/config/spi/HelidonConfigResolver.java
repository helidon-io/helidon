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

package io.helidon.builder.config.spi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.helidon.common.Builder;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigValue;

import jakarta.inject.Singleton;

/**
 * The basic implementation of {@link ConfigResolver} simply resolves against {@link io.helidon.common.config.Config} directly,
 * not "full" Helidon config.
 */
@Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 1)   // allow all other creators to take precedence over us...
@SuppressWarnings({"unchecked", "rawtypes"})
public class HelidonConfigResolver implements ConfigResolver, ConfigResolverProvider {

    /**
     * Tag that represents meta information about the attribute. Used in the maps for various methods herein.
     */
    public static final String TAG_META = "__meta";

    /**
     * Tag that represents the component type.
     */
    protected static final String TAG_COMPONENT_TYPE = "componentType";

    /**
     * Default constructor, service loader invoked.
     *
     * @deprecated needed for Java service loader
     */
    @Deprecated
    public HelidonConfigResolver() {
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
        if (!attrCfg.exists()) {
            return Optional.empty();
        }

        return optionalWrappedConfig(ctx, attrCfg, meta, request);
    }

    @Override
    public <T> Optional<Collection<T>> ofCollection(ResolutionContext ctx,
                                                    Map<String, Map<String, Object>> meta,
                                                    ConfigResolverRequest<T> request) {
        Config attrCfg = ctx.config().get(request.configKey());
        if (!attrCfg.exists()) {
            return Optional.empty();
        }

        return (Optional<Collection<T>>) optionalWrappedConfig(ctx, attrCfg, meta, request);
    }

    @Override
    public <K, V> Optional<Map<K, V>> ofMap(ResolutionContext ctx,
                                            Map<String, Map<String, Object>> meta,
                                            ConfigResolverMapRequest<K, V> request) {
        Config attrCfg = ctx.config().get(request.configKey());
        if (!attrCfg.exists()) {
            return Optional.empty();
        }

        return (Optional<Map<K, V>>) optionalWrappedConfig(ctx, attrCfg, meta, request);
    }

    private <T> Optional<T> optionalWrappedConfig(ResolutionContext ctx,
                                                  Config attrCfg,
                                                  Map<String, Map<String, Object>> meta,
                                                  ConfigResolverRequest<T> request) {
        Class<?> componentType = request.valueComponentType().orElse(null);
        Class<?> type = request.valueType();
        boolean isOptional = Optional.class.equals(type);
        if (isOptional) {
            type = request.valueComponentType().orElseThrow();
        }
        boolean isList = List.class.isAssignableFrom(type);
        boolean isSet = Set.class.isAssignableFrom(type);
        boolean isMap = Map.class.isAssignableFrom(type);

        boolean isCharArray = (char[].class == type);
        if (isCharArray) {
            type = String.class;
        }

        Object val;
        try {
            Function<Config, ?> mapper = (componentType == null) ? null : ctx.mappers().get(componentType);
            if (mapper != null) {
                if (attrCfg.isList() || isMap) {
                    if (!isList && !isSet && !isMap) {
                        throw new IllegalStateException("Unable to convert node list to " + type + " for " + attrCfg);
                    }

                    List<Object> cfgList = new ArrayList<>();
                    Map<String, Object> cfgMap = new LinkedHashMap();
                    List<Config> nodeList = attrCfg.asNodeList().get();
                    for (Config subCfg : nodeList) {
                        Object subVal = Objects.requireNonNull(mapper.apply(subCfg));
                        Builder builder = (Builder) subVal;
                        subVal = builder.build();
                        cfgList.add(subVal);
                        Object prev = cfgMap.put(subCfg.key().name(), subVal);
                        assert (prev == null) : subCfg;
                    }

                    if (isSet) {
                        val = new LinkedHashSet<>(cfgList);
                    } else if (isMap) {
                        val = cfgMap;
                    } else {
                        val = cfgList;
                    }
                } else {
                    val = Objects.requireNonNull(mapper.apply(attrCfg));
                    Builder builder = (Builder) val;
                    val = builder.build();

                    if (isList) {
                        val = List.of(val);
                    } else if (isSet) {
                        Set<Object> set = new LinkedHashSet<>();
                        set.add(val);
                        val = set;
                    }
                }
            } else { // no config bean mapper (i.e., unknown component type)
                ConfigValue<?> attrVal;
                if (isList) {
                    attrVal = attrCfg.asList(componentType);
                    val = attrVal.get();
                } else if (isSet) {
                    attrVal = attrCfg.asList(componentType);
                    val = new LinkedHashSet<>((List<?>) attrVal.get());
                } else if (isMap) {
                    attrVal = attrCfg.asMap();
                    val = attrVal.get();
                } else {
                    attrVal = attrCfg.as(type);
                    val = attrVal.get();
                }
                if (isCharArray) {
                    val = ((String) val).toCharArray();
                }
            }

            if (isOptional) {
                return Optional.of((T) Optional.of(val));
            }

            return Optional.of((T) val);
        } catch (Throwable e) {
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
