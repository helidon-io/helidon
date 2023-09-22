/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link Suite} shared context storage mapped to jUnit 5 {@code GLOBAL} storage.
 */
public class SuiteJunit5Storage implements SuiteContext.Storage {

    // jUnit 5 GLOBAL storage
    private final ExtensionContext.Store store;

    SuiteJunit5Storage(ExtensionContext.Store store) {
        this.store = store;
    }

    @Override
    public <T> T get(String key, Class<T> cls) {
        return store.get(key, cls);
    }

    @Override
    public <T> T getOrDefault(String key, Class<T> cls, T defaultValue) {
        return store.getOrDefault(key, cls, defaultValue);
    }

    @Override
    public void putOrReplace(String key, Object value) {
        if (store.get(key) != null) {
            store.remove(key);
        }
        store.put(key, value);
    }

    @Override
    public void put(String key, Object value) {
        store.put(key, value);
    }

}
