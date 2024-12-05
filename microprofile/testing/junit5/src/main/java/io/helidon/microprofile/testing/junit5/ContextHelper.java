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
package io.helidon.microprofile.testing.junit5;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

/**
 * JUnit context helper.
 */
class ContextHelper {

    private static final Namespace NAMESPACE = Namespace.create(HelidonJunitExtension.class);

    private ContextHelper() {
    }

    /**
     * Get a store.
     *
     * @param context   context
     * @param methods methods
     * @return store
     */
    static ExtensionContext.Store store(ExtensionContext context, Method... methods) {
        Namespace ns;
        if (methods.length > 0) {
            ns = NAMESPACE.append(Arrays.stream(methods)
                    .map(Method::getName)
                    .toArray());
        } else {
            ns = NAMESPACE;
        }
        return context.getStore(ns);
    }

    /**
     * Get an object from the given store.
     *
     * @param store store
     * @param type  type
     * @param name  name
     * @param <T>   object type
     * @return optional
     */
    static <T> Optional<T> lookup(ExtensionContext.Store store, Class<T> type, String name) {
        return Optional.ofNullable(store.get(name, type));
    }

    /**
     * Get the class context for a given context.
     *
     * @param context context
     * @return class context
     */
    static ExtensionContext classContext(ExtensionContext context) {
        ExtensionContext c = context;
        while (!c.getElement().map(Class.class::isInstance).orElse(false)) {
            c = c.getParent().orElseThrow();
        }
        return c;
    }
}
