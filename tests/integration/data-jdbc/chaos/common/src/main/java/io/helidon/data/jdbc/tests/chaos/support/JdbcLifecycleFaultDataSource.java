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
package io.helidon.data.jdbc.tests.chaos.support;

import javax.sql.DataSource;

/**
 * Test-owned datasource which delegates to live JDBC connections and can fail one selected lifecycle boundary.
 */
public interface JdbcLifecycleFaultDataSource extends DataSource, AutoCloseable {
    /**
     * Arms one failure for the next invocation of every selected JDBC lifecycle boundary.
     *
     * @param faults boundaries to fail
     */
    void arm(JdbcLifecycleFault... faults);

    /**
     * Returns how often a JDBC lifecycle boundary was invoked.
     *
     * @param fault boundary to inspect
     * @return invocation count
     */
    long calls(JdbcLifecycleFault fault);

    /**
     * Returns the number of physical connections created by this datasource.
     *
     * @return connection count
     */
    long connectionsCreated();

    /**
     * Closes every connection still retained after an intentionally failed cleanup path.
     */
    @Override
    void close();
}
