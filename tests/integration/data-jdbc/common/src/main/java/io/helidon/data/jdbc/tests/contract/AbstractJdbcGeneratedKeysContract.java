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
package io.helidon.data.jdbc.tests.contract;

import java.util.List;
import java.util.Optional;

import io.helidon.data.DataException;
import io.helidon.data.jdbc.tests.application.ContactLabel;
import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.application.GeneratedKeyOperations;
import io.helidon.data.jdbc.tests.support.DatabaseFixture;
import io.helidon.data.jdbc.tests.support.SensitiveFailureAssertions;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Generated-key behavior which every supported JDBC database and application
 * style must satisfy.
 */
public abstract class AbstractJdbcGeneratedKeysContract {
    private ServiceRegistryManager manager;
    private DatabaseFixture database;
    private GeneratedKeyOperations generatedKeys;

    /**
     * Returns the adapter type for one application programming style.
     *
     * @return generated-key operations adapter type
     */
    protected abstract Class<? extends GeneratedKeyOperations> operationsType();

    @BeforeEach
    protected final void setUpApplication() {
        beforeStartApplication();
        manager = ServiceRegistryManager.start();
        database = manager.registry().get(DatabaseFixture.class);
        database.reset();
        generatedKeys = manager.registry().get(operationsType());
    }

    /**
     * Allows a database-specific leaf test to publish dynamic configuration before the registry starts.
     */
    protected void beforeStartApplication() {
    }

    /**
     * Returns the generated-key operations for database-specific assertions.
     *
     * @return generated-key operations
     */
    protected final GeneratedKeyOperations generatedKeys() {
        return generatedKeys;
    }

    /**
     * Returns the database fixture for database-specific committed-state assertions.
     *
     * @return database fixture
     */
    protected final DatabaseFixture database() {
        return database;
    }

    /**
     * Proves a named generated key can be read as a scalar value and then used
     * to independently query the committed row. This is the portable key
     * contract because real drivers vary in what they return for driver-default
     * generated-key requests.
     */
    @Test
    protected void insertsAndReturnsNamedScalarGeneratedKey() {
        long id = generatedKeys.insertScalar("generated-scalar");

        assertThat(database.committedByName("generated-scalar"),
                   is(Optional.of(new ContactView(id, "generated-scalar", Optional.empty()))));
    }

    /**
     * Proves optional and list cardinality wrappers consume the generated-key
     * result set without changing the actual inserted database row. A single
     * insert must produce one optional key and one list element.
     */
    @Test
    protected void mapsGeneratedScalarKeyThroughOptionalAndListTerminals() {
        Optional<Long> optional = generatedKeys.insertOptionalScalar("generated-optional");
        List<Long> list = generatedKeys.insertScalarList("generated-list");
        long optionalId = optional.orElseThrow();

        assertThat(database.committedByName("generated-optional"),
                   is(Optional.of(new ContactView(optionalId, "generated-optional", Optional.empty()))));
        assertThat(list.size(), is(1));
        assertThat(database.committedByName("generated-list"),
                   is(Optional.of(new ContactView(list.getFirst(), "generated-list", Optional.empty()))));
    }

    /**
     * Proves a generated-key result set can map multiple requested generated
     * columns into a record projection. This validates more than the scalar ID
     * path: the driver must return the requested generated columns with labels
     * the provider can map back to application record components.
     */
    @Test
    protected void mapsGeneratedColumnsIntoARecordProjection() {
        ContactView contact = generatedKeys.insertRecord("generated-record", "record@example.test");

        assertThat(contact, is(new ContactView(contact.id(),
                                               "generated-record",
                                               Optional.of("record@example.test"))));
        assertThat(database.committedByName("generated-record"), is(Optional.of(contact)));
    }

    /**
     * Proves generated-key rows can be consumed by an application mapper rather
     * than only by built-in scalar and record mapping. The mapped ID is then
     * verified through an independent committed-state query.
     */
    @Test
    protected void mapsGeneratedColumnsThroughAnApplicationMapper() {
        ContactLabel label = generatedKeys.insertMapped("generated-mapped");

        assertThat(label.label(), is("preferred:generated-mapped"));
        assertThat(database.committedByName("generated-mapped"),
                   is(Optional.of(new ContactView(label.id(), "generated-mapped", Optional.empty()))));
    }

    /**
     * Proves an invalid generated-key column request fails as a sanitized data
     * access error and does not poison the JDBC client. Drivers may report
     * this at different phases, so the portable assertion is failure category,
     * canary secrecy, and successful recovery through the next operation.
     */
    @Test
    protected void recoversAfterInvalidGeneratedKeyColumnRequest() {
        String canary = "private-invalid-generated-key-canary";
        DataException failure = assertThrows(DataException.class,
                                             () -> generatedKeys.insertWithInvalidGeneratedKeyColumn(canary));

        SensitiveFailureAssertions.assertNoSecrets(failure, canary);
        long id = generatedKeys.insertScalar("generated-after-invalid-key");
        assertThat(database.committedByName("generated-after-invalid-key"),
                   is(Optional.of(new ContactView(id, "generated-after-invalid-key", Optional.empty()))));
    }

    @AfterEach
    protected final void shutDownApplication() {
        if (manager != null) {
            manager.shutdown();
        }
    }
}
