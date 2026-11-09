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
package io.helidon.tests.integration.data.jdbc.parameters;

import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/**
 * Supplies configured aliases from a generic library compiled without parameter names.
 *
 * @param <T> The concrete repository supplies the scalar input type.
 */
public interface ConfiguredAliasStatements<T> {

    /**
     * Matches nullable email values using the configured named statement.
     *
     * @param original The email value supplies every occurrence of its marker.
     * @return The matching contact names are ordered by identifier.
     */
    @Jdbc.Statement("${application.aliases.find}")
    List<String> configuredAliases(@Data.Param("email") T original);

    /**
     * Inserts a contact through configured SQL and returns its identifier.
     *
     * @param original The contact name is bound as a value.
     * @param contactEmail The nullable email uses its declared SQL type.
     * @return The generated identifier is present after a successful insert.
     */
    @Jdbc.Statement("${application.aliases.insert}")
    @Jdbc.GeneratedKeys("id")
    Optional<Long> configuredAliasInsert(@Data.Param("rows") T original, @Data.Param("email") T contactEmail);

    /**
     * Binds the identifier before the replacement name in Java declaration order.
     *
     * @param original The identifier selects the contact.
     * @param text The replacement name is bound using its effective alias.
     * @return The number of updated contacts is returned.
     */
    @Jdbc.Statement("${application.aliases.update}")
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    int configuredAliasUpdate(@Data.Param("id") long original, @Data.Param("name") T text);
}
