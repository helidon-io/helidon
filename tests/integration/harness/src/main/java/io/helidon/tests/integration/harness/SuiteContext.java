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
package io.helidon.tests.integration.harness;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Suite context.
 */
public final class SuiteContext {

    private final String id;
    private final Class<?> suiteClass;
    private final CompletableFuture<Void> future = new CompletableFuture<>();
    private final Map<String, Object> parameters = new HashMap<>();

    /**
     * Create a new instance.
     *
     * @param suiteClass suite class
     * @param suiteId    suite id
     */
    SuiteContext(Class<?> suiteClass, String suiteId) {
        this.suiteClass = suiteClass;
        this.id = suiteId;
    }

    /**
     * Get the suite id.
     *
     * @return suite id
     */
    public String suiteId() {
        return id;
    }

    /**
     * Get the suite class.
     *
     * @return class
     */
    public Class<?> suiteClass() {
        return suiteClass;
    }

    /**
     * Get the future.
     *
     * @return future
     */
    public CompletionStage<Void> future() {
        return future;
    }

    /**
     * Get the suite parameters.
     *
     * @return parameters
     */
    public Map<String, Object> parameters() {
        return parameters;
    }

    /**
     * Get the first parameter that matches the given type.
     *
     * @param paramType parameter type
     * @param <T>       parameter type
     * @return parameter value
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> parameter(Class<T> paramType) {
        return parameters.values()
                .stream()
                .filter(o -> {
                    Class<?> type;
                    if (paramType.isPrimitive()) {
                        type = Array.get(Array.newInstance(paramType, 1), 0).getClass();
                    } else {
                        type = paramType;
                    }
                    return type.isAssignableFrom(o.getClass());
                })
                .map(o -> (T) o)
                .findFirst();
    }
}
