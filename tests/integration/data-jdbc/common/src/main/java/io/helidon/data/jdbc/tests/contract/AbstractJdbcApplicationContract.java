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
import io.helidon.data.NoResultException;
import io.helidon.data.NonUniqueResultException;
import io.helidon.data.jdbc.tests.application.ContactOperations;
import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.application.TestSql;
import io.helidon.data.jdbc.tests.support.DatabaseFixture;
import io.helidon.data.jdbc.tests.support.SensitiveFailureAssertions;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Application behavior which every JDBC programming style must satisfy.
 */
public abstract class AbstractJdbcApplicationContract {
    private ServiceRegistryManager manager;
    private ContactOperations contacts;
    private DatabaseFixture database;

    /**
     * Returns the adapter type for one application programming style.
     *
     * @return operations adapter type
     */
    protected abstract Class<? extends ContactOperations> operationsType();

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
     * Proves both application styles expose equivalent materialized queries,
     * updates, generated keys, and update counts.
     */
    @Test
    protected final void executesTheSharedQueryUpdateAndGeneratedKeyContract() {
        List<ContactView> initial = contacts.findAll();
        assertThat(initial,
                   is(List.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")),
                              new ContactView(2, "beta", Optional.empty()))));
        assertThrows(UnsupportedOperationException.class,
                     () -> initial.add(new ContactView(3, "mutable", Optional.empty())));

        long withEmail = contacts.insert("contract-email", "contract@example.test");
        assertThat(contacts.findByName("contract-email"),
                   is(Optional.of(new ContactView(withEmail,
                                                  "contract-email",
                                                  Optional.of("contract@example.test")))));

        long withoutEmail = contacts.insertWithoutEmail("contract-null");
        assertThat(contacts.findByName("contract-null"),
                   is(Optional.of(new ContactView(withoutEmail, "contract-null", Optional.empty()))));

        long defaultKey = contacts.insertWithDefaultKey("contract-default-key");
        assertThat(contacts.findByName("contract-default-key"),
                   is(Optional.of(new ContactView(defaultKey, "contract-default-key", Optional.empty()))));

        assertThat(contacts.rename(withoutEmail, "contract-renamed"), is(1L));
        assertThat(contacts.rename(Long.MAX_VALUE, "missing"), is(0L));
        assertThat(contacts.findByName("contract-null"), is(Optional.empty()));
        assertThat(contacts.findByName("contract-renamed"),
                   is(Optional.of(new ContactView(withoutEmail, "contract-renamed", Optional.empty()))));
        assertThat(contacts.delete(withoutEmail), is(1L));
        assertThat(contacts.delete(withoutEmail), is(0L));
        assertThat(contacts.renameAll("contract-all"), is(4L));
    }

    /**
     * Proves both application styles apply the same cardinality rules and
     * collapse no-row and SQL-null optional scalar results identically.
     */
    @Test
    protected final void enforcesSharedCardinalityAndSqlNullBehavior() {
        assertThat(contacts.oneByName("alpha"),
                   is(new ContactView(1, "alpha", Optional.of("alpha@example.test"))));
        assertThat(contacts.findByName("missing"), is(Optional.empty()));
        assertThrows(NoResultException.class, () -> contacts.oneByName("missing"));
        assertThrows(NonUniqueResultException.class, contacts::oneFromAll);

        assertThat(contacts.findByEmail("alpha@example.test"),
                   is(List.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")))));
        assertThat(contacts.findByEmail("missing@example.test"), is(List.of()));
        assertThat(contacts.requiredEmail(1), is("alpha@example.test"));
        assertThat(contacts.optionalEmail(1), is(Optional.of("alpha@example.test")));
        assertThat(contacts.optionalEmail(2), is(Optional.empty()));
        assertThat(contacts.optionalEmail(Long.MAX_VALUE), is(Optional.empty()));
        assertThrows(DataException.class, () -> contacts.requiredEmail(2));
    }

    /**
     * Proves SQL, record-label, and cardinality failures release their resources and do not poison the next operation.
     */
    @Test
    protected final void recoversAfterSqlMappingAndCardinalityFailures() {
        DataException sqlFailure = assertThrows(DataException.class, contacts::executeInvalidQuery);
        SensitiveFailureAssertions.assertNoSecrets(sqlFailure, TestSql.INVALID_QUERY_CANARY);
        assertThat(contacts.findByName("alpha"),
                   is(Optional.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")))));

        assertThrows(DataException.class, () -> contacts.missingRecordLabel(1));
        assertThat(contacts.findByName("alpha"),
                   is(Optional.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")))));

        assertThrows(NonUniqueResultException.class, contacts::oneFromAll);
        assertThat(contacts.findByName("alpha"),
                   is(Optional.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")))));
    }

    /**
     * Proves driver constraint and truncation failures hide bound canaries and permit a successful follow-up query.
     */
    @Test
    protected final void sanitizesDatabaseConstraintAndTruncationFailuresAndRecovers() {
        String duplicateEmail = "private-duplicate-email-canary@example.test";
        contacts.insert("first-unique", duplicateEmail);
        DataException uniqueFailure = assertThrows(DataException.class,
                                                   () -> contacts.insert("second-unique", duplicateEmail));
        SensitiveFailureAssertions.assertNoSecrets(uniqueFailure, duplicateEmail);
        assertThat(contacts.findByName("alpha"),
                   is(Optional.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")))));

        String nullEmailCanary = "private-null-name-canary@example.test";
        DataException notNullFailure = assertThrows(DataException.class,
                                                    () -> contacts.insertNullName(nullEmailCanary));
        SensitiveFailureAssertions.assertNoSecrets(notNullFailure, nullEmailCanary);
        assertThat(contacts.findByName("alpha"),
                   is(Optional.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")))));

        String oversizedName = "private-oversized-name-canary-" + "x".repeat(80);
        DataException truncationFailure = assertThrows(DataException.class,
                                                       () -> contacts.insert(oversizedName, "oversized@example.test"));
        SensitiveFailureAssertions.assertNoSecrets(truncationFailure, oversizedName);
        assertThat(contacts.findByName("alpha"),
                   is(Optional.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")))));
    }

    /**
     * Proves declarative and imperative operations commit and roll back the same independently observed database state.
     */
    @Test
    protected final void exposesEquivalentCommittedStateAcrossLocalTransactions() {
        Tx.transaction(Tx.Type.REQUIRED, () -> {
            contacts.insert("contract-committed", "committed@example.test");
            return null;
        });
        ContactView committed = database.committedByName("contract-committed").orElseThrow();
        assertThat(committed,
                   is(new ContactView(committed.id(), "contract-committed", Optional.of("committed@example.test"))));

        assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            contacts.insert("contract-rolled-back", "rolled-back@example.test");
            throw new IllegalStateException("force rollback");
        }));
        assertThat(database.committedByName("contract-rolled-back"), is(Optional.empty()));
    }

    @AfterEach
    protected final void shutDownApplication() {
        if (manager != null) {
            manager.shutdown();
        }
    }
}
