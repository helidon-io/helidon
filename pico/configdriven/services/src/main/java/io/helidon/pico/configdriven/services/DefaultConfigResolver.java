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

package io.helidon.pico.configdriven.services;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.builder.config.spi.BasicConfigResolver;
import io.helidon.builder.config.spi.ConfigBeanInfo;
import io.helidon.builder.config.spi.ConfigBeanRegistryHolder;
import io.helidon.builder.config.spi.ConfigResolverMapRequest;
import io.helidon.builder.config.spi.ConfigResolverRequest;
import io.helidon.builder.config.spi.MetaConfigBeanInfo;
import io.helidon.builder.config.spi.ResolutionContext;
import io.helidon.builder.config.spi.StringValueParser;
import io.helidon.builder.config.spi.StringValueParserHolder;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.metadata.ConfiguredOption;

import static io.helidon.pico.configdriven.services.Utils.hasValue;
import static io.helidon.pico.configdriven.services.Utils.safeDowncastOf;
import static io.helidon.pico.configdriven.services.Utils.validatedConfigKey;

/**
 * Handles "full" config system presence.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class DefaultConfigResolver extends BasicConfigResolver {

    DefaultConfigResolver() {
    }

    @Override
    public <T> Optional<T> of(
            ResolutionContext ctx,
            Map<String, Map<String, Object>> meta,
            ConfigResolverRequest<T> request) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(request);
        Objects.requireNonNull(meta);
        Class<T> componentType = toComponentType(meta, request).orElse(null);

        // check the config bean registry to see if we know of anything by this key
        List<?> matches = findInConfigBeanRegistryAsList(ctx, meta, request);
        if (!matches.isEmpty()) {
            return optionalWrappedBean(matches.get(0), request.attributeName(), request.valueType(), componentType);
        }

        // check the config sub system
        Optional<T> result = super.of(ctx, meta, request);
        if (result.isPresent()) {
            // assume that the config sub-system did the validation
            return result;
        }

        Map<String, Object> thisMeta = meta.get(request.attributeName());
        return validatedDefaults(thisMeta, request.attributeName(), request.valueType(), componentType);
    }

    @Override
    public <T> Optional<Collection<T>> ofCollection(
            ResolutionContext ctx,
            Map<String, Map<String, Object>> meta,
            ConfigResolverRequest<T> request) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(request);
        Objects.requireNonNull(meta);
        Class<T> componentType = toComponentType(meta, request).orElse(null);

        // check the config bean registry to see if we know of anything by this key
        List<?> matches = findInConfigBeanRegistryAsList(ctx, meta, request);
        if (!matches.isEmpty()) {
            return optionalWrappedBeans(matches, request.attributeName(), request.valueType(), componentType);
        }

        // check the config sub system
        Optional<Collection<T>> result = super.ofCollection(ctx, meta, request);
        if (result.isPresent()) {
            // assume that the config sub-system did the validation
            return result;
        }

        Map<String, Object> thisMeta = meta.get(request.attributeName());
        return validatedDefaults(thisMeta, request.attributeName(), request.valueType(), componentType);
    }

    @Override
    public <K, V> Optional<Map<K, V>> ofMap(
            ResolutionContext ctx,
            Map<String, Map<String, Object>> meta,
            ConfigResolverMapRequest<K, V> request) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(request);
        Objects.requireNonNull(meta);
        Class<V> componentType = toComponentType(meta, request).orElse(null);

        // check the config bean registry to see if we know of anything by this key
        Map<String, V> matches = findInConfigBeanRegistryAsMap(ctx, meta, request);
        if (!matches.isEmpty()) {
            return optionalWrappedBeans(matches, request.attributeName(),
                                        request.keyType(), request.keyComponentType().orElse(null),
                                        request.valueType(), request.valueComponentType().orElse(null));
        }

        // check the config sub system
        Optional<Map<K, V>> result = super.ofMap(ctx, meta, request);
        if (result.isPresent()) {
            // assume that the config sub-system did the validation
            return result;
        }

        Map<String, Object> thisMeta = meta.get(request.attributeName());
        return validatedDefaults(thisMeta, request.attributeName(), request.valueType(), componentType);
    }

    static Optional validatedDefaults(
            Map<String, Object> meta,
            String attrName,
            Class<?> type,
            Class<?> componentType) {
        // check the default values...
        String defaultVal = (String) meta.get("value");
        if (defaultVal != null && defaultVal.equals(ConfiguredOption.UNCONFIGURED)) {
            defaultVal = null;
        }
        Optional result = parse(defaultVal, attrName, type, componentType);
        if (result.isPresent()) {
            return result;
        }

        // check to see if we are in policy violation...
        String requiredStr = (String) meta.get("required");
        boolean required = Boolean.parseBoolean(requiredStr);
        if (required) {
            throw new IllegalStateException("'" + attrName + "' is a required attribute and cannot be null");
        }

        return Optional.empty();
    }

    static <T> Class<T> validatedTypeForConfigBeanRegistry(
            String attrName,
            Class<T> type,
            Class<?> cbType) {
        if (type.isArray()) {
            type = (Class<T>) type.getComponentType();
            if (type == null) {
                throw new ConfigException("? is not supported for " + cbType + "." + attrName);
            }
        }

        if (type.isPrimitive() || type.getName().startsWith("java.lang.")) {
            return null;
        }

        return type;
    }

    static <T> List<T> findInConfigBeanRegistryAsList(
            ResolutionContext ctx,
            Map<String, Map<String, Object>> meta,
            ConfigResolverRequest<T> request) {
        Class<T> type = validatedTypeForConfigBeanRegistry(request.attributeName(),
                                                           request.valueType(),
                                                           request.valueComponentType().orElse(null));
        if (type == null) {
            return List.of();
        }

        DefaultConfigBeanRegistry cbr = (DefaultConfigBeanRegistry) ConfigBeanRegistryHolder.configBeanRegistry().orElseThrow();
        String fullConfigKey = fullConfigKeyOf(safeDowncastOf(ctx.config()), request.configKey(), meta);
        List result = cbr.configBeansByConfigKey(request.configKey(), fullConfigKey);
        return Objects.requireNonNull(result);
    }

    static <V> Map<String, V> findInConfigBeanRegistryAsMap(
            ResolutionContext ctx,
            Map<String, Map<String, Object>> meta,
            ConfigResolverRequest<V> request) {
        Class<?> type = validatedTypeForConfigBeanRegistry(request.attributeName(), request.valueType(), ctx.configBeanType());
        if (type == null) {
            return Map.of();
        }

        DefaultConfigBeanRegistry cbr = (DefaultConfigBeanRegistry) ConfigBeanRegistryHolder.configBeanRegistry().orElseThrow();
        String fullConfigKey = fullConfigKeyOf(safeDowncastOf(ctx.config()), request.configKey(), meta);
        Map<String, V> result = cbr.configBeanMapByConfigKey(request.configKey(), fullConfigKey);
        return Objects.requireNonNull(result);
    }

    static Optional<?> parse(
            String strValueToParse,
            String attrName,
            Class<?> type,
            Class<?> componentType) {
        if (strValueToParse == null) {
            return Optional.empty();
        }

        if (type.isAssignableFrom(strValueToParse.getClass())) {
            return Optional.of(strValueToParse);
        }

        StringValueParser provider = StringValueParserHolder.stringValueParser().orElseThrow();
        if (componentType != null) {
            // best effort here
            try {
                Object val = provider.parse(strValueToParse, componentType);
                return Optional.ofNullable(val);
            } catch (Exception e) {
                if (Optional.class != type) {
                    throw new UnsupportedOperationException("Only Optional<> is currently supported: " + attrName);
                }
            }
        }

        return provider.parse(strValueToParse, type);
    }

    static <T> Optional<T> optionalWrappedBean(
            Object configBean,
            String attrName,
            Class<T> type,
            Class<T> componentType) {
        if (type.isInstance(Objects.requireNonNull(configBean))
                || (Optional.class.equals(type)
                        && (componentType != null) && componentType.isInstance(configBean))) {
            if (Optional.class.equals(type)) {
                return Optional.of((T) Optional.of(configBean));
            }

            return Optional.of((T) configBean);
        }

        throw new UnsupportedOperationException("Cannot convert to type " + componentType + ": " + attrName);
    }

    static <T, V> Optional<Collection<V>> optionalWrappedBeans(
            List<?> configBeans,
            String ignoredAttrName,
            Class<T> ignoredType,
            Class<V> componentType) {
        assert (Objects.nonNull(configBeans) && !configBeans.isEmpty());

        configBeans.forEach(configBean -> {
            assert (componentType.isInstance(configBean));
        });

        return Optional.of((Collection) configBeans);
    }

    static <K, V> Optional<Map<K, V>> optionalWrappedBeans(
            Map<String, V> configBeans,
            String attrName,
            Class<?> keyType,
            Class<?> ignoredKeyComponentType,
            Class<?> type,
            Class<?> componentType) {
        assert (configBeans != null && !configBeans.isEmpty() && (type != null) && componentType != null);
        if (keyType != null && String.class != keyType) {
            throw new UnsupportedOperationException("Only Map with key of String is currently supported: " + attrName);
        }

        configBeans.forEach((key, value) -> {
            assert (componentType.isInstance(value));
        });

        return (Optional) Optional.of(configBeans);
    }

    static String fullConfigKeyOf(
            Config config,
            String configKey,
            Map<String, Map<String, Object>> metaAttributes) {
        assert (hasValue(configKey));
        String parentKey;
        if (config != null) {
            parentKey = config.key().toString();
        } else {
            parentKey = Objects.requireNonNull(configKeyOf(metaAttributes));
        }
        return parentKey + "." + configKey;
    }

    static MetaConfigBeanInfo configBeanInfoOf(
            Map<String, Map<String, Object>> metaAttributes) {
        Map<String, Object> meta = metaAttributes.get(TAG_META);
        if (meta == null) {
            return null;
        }

        return (MetaConfigBeanInfo) meta.get(ConfigBeanInfo.class.getName());
    }

    static String configKeyOf(
            Map<String, Map<String, Object>> metaAttributes) {
        MetaConfigBeanInfo cbi = configBeanInfoOf(metaAttributes);
        return (null == cbi) ? null : validatedConfigKey(cbi);
    }

}
