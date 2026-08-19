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

import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.application.SqlInjectionOperations;
import io.helidon.data.jdbc.tests.support.DatabaseFixture;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * SQL-injection safety behavior which every JDBC database and application
 * style must satisfy for bound values.
 */
public abstract class AbstractJdbcSqlInjectionContract {
    private ServiceRegistryManager manager;
    private DatabaseFixture database;
    private SqlInjectionOperations contacts;

    /**
     * Returns the adapter type for one application programming style.
     *
     * @return SQL-injection operations adapter type
     */
    protected abstract Class<? extends SqlInjectionOperations> operationsType();

    @BeforeEach
    protected final void setUpApplication() {
        beforeStartApplication();
        manager = ServiceRegistryManager.start();
        database = manager.registry().get(DatabaseFixture.class);
        database.reset();
        contacts = manager.registry().get(operationsType());
    }

    /**
     * Allows a database-specific leaf test to publish dynamic configuration before the registry starts.
     */
    protected void beforeStartApplication() {
    }

    /**
     * Proves quote, semicolon, and comment-marker input is inserted as one
     * bound string value and cannot terminate the INSERT or execute a second
     * statement.
     */
    @Test
    protected void insertsStatementTerminatorPayloadAsLiteralData() {
        String payload = "alpha'); DROP TABLE CONTACT; --";
        String email = "literal-insert@example.test";
        long id = contacts.insertContact(payload, email);

        assertThat(contacts.findByName(payload),
                   is(Optional.of(new ContactView(id, payload, Optional.of(email)))));
        assertBaselineRowsRemain();
    }

    /**
     * Proves a tautology-shaped value in an equality predicate is compared as
     * one literal value and cannot expand the predicate to return all rows.
     */
    @Test
    protected void equalityPredicateTreatsTautologyPayloadAsLiteralData() {
        String payload = "' OR '1'='1";

        assertThat(contacts.findAllByName(payload), is(List.of()));
        assertBaselineRowsRemain();
    }

    /**
     * Proves one hostile value used by a repeated named marker is bound at
     * every physical position and remains literal data.
     */
    @Test
    protected void repeatedNamedMarkerTreatsHostileValueAsLiteralData() {
        String payload = "alpha' OR EMAIL IS NULL -- :value ?";
        String email = "repeated-marker@example.test";
        long id = contacts.insertContact(payload, email);

        assertThat(contacts.findAllByNameOrEmail(payload),
                   is(List.of(new ContactView(id, payload, Optional.of(email)))));
        assertBaselineRowsRemain();
    }

    /**
     * Proves a delete payload containing a quoted tautology is matched only as
     * an exact bound value and cannot delete unrelated rows.
     */
    @Test
    protected void deleteUsesExactBoundValueForTautologyPayload() {
        String payload = "alpha' OR '1'='1";

        assertThat(contacts.deleteByName(payload), is(0L));
        assertBaselineRowsRemain();
    }

    /**
     * Proves update input that looks like an assignment fragment is stored as
     * the replacement name and cannot update additional columns.
     */
    @Test
    protected void updatePayloadCannotModifyAdditionalColumns() {
        String replacement = "renamed', EMAIL='attacker@example.test";

        assertThat(contacts.renameByName("alpha", replacement), is(1L));
        assertThat(contacts.findByName(replacement),
                   is(Optional.of(new ContactView(1, replacement, Optional.of("alpha@example.test")))));
        assertThat(contacts.findByName("alpha"), is(Optional.empty()));
        assertThat(contacts.findByName("beta"),
                   is(Optional.of(new ContactView(2, "beta", Optional.empty()))));
    }

    @AfterEach
    protected final void shutDownApplication() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    private void assertBaselineRowsRemain() {
        assertThat(database.committedByName("alpha"),
                   is(Optional.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")))));
        assertThat(database.committedByName("beta"),
                   is(Optional.of(new ContactView(2, "beta", Optional.empty()))));
    }
}
