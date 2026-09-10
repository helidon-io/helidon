/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

/**
 * Helidon Data Repository.
 * <p>
 * Helidon Data provides a unified repository API for database operations. Repository interfaces are translated into
 * implementing classes at compile time.
 * <p>
 * A repository interface can extend {@link io.helidon.data.Data.GenericRepository} to identify its entity and
 * identifier types. A persistence provider may also support repository interfaces annotated with
 * {@link io.helidon.data.Data.Repository} that do not declare these types at the repository level.
 * <p>
 * Depending on the persistence provider, repository operations can be defined in three ways:
 * <ul>
 *     <li>annotating a method with {@link io.helidon.data.Data.Query}</li>
 *     <li>deriving a query from the method name</li>
 *     <li>extending a standard repository interface that provides common operations or capabilities:
 *         <ul>
 *             <li>{@link io.helidon.data.Data.BasicRepository} for a basic set of entity operations</li>
 *             <li>{@link io.helidon.data.Data.CrudRepository} for entity CRUD operations</li>
 *             <li>{@link io.helidon.data.Data.PageableRepository} for pageable alternatives to
 *                 {@link io.helidon.data.Data.BasicRepository#findAll()}</li>
 *             <li>{@link io.helidon.data.Data.SessionRepository} for access to a persistence provider session, such as
 *                 {@code jakarta.persistence.EntityManager}</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * This is a preview feature of Helidon - it is ready for production use, but we reserve the rights to change APIs
 * without the usual deprecation process. This feature will be backward compatible within a major version of Helidon.
 */
package io.helidon.data;
