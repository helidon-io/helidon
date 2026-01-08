/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json.binding;

import io.helidon.common.GenericType;

/**
 * A common abstraction used by both serializers and deserializers.
 *
 * @param <T> type supported by this component
 */
public interface JsonComponent<T> {
    /**
     * Return the type this component handles.
     *
     * @return the GenericType representing the type T
     */
    GenericType<T> type();

    /**
     * Configures this component with the provided configurator.
     * <p>
     * This method allows the component to perform
     * any necessary setup during the JSON binding configuration process.
     * The default implementation does nothing.
     * </p>
     *
     * @param jsonBindingConfigurator the configurator to use for setup
     */
    default void configure(JsonBindingConfigurator jsonBindingConfigurator) {
    }

}
