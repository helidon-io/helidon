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
package io.helidon.data.jdbc.tests.declarative.h2;

import javax.sql.DataSource;

import io.helidon.data.jdbc.AbstractJdbcBootstrapDiagnosticTest;

/**
 * Runs the portable bootstrap diagnostic contract in the declarative H2 leaf.
 */
class H2DeclarativeBootstrapDiagnosticTest extends AbstractJdbcBootstrapDiagnosticTest {
    @Override
    protected DataSource dataSource() {
        return driverManagerDataSource("jdbc:h2:mem:declarative_bootstrap_diagnostics;DB_CLOSE_DELAY=-1",
                                       null,
                                       null);
    }
}
