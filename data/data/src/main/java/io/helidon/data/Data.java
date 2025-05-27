/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;

/**
 * Helidon Data Repository annotations and interfaces.
 */
public final class Data {
    private Data() {
    }

    /**
     * Repository interface.
     * Data repository interface marked with this annotation will be processed by code generator.
     * This is a required repository annotation.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    public @interface Repository {
    }

    /**
     * Repository persistence unit name.
     * <p>
     * This is an optional repository annotation.
     * <p>
     * When used, the persistence unit name will be used to lookup appropriate instance to handle this repository.
     * This is useful when multiple databases are used from a single application.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    public @interface PersistenceUnit {

        /**
         * Name of a named persistence unit.
         * When using configuration, this is expected under {@code data.persistence-units.provider-type}, where provider-type
         * is the provider of the persistence unit (such as {@code jakarta}).
         *
         * @return the name
         */
        String value();

        /**
         * Whether the named {@link io.helidon.data.Data.PersistenceUnit} is required.
         *
         * @return value of {@code true} when the {@link #value() named} {@link io.helidon.data.Data.PersistenceUnit} is required,
         *         {@code false} otherwise, to use the default configuration if a named one is not available
         */
        boolean required() default true;
    }

    /**
     * Provider used to implement the repository.
     * <p>
     * This is an optional repository annotation.
     * <p>
     * When used, code generation will be done only by the defined provider type.
     * This is useful when multiple providers are used from a single application.
     */
    @Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Provider {
        /**
         * Type of the Helidon Data Provider that will handle this instance.
         *
         * @return provider type
         */
        String value();
    }

    /**
     * Qualifier used in generated code to reference which provider type to use when creating instances of repositories,
     * such as {@code eclipselink, jakarta, sql}.
     */
    @Service.Qualifier
    @Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD})
    public @interface ProviderType {
        /**
         * Type of this annotation (from Helidon Common Types).
         */
        TypeName TYPE = TypeName.create(ProviderType.class);

        /**
         * Type of the Helidon Data Support that will handle this instance.
         *
         * @return support type
         */
        String value();
    }

    /**
     * Data support specific query language definition.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Query {
        /**
         * The query.
         *
         * @return the query string
         */
        String value();
    }

    /**
     * Data repository interface for basic entity operations.
     *
     * @param <E>  type of the entity
     * @param <ID> type of the ID
     */
    public interface BasicRepository<E, ID> extends GenericRepository<E, ID> {

        /**
         * Saves provided entity.
         * This method will update existing record or insert a new record if record does not exist in the database.
         *
         * @param entity the entity to persist, shall not be {@code null}
         * @param <T>    type of the entity
         * @return persisted entity. Never returns {@code null}
         * @throws io.helidon.data.DataException if the entity is {@code null} or the operation has failed
         */
        <T extends E> T save(T entity);

        /**
         * Persists all provided entities.
         * Implementations of this method may always execute {@code insert} without detecting whether the entity was already saved
         * or
         * not.
         *
         * @param entities the entities to persist, shall not be {@code null}
         * @param <T>      type of the entity
         * @return persisted entities, never returns {@code null}
         * @throws io.helidon.data.DataException if the entities are {@code null} or the operation has failed
         */
        <T extends E> Iterable<T> saveAll(Iterable<T> entities);

        /**
         * Find entity by ID (primary key) value.
         *
         * @param id the ID of the entity to search for, shall not be {@code null}
         * @return the entity with the given ID or {@code Optional#empty()} if no such entity was found, never returns
         *         {@code null}
         * @throws io.helidon.data.DataException if the ID is {@code null} or the operation has failed
         */
        Optional<E> findById(ID id);

        /**
         * Check whether entity with given ID (primary key) exists.
         *
         * @param id the ID of the entity to search for, shall not be {@code null}
         * @return value of {2ode true} if an entity with the given ID exists or {@code false} otherwise
         * @throws io.helidon.data.DataException if the ID is {@code null} or the operation has failed
         */
        boolean existsById(ID id);

        /**
         * Return all entities of the {@code E} type.
         *
         * @return all entities found, never returns {@code null}
         * @throws io.helidon.data.DataException if the operation has failed
         */
        Stream<E> findAll();

        /**
         * Return the number of all entities of the {@code E} type.
         *
         * @return the number of all entities found
         * @throws io.helidon.data.DataException if the operation has failed
         */
        long count();

        /**
         * Deletes the entity with the given ID (primary key).
         *
         * @param id ID of the entity to be deleted, shall not be {@code null}
         * @return the number of deleted entities
         * @throws io.helidon.data.DataException if the ID is {@code null} or the operation has failed
         */
        long deleteById(ID id);

        /**
         * Deletes provided entity.
         *
         * @param entity the entity to delete, shall not be {@code null}
         * @throws io.helidon.data.DataException if the entity is {@code null} or the operation has failed
         */
        void delete(E entity);

        /**
         * Deletes all provided entities.
         *
         * @param entities the entities to delete, shall not be {@code null}
         * @throws io.helidon.data.DataException if the entities are {@code null} or the operation has failed
         */
        void deleteAll(Iterable<? extends E> entities);

        /**
         * Deletes all entities of the {@code E} type.
         *
         * @return the number of deleted entities
         * @throws io.helidon.data.DataException if the operation has failed
         */
        long deleteAll();

    }

    /**
     * Data repository interface for CRUD entity operations.
     * CRUD entity operations are:<ul>
     * <li>Create</li>
     * <li>Read</li>
     * <li>Write</li>
     * <li>Update</li></ul>
     *
     * @param <E>  type of the entity
     * @param <ID> type of the ID (primary key)
     */
    public interface CrudRepository<E, ID> extends BasicRepository<E, ID> {

        /**
         * Inserts provided entity.
         * This method will insert a new record into the database. The operation will fail if the record
         * is already present in the database.
         *
         * @param entity the entity to persist, shall not be {@code null}
         * @param <T>    type of the entity
         * @return persisted entity, never returns {@code null}
         */
        <T extends E> T insert(T entity);

        /**
         * Inserts all provided entities.
         * This method will insert a new record into the database. The operation will fail if the record
         * is already present in the database.
         *
         * @param entities the entities to persist, shall not be {@code null}
         * @param <T>      type of the entity
         * @return persisted entity, never returns {@code null}
         */
        <T extends E> Iterable<T> insertAll(Iterable<T> entities);

        /**
         * Persist provided entity with {@code update} operation.
         *
         * @param entity the entity to persist, shall not be {@code null}
         * @param <T>    type of the entity
         * @return persisted entity, never returns {@code null}
         * @throws io.helidon.data.DataException if the entity is {@code null} or the operation has failed
         */
        <T extends E> T update(T entity);

        /**
         * Persist all provided entities with {@code update} operation.
         *
         * @param entities the entities to persist, shall not be {@code null}
         * @param <T>      type of the entity
         * @return persisted entities, never returns {@code null}
         * @throws io.helidon.data.DataException if the entities are {@code null} or the operation has failed
         */
        <T extends E> Iterable<T> updateAll(Iterable<T> entities);

    }

    /**
     * Data repository interface.
     * This is the parent interface of all data repositories.
     *
     * @param <E>  type of the entity
     * @param <ID> type of the ID
     */
    public interface GenericRepository<E, ID> {
    }

    /**
     * Data repository interface with persistence session support.
     * Implementing this interface makes repository class to depend on specific persistence session type.
     * Target persistence session type must match session type of the specific persistence provider, e.g.<ul>
     * <li>{@code EntityManager} for Jakarta Persistence</li>
     * <li>{@code ClientSession} for native EclipseLink</li>
     * </ul>
     *
     * @param <S> type of the persistence session, e.g. {@code EntityManager}
     */
    public interface SessionRepository<S> {

        /**
         * Execute task with persistence session.
         * Task does not return any result.
         *
         * @param task task to be executed
         */
        void run(Consumer<S> task);

        /**
         * Execute task with persistence session.
         * Task computes and returns result.
         *
         * @param task task to be executed
         * @param <R>  task result type
         * @return task result
         */
        <R> R call(Function<S, R> task);

    }

    /**
     * Data repository interface with pagination support.
     *
     * @param <E>  type of the entity
     * @param <ID> type of the ID
     */
    public interface PageableRepository<E, ID> extends GenericRepository<E, ID> {

        /**
         * Return all entities of the {@code E} type.
         *
         * @param pageable the query result request
         * @return all entities found, never returns {@code null}
         * @throws io.helidon.data.DataException if the operation has failed
         */
        Page<E> pages(PageRequest pageable);

        /**
         * Return all entities of the {@code E} type.
         *
         * @param pageable the query result request
         * @return all entities found, never returns {@code null}
         * @throws io.helidon.data.DataException if the operation has failed
         */
        Slice<E> slices(PageRequest pageable);

    }
}
