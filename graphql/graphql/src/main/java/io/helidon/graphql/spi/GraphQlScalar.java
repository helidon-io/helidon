/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.graphql.spi;

import io.helidon.common.Api;
import io.helidon.service.registry.Service;

/**
 * Service Registry contract for declarative GraphQL custom scalar coercion.
 * <p>
 * Declarative integrations generate the matching {@link io.helidon.graphql.GeneratedGraphQl.CustomScalar} adapter from the
 * annotated scalar Java type and delegate conversion to this contract.
 *
 * @param <T> Java scalar type
 */
@Api.Preview
@Api.Since("27.0.0")
@Service.Contract
public interface GraphQlScalar<T> {
    /**
     * Convert a Java value to a GraphQL scalar result value.
     * <p>
     * GraphQL integrations handle {@code null} values before invoking this method.
     *
     * @param value Java value, never {@code null}
     * @return GraphQL scalar value, never {@code null}
     */
    Object serialize(T value);

    /**
     * Convert a GraphQL variable value to a Java value.
     * <p>
     * GraphQL integrations handle {@code null} values before invoking this method.
     *
     * @param value GraphQL input value, never {@code null}
     * @return Java value, never {@code null}
     */
    T parseValue(Object value);

}
