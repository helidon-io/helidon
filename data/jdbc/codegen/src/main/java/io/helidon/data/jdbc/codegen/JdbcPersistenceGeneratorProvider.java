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
package io.helidon.data.jdbc.codegen;

import io.helidon.common.Api;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.common.spi.PersistenceGeneratorProvider;

/**
 * Service-loader provider for JDBC repository code generation.
 */
@Api.Internal
public final class JdbcPersistenceGeneratorProvider implements PersistenceGeneratorProvider {

    /**
     * Creates the JDBC persistence-generator provider.
     */
    public JdbcPersistenceGeneratorProvider() {
    }

    /**
     * Creates a new stateless JDBC persistence generator for one code-generation environment.
     *
     * @return JDBC persistence generator
     */
    @Override
    public PersistenceGenerator create() {
        return new JdbcPersistenceGenerator();
    }
}
