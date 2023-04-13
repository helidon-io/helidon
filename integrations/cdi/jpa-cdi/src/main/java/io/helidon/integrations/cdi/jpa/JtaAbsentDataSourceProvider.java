/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.jpa;

import javax.sql.DataSource;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class JtaAbsentDataSourceProvider implements PersistenceUnitInfoBean.DataSourceProvider {


    /*
     * Instance fields.
     */


    private final Instance<DataSource> instance;


    /*
     * Constructors.
     */


    @Deprecated
    JtaAbsentDataSourceProvider() {
        this(null);
    }

    @Inject
    JtaAbsentDataSourceProvider(Instance<DataSource> instance) {
        super();
        this.instance = instance;
    }


    /*
     * Instance methods.
     */


    @Override
    public DataSource getDataSource(boolean jta, boolean useDefaultJta, String dataSourceName) {
        Instance<DataSource> instance = this.instance;
        if (dataSourceName == null) {
            if (useDefaultJta) {
                instance = null;
            }
        } else if (instance != null) {
            instance = instance.select(NamedLiteral.of(dataSourceName));
        }
        return instance == null ? null : instance.get();
    }
}
