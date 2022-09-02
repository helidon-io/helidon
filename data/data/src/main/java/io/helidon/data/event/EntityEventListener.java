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

import io.helidon.data.model.runtime.RuntimePersistentEntity;

import java.lang.annotation.Annotation;
import java.util.EventListener;

/**
 * The interface representing an entity event listener.
 *
 * <p>Event listeners are able to listen for persistence events through the various method hooks provided.
 * The {@link #supports(RuntimePersistentEntity, Class)} method can be used to narrow applicable entities and
 * should be called by implementors prior to invoking one of the event methods.</p>
 *
 * <p>Note that {@link EntityEventListener} added by the user SHOULD NOT be annotated with {@link io.micronaut.context.annotation.Primary}.</p>
 *
 * @param <T> the entity type
 * @author graemerocher
 * @author Denis Stepanov
 * @since 2.3.0
 */
public interface EntityEventListener<T> extends EventListener  {
    /**
     * A no-op event listener that does nothing.
     */
    @SuppressWarnings("unchecked")
    EntityEventListener<Object> NOOP = new EntityEventListener() {
        @Override
        public boolean supports(RuntimePersistentEntity entity, Class eventType) {
            return false;
        }
    };

    /**
     * Allows including or excluding a listener for a specific entity.
     * @param entity The entity
     * @param eventType The event type
     * @return True if it is supported
     */
    default boolean supports(RuntimePersistentEntity<T> entity, Class<? extends Annotation> eventType) {
        return true;
    }

    /**
     * A pre-persist hook. Implementors can return false to evict the operation.
     *
     * @param context The context object
     * @return A boolean value indicating whether to proceed with the operation.
     */
    default boolean prePersist(EntityEventContext<T> context) {
        return true;
    }

    /**
     * A post-persist hook. Executed once the object has been persisted.
     *
     * @param context The context object
     */
    default void postPersist(EntityEventContext<T> context) {
    }

    /**
     * A post-load hook. Executed once the object has been persisted.
     *
     * @param context The context object
     */
    default void postLoad(EntityEventContext<T> context) {
    }

    /**
     * A pre-remove hook. Implementors can return false to evict the operation.
     *
     * @param context The context object
     * @return A boolean value indicating whether to proceed with the operation.
     */
    default boolean preRemove(EntityEventContext<T> context) {
        return true;
    }

    /**
     * A post-remove hook. Executed once the object has been removed.
     *
     * @param context The context object
     */
    default void postRemove(EntityEventContext<T> context) {
    }

    /**
     * A pre-update hook. Implementors can return false to evict the operation.
     *
     * @param context The context object
     * @return A boolean value indicating whether to proceed with the operation.
     */
    default boolean preUpdate(EntityEventContext<T> context) {
        return true;
    }

    /**
     * A pre-update hook. Implementors can return false to evict the operation.
     *
     * @param context The context object
     * @return A boolean value indicating whether to proceed with the operation.
     */
    default boolean preQuery(QueryEventContext<T> context) {
        return true;
    }

    /**
     * A post-update hook. Executed once the object has been updated.
     *
     * @param context The context object
     */
    default void postUpdate(EntityEventContext<T> context) {
    }
}
