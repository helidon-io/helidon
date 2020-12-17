/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.integrations.db.pgsql;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.postgresql.PGProperty;
import org.postgresql.ds.common.BaseDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test methods substituted in BaseDataSource.
 */
public class TestBaseDataSourceSubstitution {

    /*
     * Checkstyle settings require private fileds in BaseDataSourceSubstitution
     * and I can't add additional accessor methods to native image substitution class.
     * so reflection should be used to access them.
    */
    private static Object getPrivateField(Object instance, String name)
            throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        final Field field = instance.getClass().getDeclaredField(name);
        final boolean access = field.canAccess(instance);
        field.setAccessible(true);
        final Object value = field.get(instance);
        field.setAccessible(access);
        return value;
    }

    /**
     * Test initializeFrom on sample data.
     *
     * @throws java.io.IOException
     * @throws java.lang.ClassNotFoundException
     * @throws java.sql.SQLException
     * @throws java.lang.NoSuchFieldException
     * @throws java.lang.IllegalAccessException
     */
    @Test
    public void testInitializeFrom()
            throws IOException, ClassNotFoundException, SQLException,
            NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        String[] serverNames = {"first.server.org", "second.server.org"};
        String databaseName = "myDatabase";
        String user = "someUser";
        String password = "Us3rP4ssw0rd";
        int[] portNumbers = {5432};
        String appNameProperty = "Test Application";
        BaseDataSource dataSrc = new BaseDataSource() {
            @Override
            public String getDescription() {
                throw new UnsupportedOperationException("Not supported in tests.");
            }
        };
        dataSrc.setServerNames(serverNames);
        dataSrc.setDatabaseName(databaseName);
        dataSrc.setUser(user);
        dataSrc.setPassword(password);
        dataSrc.setPortNumbers(portNumbers);
        dataSrc.setProperty(PGProperty.APPLICATION_NAME, appNameProperty);
        BaseDataSourceSubstitution dataCopy = new BaseDataSourceSubstitution();
        dataCopy.initializeFrom(dataSrc);
        assertEquals(databaseName, getPrivateField(dataCopy, "databaseName"));
        assertEquals(user, getPrivateField(dataCopy, "user"));
        assertEquals(password, getPrivateField(dataCopy, "password"));
        int[] dataPortNumbers = (int[]) getPrivateField(dataCopy, "portNumbers");
        Properties dataProperties = (Properties) getPrivateField(dataCopy, "properties");
        assertEquals(portNumbers.length, dataPortNumbers.length);
        for (int i = 0; i < portNumbers.length; i++) {
             assertEquals(portNumbers[i], dataPortNumbers[i]);
        }
        assertEquals(appNameProperty, dataProperties.getProperty(PGProperty.APPLICATION_NAME.getName()));
    }

}