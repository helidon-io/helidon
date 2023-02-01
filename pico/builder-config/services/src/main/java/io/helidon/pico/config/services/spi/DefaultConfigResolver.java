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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.pico.config.services.ConfigBeanRegistry;
import io.helidon.pico.config.services.ConfigBeanRegistryProvider;
import io.helidon.pico.config.services.StringValueParser;
import io.helidon.pico.config.services.StringValueParserProvider;
import io.helidon.pico.config.spi.BasicConfigResolver;
import io.helidon.pico.config.spi.ConfigBeanInfo;
import io.helidon.pico.config.spi.MetaConfigBeanInfo;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;

import jakarta.inject.Singleton;

/**
 * Default implementation of {@link ConfigResolver}.
 */
@Singleton
public class DefaultConfigResolver extends BasicConfigResolver {

    @Override
    public <T> Optional<T> of(Config config,
                              String configKey,
                              String attrName,
                              Class<T> type,
                              Class<?> configBeanType,
                              Map<String, Map<String, Object>> metaAttributes) {
        Class<T> componentType = toComponentType(attrName, metaAttributes);

        // check the config bean registry to see if we know of anything by this key...
        List<?> matches = findInConfigBeanRegistryAsList(
                config, configKey, attrName, type, configBeanType, metaAttributes);
        if (!matches.isEmpty()) {
            return optionalWrappedBean(matches.get(0), attrName, type, componentType);
        }

        // check the config sub system...
        Optional<T> result = super.of(config, configKey, attrName, type, configBeanType, metaAttributes);
        if (result.isPresent()) {
            // assume that the config sub-system did the validation
            return result;
        }

        Map<String, Object> meta = Objects.isNull(metaAttributes) ? null : metaAttributes.get(attrName);
        return validatedDefaults(meta, attrName, type, componentType);
    }

    @Override
    public <T, V> Optional<Collection<V>> ofCollection(Config config,
                                                       String configKey,
                                                       String attrName,
                                                       Class<T> type,
                                                       Class<V> componentType,
                                                       Class<?> configBeanType,
                                                       Map<String, Map<String, Object>> metaAttributes) {
        // check the config bean registry to see if we know of anything by this key...
        List<?> matches = findInConfigBeanRegistryAsList(
                config, configKey, attrName, type, configBeanType, metaAttributes);
        if (!matches.isEmpty()) {
            return optionalWrappedBeans(matches, attrName, type, componentType);
        }

        // check the config sub system...
        Optional<Collection<V>> result = super
                .ofCollection(config, configKey, attrName, type, componentType, configBeanType, metaAttributes);
        if (result.isPresent()) {
            // assume that the config sub-system did the validation
            return result;
        }

        Map<String, Object> meta = Objects.isNull(metaAttributes) ? null : metaAttributes.get(attrName);
        return validatedDefaults(meta, attrName, type, componentType);
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
        // check the config bean registry to see if we know of anything by this key...
        Map<String, V> matches = findInConfigBeanRegistryAsMap(
                config, configKey, attrName, type, componentType, configBeanType, metaAttributes);
        if (!matches.isEmpty()) {
            return optionalWrappedBeans(matches, attrName, keyType, keyComponentType, type, componentType);
        }

        // check the config sub system...
        // check the config sub system...
        Optional<Map<K, V>> result = super
                .ofMap(config, configKey, attrName, keyType, keyComponentType, type, componentType,
                       configBeanType, metaAttributes);
        if (result.isPresent()) {
            // assume that the config sub-system did the validation
            return result;
        }

        Map<String, Object> meta = Objects.isNull(metaAttributes) ? null : metaAttributes.get(attrName);
        return validatedDefaults(meta, attrName, type, componentType);
    }

    @Override
    protected String toTypeNameDescription(Class<?> type, Class<?> componentType) {
        DefaultTypeName typeName = DefaultTypeName.create(type);
        if (Objects.isNull(typeName)) {
            return null;
        }

        TypeName componentTypeName = DefaultTypeName.create(componentType);
        if (Objects.nonNull(componentTypeName)) {
            typeName = typeName.toBuilder().typeArguments(Collections.singletonList(componentTypeName)).build();
        }
        return typeName.toString();
    }

    private Optional validatedDefaults(Map<String, Object> meta,
                                       String attrName,
                                       Class<?> type,
                                       Class<?> componentType) {
        // check the default values...
        String defaultVal = Objects.isNull(meta) ? null : (String) meta.get("value");
        Optional result = parse(defaultVal, attrName, type, componentType);
        if (result.isPresent()) {
            return result;
        }

        // check to see if we are in policy violation...
        String requiredStr = Objects.isNull(meta) ? "false" : (String) meta.get("required");
        boolean required = Boolean.parseBoolean(requiredStr);
        if (required) {
            throw new IllegalStateException("'" + attrName + "' is a required attribute and cannot be null");
        }

        return Optional.empty();
    }

    static <T, V> Class<T> validatedTypeForConfigBeanRegistry(String attrName,
                                                              Class<T> type,
                                                              Class<V> ignoredComponentType,
                                                              Class<?> cbType) {
        if (type.isArray()) {
            type = (Class<T>) type.getComponentType();
            if (Objects.isNull(type)) {
                throw new UnsupportedOperationException("? is not supported for " + cbType + "." + attrName);
            }
        }

        if (type.isPrimitive() || type.getName().startsWith("java.lang.")) {
            return null;
        }

        return type;
    }

    static <T> List<T> findInConfigBeanRegistryAsList(Config config,
                                                      String configKey,
                                                      String attrName,
                                                      Class<T> type,
                                                      Class<?> metaConfigBeanType,
                                                      Map<String, Map<String, Object>> metaAttributes) {
        type = validatedTypeForConfigBeanRegistry(attrName, type, null, metaConfigBeanType);
        if (Objects.isNull(type)) {
            return Collections.emptyList();
        }

        ConfigBeanRegistry cbr = Objects.requireNonNull(ConfigBeanRegistryProvider.getInstance());
        String fullConfigKey = fullConfigKeyOf(config, configKey, metaConfigBeanType, metaAttributes);
        List<T> result = cbr.getConfigBeansByConfigKey(configKey, fullConfigKey);
        return Objects.nonNull(result) ? result : Collections.emptyList();
    }

    static <T, V> Map<String, V> findInConfigBeanRegistryAsMap(Config config,
                                                               String configKey,
                                                               String attrName,
                                                               Class<T> type,
                                                               Class<V> componentType,
                                                               Class<?> metaConfigBeanType,
                                                               Map<String, Map<String, Object>> metaAttributes) {
        type = validatedTypeForConfigBeanRegistry(attrName, type, componentType, metaConfigBeanType);
        if (Objects.isNull(type)) {
            return Collections.emptyMap();
        }

        ConfigBeanRegistry cbr = Objects.requireNonNull(ConfigBeanRegistryProvider.getInstance());
        String fullConfigKey = fullConfigKeyOf(config, configKey, metaConfigBeanType, metaAttributes);
        Map<String, V> result = cbr.getConfigBeanMapByConfigKey(configKey, fullConfigKey);
        return Objects.nonNull(result) ? result : Collections.emptyMap();
    }

    static Optional<?> parse(String strValueToParse, String attrName, Class<?> type, Class<?> componentType) {
        if (Objects.isNull(strValueToParse)) {
            return Optional.empty();
        }

        if (type.isAssignableFrom(strValueToParse.getClass())) {
            return Optional.of(strValueToParse);
        }

        StringValueParser provider = Objects.requireNonNull(StringValueParserProvider.getInstance());

        if (Objects.nonNull(componentType)) {
            // best effort here...
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

    static <T> Optional<T> optionalWrappedBean(Object configBean,
                                               String attrName,
                                               Class<T> type,
                                               Class<T> componentType) {
        if (type.isInstance(Objects.requireNonNull(configBean))
                || (
                Optional.class.equals(type)
                        && Objects.nonNull(componentType) && componentType.isInstance(configBean))) {
            if (Optional.class.equals(type)) {
                return Optional.of((T) Optional.of(configBean));
            }

            return Optional.of((T) configBean);
        }

        throw new UnsupportedOperationException("Cannot convert to type " + componentType + ": " + attrName);
    }

    static <T, V> Optional<Collection<V>> optionalWrappedBeans(List<?> configBeans,
                                                               String ignoredAttrName,
                                                               Class<T> ignoredType,
                                                               Class<V> componentType) {
        assert (Objects.nonNull(configBeans) && !configBeans.isEmpty());

        configBeans.forEach(configBean -> {
            assert (componentType.isInstance(configBean));
        });

        return Optional.of((Collection) configBeans);
    }

    static <K, T, V> Optional<Map<K, V>> optionalWrappedBeans(Map<String, V> configBeans,
                                                              String attrName,
                                                              Class<K> keyType,
                                                              Class<?> ignoredKeyComponentType,
                                                              Class<T> type,
                                                              Class<V> componentType) {
        assert (
                Objects.nonNull(configBeans) && !configBeans.isEmpty()
                        && Objects.nonNull(type) && Objects.nonNull(componentType));

        if (Objects.nonNull(keyType) && String.class != keyType) {
            throw new UnsupportedOperationException("Only Map with key of String is currently supported: " + attrName);
        }

        configBeans.forEach((key, value) -> {
            assert (componentType.isInstance(value));
        });

        return (Optional) Optional.of(configBeans);
    }

    static String fullConfigKeyOf(Config config,
                                  String configKey,
                                  Class<?> ignoredMetaConfigBeanType,
                                  Map<String, Map<String, Object>> metaAttributes) {
        assert (AnnotationAndValue.hasNonBlankValue(configKey));
        String parentKey;
        if (Objects.nonNull(config)) {
            parentKey = config.key().toString();
        } else {
            parentKey = Objects.requireNonNull(configKeyOf(metaAttributes));
        }
        return parentKey + "." + configKey;
    }

    static MetaConfigBeanInfo configBeanInfoOf(Map<String, Map<String, Object>> metaAttributes) {
        if (Objects.isNull(metaAttributes)) {
            return null;
        }

        Map<String, Object> meta = metaAttributes.get(TAG_META);
        if (Objects.isNull(meta)) {
            return null;
        }

        return (MetaConfigBeanInfo) meta.get(ConfigBeanInfo.class.getName());
    }

    static String configKeyOf(Map<String, Map<String, Object>> metaAttributes) {
        MetaConfigBeanInfo cbi = configBeanInfoOf(metaAttributes);
        return (Objects.isNull(cbi)) ? null : cbi.validatedConfigKey();
    }

}
