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
package io.helidon.data.jdbc.tests.support;

import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

/**
 * Publishes test owned data sources for registry integration scenarios.
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
public final class TestDataSourceFactory implements Service.ServicesFactory<DataSource> {

    private static volatile List<Service.QualifiedInstance<DataSource>> dataSources = List.of();

    /**
     * Publishes one named data source for the next registry.
     *
     * @param name data source name
     * @param dataSource data source to publish
     */
    public static void dataSource(String name, DataSource dataSource) {
        Objects.requireNonNull(name, "The test data source name must not be null.");
        Objects.requireNonNull(dataSource, "The test data source must not be null.");
        dataSources = List.of(Service.QualifiedInstance.create(dataSource,
                                                               Qualifier.createNamed(name)));
    }

    /**
     * Removes every published test data source.
     */
    public static void reset() {
        dataSources = List.of();
    }

    @Override
    public List<Service.QualifiedInstance<DataSource>> services() {
        return dataSources;
    }
}
