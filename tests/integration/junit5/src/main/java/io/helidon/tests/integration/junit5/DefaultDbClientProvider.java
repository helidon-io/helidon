/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5;

import java.lang.System.Logger;
import java.lang.reflect.Type;

import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.junit5.spi.DbClientProvider;

public class DefaultDbClientProvider implements DbClientProvider, SuiteResolver {

    private static final Logger LOGGER = System.getLogger(DefaultDbClientProvider.class.getName());

    private SuiteContext suiteContext;
    private DbClient dbClient;
    private DbClient.Builder builder;
    public DefaultDbClientProvider() {
        suiteContext = null;
        dbClient = null;
        builder = null;
    }

    public void suiteContext(SuiteContext suiteContext) {
        this.suiteContext = suiteContext;
    }

    @Override
    public void setup() {
        builder = DbClient.builder();
     }

    @Override
    public DbClient.Builder builder() {
        return builder;
    }

    @Override
    public void start() {
        dbClient = builder.build();
    }

    @Override
    public DbClient dbClient() {
        return dbClient;
    }

    @Override
    public boolean supportsParameter(Type type) {
        return DbClient.class.isAssignableFrom((Class<?>) type);
    }

    @Override
    public Object resolveParameter(Type type) {
        if (DbClient.class.isAssignableFrom((Class<?>)type)) {
            return dbClient;
        }
        throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
    }

}
