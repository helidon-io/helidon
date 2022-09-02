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
package io.helidon.data.event.listeners;

import io.helidon.data.annotation.event.PostUpdate;
import io.helidon.data.event.EntityEventContext;
import io.helidon.data.event.EntityEventListener;
import io.helidon.data.model.runtime.RuntimePersistentEntity;

import java.lang.annotation.Annotation;

/**
 * Functional version or {@link io.helidon.data.annotation.event.PostUpdate} event handlers.
 *
 * @param <T> The entity type
 * @author graemerocher
 * @since 2.3.0
 */
public interface PostUpdateEventListener<T> extends EntityEventListener<T> {
    /**
     * A post-update hook. Implementors can return false to evict the operation.
     *
     * @param entity The entity
     */
    void postUpdate(T entity);

    @Override
    default void postUpdate(EntityEventContext<T> context) {
        postUpdate(context.getEntity());
    }

    @Override
    default boolean supports(RuntimePersistentEntity<T> entity, Class<? extends Annotation> eventType) {
        return eventType == PostUpdate.class;
    }
}

