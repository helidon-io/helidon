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
package io.helidon.data.jdbc.tests;

import java.io.IOException;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Service;

/**
 * Generated-source fixture for generic repository hierarchies resolved by annotation processing.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface GenericHierarchyRepository extends GenericQueryRepository<String>,
                                                    GenericExceptionRepository<IOException>,
                                                    GenericCovariantRepository<String>,
                                                    ObjectCovariantRepository {

    /**
     * Uses an explicit mapper whose row-mapper contract is inherited through a generic superclass.
     *
     * @return mapped value
     */
    @Jdbc.Statement("SELECT VALUE FROM TEST_VALUE")
    @Jdbc.RowMapper(InheritedStringMapper.class)
    String mappedValue();

    /**
     * Generic row-mapper superclass.
     *
     * @param <T> mapped value type
     */
    abstract class GenericRowMapper<T> implements JdbcClient.RowMapper<T> {
    }

    /**
     * Concrete mapper that inherits {@code RowMapper<String>} through its generic superclass.
     */
    @Service.Singleton
    final class InheritedStringMapper extends GenericRowMapper<String> {
        @Override
        public String map(JdbcClient.Row row) {
            return row.required(1, String.class);
        }
    }
}

/**
 * Generic parent that contributes a parameter and result type resolved by the child repository.
 *
 * @param <T> query value type
 */
interface GenericQueryRepository<T> {

    /**
     * Returns the supplied generic value.
     *
     * @param value query value
     * @return mapped value
     */
    @Jdbc.Statement("SELECT ?")
    T genericValue(T value);
}

/**
 * Generic parent that contributes a checked exception resolved by the child repository.
 *
 * @param <E> checked exception type
 */
interface GenericExceptionRepository<E extends Exception> {

    /**
     * Updates a value while retaining the resolved checked exception contract.
     *
     * @param value updated value
     * @throws E when the update fails according to the repository contract
     */
    @Jdbc.Statement("UPDATE TEST_VALUE SET VALUE = ?")
    void updateValue(String value) throws E;
}

/**
 * Generic parent that contributes the covariant result declaration.
 *
 * @param <T> result type
 */
interface GenericCovariantRepository<T> {

    /**
     * Returns a covariant value.
     *
     * @return mapped value
     */
    @Jdbc.Statement("SELECT VALUE FROM TEST_VALUE")
    T covariantValue();
}

/**
 * Parent whose broad result is implemented by the generic covariant declaration.
 */
interface ObjectCovariantRepository {

    /**
     * Returns a broad value.
     *
     * @return mapped value
     */
    @Jdbc.Statement("SELECT VALUE FROM TEST_VALUE")
    Object covariantValue();
}
