/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.mp;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigValue;
import io.helidon.config.ConfigValues;
import io.helidon.config.MissingValueException;
import io.helidon.config.spi.ConfigMapper;

/**
 * Implementation of SE config backed by MP config.
 */
class SeConfig implements Config {
    private static final Pattern SPLIT_PATTERN = Pattern.compile("(?<!\\\\),");
    private static final Pattern ESCAPED_COMMA_PATTERN = Pattern.compile("\\,", Pattern.LITERAL);

    private final Map<Key, SeConfig> children = new ConcurrentHashMap<>();
    private final io.helidon.config.Config mapper;
    private final Key prefix;
    private final Key key;
    private final Key fullKey;
    private final org.eclipse.microprofile.config.Config delegate;
    private final MpConfigImpl delegateImpl;
    private final String stringKey;
    private final String stringPrefix;

    SeConfig(io.helidon.config.Config mapper,
             Key prefix,
             Key key,
             Key fullKey,
             org.eclipse.microprofile.config.Config delegate,
             MpConfigImpl delegateImpl) {
        this.mapper = mapper;
        this.prefix = prefix;
        this.key = key;
        this.fullKey = fullKey;
        this.delegate = delegate;
        this.stringKey = fullKey.toString();
        this.stringPrefix = prefix.toString();
        this.delegateImpl = delegateImpl;
    }

    SeConfig(Config mapper,
             org.eclipse.microprofile.config.Config delegate) {
        this.mapper = mapper;
        this.prefix = Key.create("");
        this.key = prefix;
        this.fullKey = prefix;
        this.delegate = delegate;
        this.stringKey = prefix.child(key).toString();
        this.stringPrefix = prefix.toString();

        if (delegate instanceof MpConfigImpl) {
            this.delegateImpl = (MpConfigImpl) delegate;
        } else {
            this.delegateImpl = null;
        }
    }

    @Override
    public Instant timestamp() {
        return Instant.now();
    }

    @Override
    public Key key() {
        return key;
    }

    @Override
    public Config get(Key key) {
        return children.computeIfAbsent(key,
                                        it -> new SeConfig(mapper,
                                                           prefix,
                                                           key,
                                                           fullKey.child(key),
                                                           delegate,
                                                           delegateImpl));
    }

    @Override
    public Config detach() {
        return new SeConfig(mapper, fullKey, Key.create(""), fullKey, delegate, delegateImpl);
    }

    @Override
    public Type type() {
        // check if there are any sub-nodes that have prefix with our key
        boolean isObject = false;

        Iterator<String> it = delegate.getPropertyNames().iterator();
        if (stringKey.isEmpty()) {
            if (!it.hasNext()) {
                return hasValue() ? Type.VALUE : Type.MISSING;
            }
            return Type.OBJECT;
        }

        while (it.hasNext()) {
            String name = it.next();
            if (name.equals(stringKey)) {
                continue;
            }
            if (name.startsWith(stringKey + ".")) {
                isObject = true;
                break;
            }
        }
        if (isObject) {
            return Type.OBJECT;
        }
        if (hasValue()) {
            return Type.VALUE;
        }

        return Type.MISSING;
    }

    @Override
    public boolean hasValue() {
        return currentValue().isPresent();
    }

    @Override
    public Stream<Config> traverse(Predicate<Config> predicate) {
        return asNodeList()
                .map(list -> list.stream()
                        .filter(predicate)
                        .map(node -> traverseSubNodes(node, predicate))
                        .reduce(Stream.empty(), Stream::concat))
                .orElseThrow(MissingValueException.createSupplier(key()));

    }

    @Override
    public <T> T convert(Class<T> type, String value) throws ConfigMappingException {
        try {
            return impl().obtainConverter(type)
                    .convert(value);
        } catch (Exception e) {
            try {
                return mapper.convert(type, value);
            } catch (ConfigMappingException ignored) {
                throw e;
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ConfigValue<T> as(GenericType<T> genericType) {
        if (genericType.isClass()) {
            return (ConfigValue<T>) as(genericType.rawType());
        }

        return new SeConfigValue<>(key, () -> mapper.mapper().map(SeConfig.this, genericType));
    }

    @Override
    public <T> ConfigValue<T> as(Class<T> type) {
        if (type() == Type.MISSING) {
            return ConfigValues.empty();
        }
        if (impl().getConverter(type).isPresent()) {
            return delegate.getOptionalValue(stringKey, type)
                    .map(ConfigValues::simpleValue)
                    .orElseGet(ConfigValues::empty);
        } else {
            return new SeConfigValue<>(key, () -> mapper.mapper().map(SeConfig.this, type));
        }
    }

    @Override
    public <T> ConfigValue<T> as(Function<Config, T> mapper) {
        if (type() == Type.MISSING) {
            return ConfigValues.empty();
        }

        return ConfigValues.simpleValue(mapper.apply(this));
    }

    @Override
    public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigMappingException {
        if (type() == Type.MISSING) {
            return ConfigValues.empty();
        }
        if (Config.class.equals(type)) {
            return toNodeList();
        }
        return asList(stringKey, type);
    }

    @Override
    public <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException {
        if (type() == Type.MISSING) {
            return ConfigValues.empty();
        }
        return asNodeList()
                .as(it -> it.stream()
                        .map(mapper)
                        .collect(Collectors.toList()));
    }

    @Override
    public ConfigValue<List<Config>> asNodeList() throws ConfigMappingException {
        if (type() == Type.MISSING) {
            return ConfigValues.empty();
        }
        return asList(Config.class);
    }

    @Override
    public ConfigValue<Map<String, String>> asMap() throws MissingValueException {
        Type nodeType = type();
        if (nodeType == Type.MISSING || nodeType == Type.VALUE) {
            return ConfigValues.empty();
        }

        Map<String, String> children = new HashMap<>();

        for (String propertyName : delegate.getPropertyNames()) {
            if (stringKey.isEmpty()) {
                children.put(propertyName, delegate.getValue(propertyName, String.class));
            } else {
                if (propertyName.equals(stringKey)) {
                    continue;
                }
                if (propertyName.startsWith(stringKey + ".")) {
                    String noPrefix = propertyName.substring(stringPrefix.length() + 1);

                    children.put(noPrefix, delegate.getValue(propertyName, String.class));
                }
            }
        }

        return ConfigValues.simpleValue(children);
    }

    @Override
    public String toString() {
        return type() + " " + stringKey + " = " + currentValue().orElse(null);
    }

    @Override
    public ConfigMapper mapper() {
        return mapper.mapper();
    }

    private Stream<Config> traverseSubNodes(Config config, Predicate<Config> predicate) {
        if (type() == Type.MISSING) {
            return Stream.of();
        }
        if (config.type().isLeaf()) {
            return Stream.of(config);
        } else {
            return config.asNodeList()
                    .map(list -> list.stream()
                            .filter(predicate)
                            .map(node -> traverseSubNodes(node, predicate))
                            .reduce(Stream.of(config), Stream::concat))
                    .orElseThrow(MissingValueException.createSupplier(key()));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ConfigValue<List<T>> toNodeList() {
        Type nodeType = type();
        if (nodeType == Type.MISSING || nodeType == Type.VALUE) {
            return ConfigValues.empty();
        }

        // this is an object or a list
        List<T> result = new LinkedList<>();
        Set<String> children = new HashSet<>();

        for (String propertyName : delegate.getPropertyNames()) {
            if (stringKey.isEmpty()) {
                String noSuffix = propertyName;
                int dot = noSuffix.indexOf('.');
                if (dot > 0) {
                    noSuffix = noSuffix.substring(0, dot);
                }
                children.add(noSuffix);
            } else {
                if (propertyName.equals(stringKey)) {
                    continue;
                }
                if (propertyName.startsWith(stringKey + ".")) {
                    String noSuffix = propertyName.substring(stringKey.length() + 1);
                    int dot = noSuffix.indexOf('.');
                    if (dot > 0) {
                        noSuffix = noSuffix.substring(0, dot);
                    }
                    children.add(noSuffix);
                }
            }
        }

        for (String child : children) {
            result.add((T) get(child));
        }

        return ConfigValues.simpleValue(result);
    }

    private Optional<String> currentValue() {
        return delegate.getOptionalValue(stringKey, String.class);
    }

    private MpConfigImpl impl() {
        if (null == delegateImpl) {
            throw new IllegalStateException("Cannot convert to arbitrary types when the MP Config is not a Helidon "
                                                    + "implementation");
        }

        return delegateImpl;
    }

    private <T> ConfigValue<List<T>> asList(String configKey, Class<T> typeArg) {
        return new SeConfigValue<>(key(), () -> toList(configKey, typeArg));
    }

    private <T> List<T> toList(String configKey, Class<T> typeArg) {
        // first try to see if we have a direct value
        Optional<String> optionalValue = delegate.getOptionalValue(configKey, String.class);
        if (optionalValue.isPresent()) {
            return valueToList(configKey, optionalValue.get(), typeArg);
        }

        /*
         we also support indexed value
         e.g. for key "my.list" you can have both:
         my.list=12,13,14
         or (not and):
         my.list.0=12
         my.list.1=13
         */

        String indexedConfigKey = configKey + ".0";
        optionalValue = delegate.getOptionalValue(indexedConfigKey, String.class);
        if (optionalValue.isPresent()) {
            List<T> result = new LinkedList<>();

            // first element is already in
            result.add(convert(indexedConfigKey, optionalValue.get(), typeArg));

            // start from index 1, as 0 is already aded
            int i = 1;
            while (true) {
                indexedConfigKey = configKey + "." + i;
                optionalValue = delegate.getOptionalValue(indexedConfigKey, String.class);
                if (optionalValue.isPresent()) {
                    result.add(convert(indexedConfigKey, optionalValue.get(), typeArg));
                } else {
                    // finish the iteration on first missing index
                    break;
                }
                i++;
            }
            return result;
        } else {
            // and further still we may have a list of objects
            if (get("0").type() == Type.MISSING) {
                throw MissingValueException.create(key);
            }
            // there are objects here, let's do that
            List<T> result = new LinkedList<>();

            int i = 0;
            while (true) {
                Config config = get(String.valueOf(i));
                if (config.type() == Type.MISSING) {
                    break;
                }
                result.add(config.as(typeArg).get());
                i++;
            }
            return result;
        }
    }

    private <T> List<T> valueToList(String configKey,
                                    String stringValue,
                                    Class<T> typeArg) {
        if (stringValue.isEmpty()) {
            return List.of();
        }
        // we have a comma separated list
        List<T> result = new LinkedList<>();
        for (String value : toArray(stringValue)) {
            result.add(convert(configKey, value, typeArg));
        }
        return result;
    }

    static String[] toArray(String stringValue) {
        String[] values = SPLIT_PATTERN.split(stringValue, -1);

        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            values[i] = ESCAPED_COMMA_PATTERN.matcher(value).replaceAll(Matcher.quoteReplacement(","));
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private <T> T convert(String key, String value, Class<T> type) {
        if (null == value) {
            return null;
        }
        if (String.class.equals(type)) {
            return (T) value;
        }

        try {
            return impl().getConverter(type)
                    .orElseThrow(() -> new IllegalArgumentException("Did not find converter for type "
                                                                            + type.getName()
                                                                            + ", for key "
                                                                            + key))
                    .convert(value);
        } catch (Exception e) {
            try {
                return mapper.convert(type, value);
            } catch (ConfigMappingException ignored) {
                throw e;
            }
        }
    }
}
