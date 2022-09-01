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

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.data.model.Page;
import io.helidon.data.model.Pageable;
import io.helidon.data.model.Sort;
import io.helidon.data.RepositoryException;

/**
 * Reactive data repository interface with pagination support.
 *
 * @param <E> type of the entity
 * @param <ID> type of the ID (primary key)
 */
public interface ReactivePageableRepository<E, ID> extends ReactiveCrudRepository<E, ID> {

    /**
     * Find all results for the given sort order.
     *
     * @param sort the sort. Must not be {@code null}
     * @return all entities found
     * @throws {@link RepositoryException} if the sort is {@code null} or the operation has failed
     */
    Multi<E> findAll(Sort sort);

    /**
     * Finds all entities for the given Pageable.
     *
     * @param pageable the pageable. Must not be {@code null}
     * @return all entities found
     * @throws {@link RepositoryException} if the pageable is {@code null} or the operation has failed
     */
    Single<Page<E>> findAll(Pageable pageable);

}
