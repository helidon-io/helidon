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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Properties;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.postgresql.ds.common.BaseDataSource;

/**
 * Replace serialization code.
 */
@TargetClass(className = "org.postgresql.ds.common.BaseDataSource")
public final class BaseDataSourceSubstitution {

    @Alias
    private String[] serverNames;
    @Alias
    private String databaseName;
    @Alias
    private String user;
    @Alias
    private String password;
    @Alias
    private int[] portNumbers;
    @Alias
    private Properties properties;

    /*
     * Original PgSQL prototype uses Serialization to create deep clone of BaseDataSource
     * class. This replacement makes just shallow copy but it seems to be enough.
     * IOException is thrown to match prototype's failures.
     */
    /**
     * Initialize PgSQL {@code BaseDataSource} from another instance.
     *
     * @param source source instance
     * @throws IOException when any issue with data copying occurs
     */
    @Substitute
    public void initializeFrom(BaseDataSource source) throws IOException {
        final String[] serverNamesSrc = source.getServerNames();
        final int[] portNumbersSrc = source.getPortNumbers();
        if (serverNamesSrc != null) {
            serverNames = new String[serverNamesSrc.length];
            for (int i = 0; i < serverNamesSrc.length; i++) {
                serverNames[i] = serverNamesSrc[i] != null ? serverNamesSrc[i] : null;
            }
        } else {
            serverNames = null;
        }
        databaseName = source.getDatabaseName();
        user = source.getUser();
        password = source.getPassword();
        if (portNumbersSrc != null) {
            portNumbers = new int[portNumbersSrc.length];
            System.arraycopy(portNumbersSrc, 0, portNumbers, 0, portNumbersSrc.length);
        } else {
            portNumbers = null;
        }
        try {
            Field propertiesField = BaseDataSource.class.getDeclaredField("properties");
            boolean propertiesAcc = propertiesField.canAccess(source);
            propertiesField.setAccessible(true);
            final Properties propertiesSrc = (Properties) propertiesField.get(source);
            propertiesField.setAccessible(propertiesAcc);
            properties = new Properties();
            properties.putAll(propertiesSrc);
        } catch (IllegalAccessException | NoSuchFieldException | SecurityException e) {
            throw new IOException("Could not initialize Properties class", e);
        }
    }

    /*
     * Removing writeBaseObject from original. It shall not be accessible now.
     */
    @Delete
    protected void writeBaseObject(ObjectOutputStream out) throws IOException {
    }

    /*
     * Removing readBaseObject from original. It shall not be accessible now.
     */
    @Delete
    protected void readBaseObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    }

    /**
     * Creates an instance of PgSQL BaseDataSource substitution class.
     * For testing purposes only.
     */
    BaseDataSourceSubstitution() {
        this.serverNames = null;
        this.databaseName = null;
        this.user = null;
        this.password = null;
        this.portNumbers = null;
        this.properties = null;
    }

}
