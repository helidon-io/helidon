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

/**
 * Helidon Data Repository.
 * <p>
 * The Helidon Data Repository provides unified API to work with database queries.
 * Data repository queries are an abstraction on top of Object–Relational Mapping (ORM).
 * They allow compile time translation of interfaces with query definitions into implementing
 * classes.
 * <p>
 * Data repository queries are methods of interfaces that extend {@link io.helidon.data.Data.GenericRepository}.
 * Data repository queries can be implemented in three ways:
 * <ul>
 *     <li>using method annotated with {@link io.helidon.data.Data.Query}</li>
 *     <li>using method name as query definition</li>
 *     <li>extending existing {@link io.helidon.data.Data.GenericRepository} child interfaces
 *         with common sets of query methods:
 *         <ul>
 *             <li>{@link io.helidon.data.Data.BasicRepository} to implement basic set of entity operations</li>
 *             <li>{@link io.helidon.data.Data.CrudRepository} to implement entity CRUD operations</li>
 *             <li>{@link io.helidon.data.Data.PageableRepository} to implement pageable
 *                 {@link io.helidon.data.Data.BasicRepository#findAll()} queries</li>
 *             <li>{@link io.helidon.data.Data.SessionRepository} to get access to persistence provider's session,
 *                  e.g. {@code jakarta.persistence.EntityManager}</li>
 *         </ul>
 *     </li>
 * </ul>
 */
package io.helidon.data;
