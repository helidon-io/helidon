/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.data.repository.reactive;

import java.util.Optional;
import java.util.concurrent.Flow;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.data.repository.GenericRepository;
import io.helidon.data.repository.RepositoryException;

/**
 * Reactive data repository interface for blocking CRUD operations.
 * Blocking variant of <ul>
 *     <li>Create</li>
 *     <li>Read</li>
 *     <li>Write</li>
 *     <li>Update</li>
 * </ul> operations.
 *
 * @param <E> type of the entity
 * @param <ID> type of the ID (primary key)
 */
public interface ReactiveStreamsCrudRepository<E, ID> extends GenericRepository<E, ID> {

    /**
     * Persists provided entity.
     * Implementations of this method may always execute {@code insert} without detecting whether the entity was already saved or not.
     *
     * @param entity the entity to persist. Must not be {@code null}
     * @param <T> type of the entity
     * @return persisted entity. Never returns {@code null}
     * @throws {@link RepositoryException} if the entity is {@code null} or the operation has failed
     */
    <T extends E> Single<T> save(T entity);

    /**
     * Persists all provided entities.
     * Implementations of this method may always execute {@code insert} without detecting whether the entity was already saved or not.
     *
     * @param entities the entities to persist. Must not be {@code null}
     * @param <T> type of the entity
     * @return persisted entities. Never returns {@code null}
     * @throws {@link RepositoryException} if the entities are {@code null} or the operation has failed
     */
    <T extends E> Multi<T> saveAll(Iterable<T> entities);

    /**
     * Persists provided entity with {@code update} operation.
     *
     * @param entity the entity to persist. Must not be {@code null}
     * @param <T> type of the entity
     * @return persisted entity. Never returns {@code null}
     * @throws {@link RepositoryException} if the entity is {@code null} or the operation has failed
     */
    <T extends E> Single<T> update(T entity);

    /**
     * Persists all provided entities with {@code update} operation.
     *
     * @param entities the entities to persist. Must not be {@code null}
     * @param <T> type of the entity
     * @return persisted entities. Never returns {@code null}
     * @throws {@link RepositoryException} if the entities are {@code null} or the operation has failed
     */
    <T extends E> Multi<T> updateAll(Iterable<T> entities);

    /**
     * Find entity by ID (primary key) value.
     *
     * @param id the ID of the entity to search for. Must not be {@code null}
     * @return the entity with the given ID or {@code Optional#empty()} if no such entity was found
     * @throws {@link RepositoryException} if the ID is {@code null} or the operation has failed
     */
    Single<Optional<E>> findById(ID id);

    /**
     * Check whether entity with given ID (primary key) exists.
     *
     * @param id the ID of the entity to search for. Must not be {@code null}
     * @return value of {2ode true} if an entity with the given ID exists or {@code false} otherwise
     * @throws {@link RepositoryException} if the ID is {@code null} or the operation has failed
     */
    Single<Boolean> existsById(ID id);

    /**
     * Return all entities of the {@code E} type.
     *
     * @return all entities found
     * @throws {@link RepositoryException} if the operation has failed
     */
    Multi<E> findAll();

    /**
     * Return the number of all entities of the {@code E} type.
     *
     * @return the number of all entities found
     * @throws {@link RepositoryException} if the operation has failed
     */
    Single<Long> count();

    /**
     * Deletes the entity with the given ID (primary key.
     *
     * @param id ID of the entity to be deleted. Must not be {@code null}
     * @throws {@link RepositoryException} if the ID is {@code null} or the operation has failed
     */
    Single<Void> deleteById(ID id);

    /**
     * Deletes provided entity.
     *
     * @param entity the entity to delete. Must not be {@code null}
     * @throws {@link RepositoryException} if the entity is {@code null} or the operation has failed
     */
    Single<Void> delete(E entity);

    /**
     * Deletes all provided entities.
     *
     * @param entities the entities to delete. Must not be {@code null}
     * @throws {@link RepositoryException} if the entities are {@code null} or the operation has failed
     */
    Single<Void> deleteAll(Iterable<? extends E> entities);

    /**
     * Deletes all entities of the {@code E} type.
     *
     * @throws {@link RepositoryException} if the operation has failed
     */
    Single<Void> deleteAll();
}

