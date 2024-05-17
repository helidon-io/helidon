/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.configurable;

import java.io.InputStream;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.microprofile.config.Config;

/**
 * A lazy read-only {@link Properties} implementation backed by a {@link Config} instance.
 */
final class ConfiguredProperties extends Properties {

    private static final VarHandle ENTRIES;

    static {
        try {
            ENTRIES = MethodHandles.lookup().findVarHandle(ConfiguredProperties.class, "entries", Map.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unused")
    private volatile Map<Object, Object> entries;
    private final Map<Object, String> keys = new HashMap<>();
    private final Config config;

    ConfiguredProperties(Config config) {
        this.config = config;
    }

    void configKey(String propertyName, String configKey) {
        keys.put(propertyName, configKey);
    }

    private Map<Object, Object> entries() {
        Map<Object, Object> entries = this.entries; // volatile read
        if (entries == null) {
            Map<Object, Object> map = new HashMap<>();
            keys.forEach((name, key) -> map.put(name, config.getOptionalValue(key, String.class).orElse(null)));
            entries = Collections.unmodifiableMap(map);
            if (!ENTRIES.compareAndSet(this, null, entries)) { // volatile assignment, maybe
                entries = this.entries; // volatile read; will only happen because this.entries will be non-null
            }
        }
        return entries;
    }

    @Override
    public String getProperty(String name) {
        if (entries().get(name) instanceof String s) {
            return s;
        }
        return null;
    }

    @Override
    public int size() {
        return keys.size();
    }

    @Override
    public boolean isEmpty() {
        return keys.isEmpty();
    }

    @Override
    public Enumeration<Object> keys() {
        return Collections.enumeration(keys.keySet());
    }

    @Override
    public Enumeration<Object> elements() {
        return Collections.enumeration(entries().values());
    }

    @Override
    public boolean contains(Object value) {
        return containsValue(value);
    }

    @Override
    public boolean containsValue(Object value) {
        return entries().containsValue(value);
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof String s) {
            return keys.containsKey(s);
        }
        return false;
    }

    @Override
    public Object get(Object key) {
        if (key instanceof String s) {
            return getProperty(s);
        }
        return null;
    }

    @Override
    public synchronized String toString() {
        return entries().toString();
    }

    @Override
    public Set<Object> keySet() {
        return keys.keySet();
    }

    @Override
    public Collection<Object> values() {
        return entries().values();
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return entries().entrySet();
    }

    @Override
    public synchronized boolean equals(Object o) {
        return entries().equals(o);
    }

    @Override
    public synchronized int hashCode() {
        return entries().hashCode();
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        return entries().getOrDefault(key, defaultValue);
    }

    @Override
    public synchronized void forEach(BiConsumer<? super Object, ? super Object> action) {
        entries().forEach(action);
    }

    @Override
    public synchronized Object setProperty(String key, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void load(Reader reader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void load(InputStream inStream) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void loadFromXML(InputStream in) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object put(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void putAll(Map<?, ?> t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void replaceAll(BiFunction<? super Object, ? super Object, ?> function) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object putIfAbsent(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized boolean replace(Object key, Object oldValue, Object newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object replace(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object computeIfAbsent(Object key, Function<? super Object, ?> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object computeIfPresent(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object compute(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object merge(Object key,
                                     Object value,
                                     BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object clone() {
        throw new UnsupportedOperationException();
    }
}
