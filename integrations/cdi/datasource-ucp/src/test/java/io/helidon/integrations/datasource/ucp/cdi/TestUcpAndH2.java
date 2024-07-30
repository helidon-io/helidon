/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.datasource.ucp.cdi;

import java.sql.Connection;
import java.sql.SQLException;

import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static oracle.ucp.jdbc.PoolDataSourceFactory.getPoolDataSource;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class TestUcpAndH2 {

    TestUcpAndH2() {
        super();
    }

    @AfterEach
    void destroyPools() throws SQLException, UniversalConnectionPoolException {
        UniversalConnectionPoolManager ucpManager = UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager();
        for (String n : ucpManager.getConnectionPoolNames()) {
            ucpManager.destroyConnectionPool(n);
        }
    }

    /**
     * Ensures we don't accidentally upgrade to a version of the Universal Connection Pool that throws {@link
     * NullPointerException}s when no {@code serviceName} is set on a {@link PoolDataSource}.
     *
     * <p>The only way to set a service name on a {@link PoolDataSource} is, when the {@link PoolDataSource} is actually
     * a {@link PoolDataSourceImpl}, by invoking its {@code private void setServiceName(String)} method via
     * reflection.</p>
     *
     * @exception SQLException if a database access error occurs
     *
     * @exception NullPointerException if the Universal Connection Pool version in effect cannot handle unset service
     * names
     */
    @Test
    void testUcpAndH2Canary() throws SQLException {
        PoolDataSource pds = getPoolDataSource();
        pds.setConnectionFactoryClassName("org.h2.jdbcx.JdbcDataSource");
        pds.setURL("jdbc:h2:mem:test");
        pds.setUser("sa");
        pds.setPassword("");
        try (Connection c = pds.getConnection()) { // Throws NullPointerException in versions 21.10.0.0 and 21.11.0.0 and possibly others
            assertThat(c, not(nullValue()));
        }
    }

}
