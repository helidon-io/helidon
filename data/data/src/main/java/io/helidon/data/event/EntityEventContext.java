/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.event;

//import io.micronaut.core.annotation.NonNull;
import io.helidon.core.beans.BeanProperty;

/**
 * Context object for event receivers.
 * @param <T> The generic type of the entity
 * @author graemerocher
 * @since 2.3.0
 */
public interface EntityEventContext<T> extends PersistenceEventContext<T> {
    /**
     * @return The entity associated with the event.
     */
    T getEntity();

    /**
     * Sets a property to its new value. If the property is immutable then the copy-constructor will be used and {@link #getEntity()} will return the updated entity.
     * @param property The property
     * @param newValue The new value
     * @param <P> THe property type
     */
    <P> void setProperty(BeanProperty<T, P> property, P newValue);

    /**
     * @return Does the underlying persistence engine have its own event system.
     */
    default boolean supportsEventSystem() {
        return true;
    }
}
