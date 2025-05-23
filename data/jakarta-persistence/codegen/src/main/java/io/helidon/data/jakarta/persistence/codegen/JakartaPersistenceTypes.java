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
package io.helidon.data.jakarta.persistence.codegen;

import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

class JakartaPersistenceTypes {

    static final TypeName DATA_EXCEPTION = TypeName.create("io.helidon.data.DataException");
    static final TypeName PU_NAME_ANNOTATION = TypeName.create("io.helidon.data.Data.PersistenceUnit");
    static final TypeName BASE_REPOSITORY_EXECUTOR = TypeName.create(
            "io.helidon.data.jakarta.persistence.JpaRepositoryExecutor");
    static final TypeName REPOSITORY_FACTORY = TypeName.create("io.helidon.data.spi.RepositoryFactory");
    static final TypeName REPOSITORY_PROVIDER = TypeName.create("io.helidon.data.spi.RepositoryProvider");
    static final TypeName EXECUTOR = TypeName.create("io.helidon.data.jakarta.persistence.JpaRepositoryExecutor");

    static final TypeName ENTITY_MANAGER = TypeName.create("jakarta.persistence.EntityManager");
    // SessionRepository<EntityManager>
    static final TypeName SESSION_REPOSITORY = TypeName.builder()
            .from(TypeName.create("io.helidon.data.Data.SessionRepository"))
            .addTypeArgument(ENTITY_MANAGER)
            .build();
    // Consumer<EntityManager>
    static final TypeName SESSION_CONSUMER = TypeName.builder()
            .from(TypeName.create("java.util.function.Consumer"))
            .addTypeArgument(ENTITY_MANAGER)
            .build();
    // <R>
    static final TypeArgument GENERIC_R = TypeArgument.builder()
            .token("R")
            .build();
    // Function<EntityManager, R>
    static final TypeName SESSION_FUNCTION = TypeName.builder()
            .from(TypeName.create("java.util.function.Function"))
            .addTypeArgument(ENTITY_MANAGER)
            .addTypeArgument(GENERIC_R)
            .build();

    // Jakarta Persistence CriteriaBuilder
    static final TypeName CRITERIA_BUILDER = TypeName.create("jakarta.persistence.criteria.CriteriaBuilder");
    // Jakarta Persistence CriteriaQuery with no generic type
    static final TypeName RAW_CRITERIA_QUERY = TypeName.create("jakarta.persistence.criteria.CriteriaQuery");
    // Jakarta Persistence CriteriaQuery with no generic type
    static final TypeName RAW_CRITERIA_DELETE = TypeName.create("jakarta.persistence.criteria.CriteriaDelete");
    // Jakarta Persistence criteria Root with no generic type
    static final TypeName RAW_ROOT = TypeName.create("jakarta.persistence.criteria.Root");
    // Jakarta Persistence criteria Order
    static final TypeName ORDER = TypeName.create("jakarta.persistence.criteria.Order");
    // Jakarta Persistence criteria Expression with no generic type
    static final TypeName RAW_EXPRESSION = TypeName.create("jakarta.persistence.criteria.Expression");
    static final Annotation INJECTION_SINGLETON = Annotation.create(TypeName.create(
            "io.helidon.service.registry.Service.Singleton"));
    static final Annotation INJECTION_PRE_DESTROY = Annotation.create(TypeName.create(
            "io.helidon.service.registry.Service.PreDestroy"));

    private JakartaPersistenceTypes() {
        throw new UnsupportedOperationException("No instances of Types are allowed");
    }

}
