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

import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

/**
 * Publishes test-owned datasources for chaos scenarios that need bounded pool behavior.
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
public class ChaosTestDataSourceFactory implements Service.ServicesFactory<DataSource> {
    private static volatile List<Service.QualifiedInstance<DataSource>> dataSources = List.of();

    /**
     * Publishes one named datasource for the next service registry.
     *
     * @param name datasource name
     * @param dataSource datasource to expose
     */
    public static void dataSource(String name, DataSource dataSource) {
        dataSources = List.of(Service.QualifiedInstance.create(Objects.requireNonNull(dataSource),
                                                               Qualifier.createNamed(name)));
    }

    /**
     * Removes all published datasources after a chaos test shuts down.
     */
    public static void reset() {
        dataSources = List.of();
    }

    @Override
    public List<Service.QualifiedInstance<DataSource>> services() {
        return dataSources;
    }
}
