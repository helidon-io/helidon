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
package io.helidon.data.jdbc;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * Configuration blueprint for persistence unit properties owned by the JDBC
 * provider. These values are not forwarded to a datasource, JDBC driver,
 * connection, statement, or result set.
 */
@Api.Preview
@Prototype.Blueprint
@Prototype.Configured
interface JdbcPropertiesConfigBlueprint {

    /**
     * JDBC provider properties.
     *
     * @return JDBC provider properties
     */
    @Option.Configured
    @Option.DefaultMethod("create")
    JdbcProviderPropertiesConfig jdbc();
}
