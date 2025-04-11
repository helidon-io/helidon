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
package io.helidon.data.codegen;

import java.util.List;
import java.util.Set;

import io.helidon.common.types.TypeName;

class HelidonDataTypes {

    /* Helidon Data interfaces */

    // GenericRepository<T, K> is top level repository interface
    static final TypeName GENERIC_REPOSITORY = TypeName.create("io.helidon.data.Data.GenericRepository");
    // BasicRepository<E, ID> extends GenericRepository<E, ID> adds basic set of repository operations
    static final TypeName BASIC_REPOSITORY = TypeName.create("io.helidon.data.Data.BasicRepository");
    // CrudRepository<T, K> extends BasicRepository<E, ID> adds CRUD repository operations
    static final TypeName CRUD_REPOSITORY = TypeName.create("io.helidon.data.Data.CrudRepository");
    // PageableRepository<E, ID> extends GenericRepository<E, ID> adds pagination support
    static final TypeName PAGEABLE_REPOSITORY = TypeName.create("io.helidon.data.Data.PageableRepository");
    // Interfaces sorted from the most generic top the most specific interface.
    static final List<TypeName> INTERFACES_PRIORITY = List.of(GENERIC_REPOSITORY,
                                                              BASIC_REPOSITORY,
                                                              PAGEABLE_REPOSITORY,
                                                              CRUD_REPOSITORY);
    static final Set<TypeName> INTERFACES = Set.copyOf(INTERFACES_PRIORITY);

    /* Helidon Data types */

    static final TypeName SORT = TypeName.create("io.helidon.data.Sort");
    static final TypeName SLICE = TypeName.create("io.helidon.data.Slice");
    static final TypeName PAGE = TypeName.create("io.helidon.data.Page");
    static final TypeName PAGE_REQUEST = TypeName.create("io.helidon.data.PageRequest");

    static final TypeName DATA_QUERY = TypeName.create("io.helidon.data.query.DataQuery");
    static final TypeName PROJECTION = TypeName.create("io.helidon.data.query.Projection");

    /* Helidon Data annotations */

    // @Repository annotation (marks data repository interface)
    static final TypeName REPOSITORY = TypeName.create("io.helidon.data.Data.Repository");
    // @Data.Query method annotation
    static final TypeName QUERY_ANNOTATION = TypeName.create("io.helidon.data.Data.Query");

    // Set of annotations passed to codegen repository interfaces filtering
    static final Set<TypeName> ANNOTATIONS = Set.of(REPOSITORY);

    private HelidonDataTypes() {
        throw new UnsupportedOperationException("No instances of HelidonDataTypes are allowed");
    }

}
