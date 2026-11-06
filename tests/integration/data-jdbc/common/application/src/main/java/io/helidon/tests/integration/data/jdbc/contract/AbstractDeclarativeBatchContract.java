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
package io.helidon.tests.integration.data.jdbc.contract;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.helidon.data.jdbc.JdbcBatchException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.tests.integration.data.jdbc.declarative.repository.BatchRepository;
import io.helidon.tests.integration.data.jdbc.support.DatabaseFixture;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Executes generated source adapters through the same real-driver batch contract.
 */
public abstract class AbstractDeclarativeBatchContract extends AbstractJdbcBatchContract {
    @Override
    protected final JdbcClient.BatchResult insert(List<String> rows, String email) {
        return service(BatchRepository.class).insert(rows, email);
    }

    @Test
    protected final void executesRecordsRepeatedMarkersAndAlignedArrays() {
        BatchRepository repository = service(BatchRepository.class);
        repository.records(List.of(new BatchRepository.Change("record-one", null, 1),
                                   new BatchRepository.Change("record-two", "email", 2)));
        assertThat(client().create("SELECT NAME FROM CONTACT WHERE ID = 1").map(String.class).one(), is("record-one"));
        repository.aligned(Arrays.asList("array-one", "array-two").iterator(), null, 1, 2);
        assertThat(client().create("SELECT EMAIL FROM CONTACT WHERE ID = 1").map(String.class).optional(),
                   is(Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> repository.aligned(List.of("mismatch").iterator(), "email", 1, 2));
        assertThat(client().create("SELECT NAME FROM CONTACT WHERE ID = 1").map(String.class).one(), is("array-one"));
    }

    @Test
    protected final void executesInheritedAliasesAndUnchangedRecordComponents() {
        BatchRepository repository = service(BatchRepository.class);
        DatabaseFixture fixture = service(DatabaseFixture.class);
        String input = "alias ' OR '1'='1";
        JdbcClient.BatchResult inserted = repository.insertAliased(List.of(input, "alias-second"), null);
        assertThat(inserted.entries().size(), is(2));
        long first = fixture.committedByName(input).orElseThrow().id();
        long second = fixture.committedByName("alias-second").orElseThrow().id();
        assertThat(fixture.committedByName(input).orElseThrow().email(), is(Optional.empty()));
        assertThat(repository.alignedAliases(List.of("alias-updated", "alias-other").iterator(), null, first, second)
                           .entries().size(), is(2));
        assertThat(fixture.committedByName("alias-updated").orElseThrow().id(), is(first));
        assertThat(repository.aliasedRecords(null, List.of(new BatchRepository.Rename("alias-record", first)))
                           .entries().size(), is(1));
        assertThat(fixture.committedByName("alias-record").orElseThrow().email(), is(Optional.empty()));
        assertThat(fixture.committedByName("alias-other").orElseThrow().id(), is(second));
    }

    @Test
    protected final void aliasedEmptyAndUnequalSourcesPreserveCommittedState() {
        BatchRepository repository = service(BatchRepository.class);
        DatabaseFixture fixture = service(DatabaseFixture.class);
        JdbcClient.BatchResult empty = repository.insertAliased(List.of(), null);
        assertThat(empty.entries().isEmpty(), is(true));
        assertThat(empty.totalUpdateCount().orElseThrow(), is(0L));
        assertThrows(IllegalArgumentException.class,
                () -> repository.alignedAliases(List.of("mismatch-one", "mismatch-two").iterator(), null, 1));
        assertThat(fixture.committedByName("alpha").isPresent(), is(true));
        assertThat(fixture.committedByName("beta").isPresent(), is(true));
        assertThat(fixture.committedByName("mismatch-one"), is(Optional.empty()));
    }

    @Test
    protected final void aliasBatchesPreserveTransactionCompletionAndLaterReuse() {
        BatchRepository repository = service(BatchRepository.class);
        DatabaseFixture fixture = service(DatabaseFixture.class);
        transactions().transaction(Tx.Type.REQUIRED,
                () -> repository.insertAliased(List.of("alias-committed"), null));
        long id = fixture.committedByName("alias-committed").orElseThrow().id();
        assertThrows(TxException.class, () -> transactions().transaction(Tx.Type.REQUIRED, () -> {
            repository.alignedAliases(List.of("alias-rolled-back").iterator(), null, id);
            throw new IllegalStateException("Roll back the aliased batch.");
        }));
        assertThat(fixture.committedByName("alias-committed").orElseThrow().id(), is(id));
        assertThat(fixture.committedByName("alias-rolled-back"), is(Optional.empty()));
        assertThrows(TxException.class, () -> transactions().transaction(Tx.Type.REQUIRED, () -> {
            repository.insertAliased(List.of("alias-joined"), null);
            try {
                transactions().transaction(Tx.Type.REQUIRED,
                        () -> repository.insertAliased(Arrays.asList("alias-prefix", null), null));
            } catch (TxException _) {
                // The joined scope observed the failure and marked the transaction for rollback.
            }
            return null;
        }));
        assertThat(fixture.committedByName("alias-joined"), is(Optional.empty()));
        assertThat(fixture.committedByName("alias-prefix"), is(Optional.empty()));
        transactions().transaction(Tx.Type.REQUIRED,
                () -> repository.insertAliased(List.of("alias-reused"), null));
        assertThat(fixture.committedByName("alias-reused").isPresent(), is(true));
    }

    @Test
    protected final void observesAliasedPartialFailuresAndCaughtTransactionEffects() {
        BatchRepository repository = service(BatchRepository.class);
        DatabaseFixture fixture = service(DatabaseFixture.class);
        JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                () -> repository.insertAliased(Arrays.asList("batch-before", null, "batch-after"), null));
        assertThat(failure.result().entries().stream().map(JdbcClient.BatchResult.Entry::itemIndex).toList(),
                   is(List.of(0, 1, 2)));
        assertThat(failure.getMessage(), is("The JDBC batch failed."));
        List<String> committed = List.of("batch-before", "batch-after").stream()
                .filter(name -> fixture.committedByName(name).isPresent()).toList();
        assertThat(committed, is(committedAfterPartialFailure()));
        repository.insertAliased(List.of("alias-after-failure"), null);
        assertThat(fixture.committedByName("alias-after-failure").isPresent(), is(true));
        transactions().transaction(Tx.Type.REQUIRED, () -> {
            repository.insertAliased(List.of("alias-before-caught"), null);
            assertThrows(JdbcBatchException.class, () -> repository.insertAliased(Arrays.asList((String) null), null));
            return null;
        });
        assertThat(fixture.committedByName("alias-before-caught").isPresent(), is(!constraintFailureAbortsTransaction()));
    }
}
