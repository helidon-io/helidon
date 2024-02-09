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

import java.sql.SQLException;
import java.util.Properties;

import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.UniversalConnectionPoolAdapter;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.Test;

import static oracle.ucp.jdbc.PoolDataSourceFactory.getPoolDataSource;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestUcpApi {

    TestUcpApi() {
        super();
    }

    @Test
    void testGetPoolDataSourceWithName() throws SQLException {
        // Some magic undocumented XML configuration file has not been set as a system property (!), so this call fails
        // in an undocumented way.
        assertThrows(SQLException.class, () -> getPoolDataSource("bogus"));
    }

    @Test
    void testGetPoolDataSourceWithMinimalProperties() throws SQLException {
        Properties p = new Properties();
        p.setProperty("connectionPoolName", "bogusConnectionPoolName");
        p.setProperty("dataSourceName", "bogusDataSourceName");
        // This call fails without some XML file present somewhere (!).
        assertThrows(SQLException.class, () -> getPoolDataSource(p));
    }

    @Test
    void testDefaultConnectionPoolNameIsNull() throws SQLException, UniversalConnectionPoolException {
        assertThat(getPoolDataSource().getConnectionPoolName(), is(nullValue()));
    }

    @Test
    void testCreateConnectionPoolFailsWithoutSufficientInformation() throws SQLException, UniversalConnectionPoolException {
        UniversalConnectionPoolManager ucpManager = UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager();
        assertThrows(UniversalConnectionPoolException.class,
                     () -> ucpManager.createConnectionPool((UniversalConnectionPoolAdapter) getPoolDataSource()));
    }

    @Test
    void testCreateConnectionPoolManuallyWithoutName() throws SQLException, UniversalConnectionPoolException {
        PoolDataSource pds = getPoolDataSource();
        pds.setConnectionFactoryClassName("org.h2.jdbcx.JdbcDataSource");
        pds.setURL("jdbc:h2:mem:test");
        assertThat(pds.getConnectionPoolName(), is(nullValue()));
        assertThat(pds.getDataSourceName(), is(nullValue()));
        UniversalConnectionPoolManager ucpManager = UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager();
        // You can create the pool without a name...
        ucpManager.createConnectionPool((UniversalConnectionPoolAdapter)pds);
        String[] names = ucpManager.getConnectionPoolNames();
        assertThat(names.length, is(1));
        assertThat(names[0], is(not(nullValue())));
        // ...and the name will be auto-generated.
        UniversalConnectionPool ucp = ucpManager.getConnectionPool(names[0]);
        assertThat(ucp.getName(), is(names[0]));
        // This whole API is perhaps surprising: for example, here the creation of the pool modifies the pds that was
        // serving as its creation template (!).
        assertThat(pds.getConnectionPoolName(), is(names[0]));
        // The dataSourceName appears never to be used or modified and may, perhaps, make sense only when the
        // aforementioned XML file exists.
        assertThat(pds.getDataSourceName(), is(nullValue()));
    }

}
