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

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.service.registry.Service;

/**
 * Service Registry contract for GraphQL custom scalar mappings.
 * <p>
 * Implementations can be provided by generated code or by applications. Server and client integrations use this contract
 * to convert between Java values and GraphQL scalar values.
 */
@Api.Incubating
@Api.Since("27.0.0")
@Service.Contract
public interface GraphQlScalar {
    /**
     * GraphQL scalar name.
     *
     * @return scalar name
     */
    String name();

    /**
     * Java type represented by this scalar.
     *
     * @return Java scalar type
     */
    Class<?> type();

    /**
     * Description of this scalar.
     *
     * @return scalar description, or an empty string
     */
    default String description() {
        return "";
    }

    /**
     * Convert a Java value to a GraphQL scalar result value.
     * <p>
     * GraphQL integrations handle {@code null} values before invoking this method.
     *
     * @param value Java value, never {@code null}
     * @return GraphQL scalar value, never {@code null}
     */
    Object serialize(Object value);

    /**
     * Convert a GraphQL variable value to a Java value.
     * <p>
     * GraphQL integrations handle {@code null} values before invoking this method.
     *
     * @param value GraphQL input value, never {@code null}
     * @return Java value, never {@code null}
     */
    Object parseValue(Object value);

    /**
     * Convert a GraphQL literal value to a Java value.
     * <p>
     * GraphQL integrations handle {@code null} values before invoking this method.
     *
     * @param value GraphQL literal value converted to a Java scalar value, never {@code null}
     * @return Java value, never {@code null}
     */
    default Object parseLiteral(Object value) {
        return Objects.requireNonNull(parseValue(Objects.requireNonNull(value)), "parseLiteral result");
    }
}
