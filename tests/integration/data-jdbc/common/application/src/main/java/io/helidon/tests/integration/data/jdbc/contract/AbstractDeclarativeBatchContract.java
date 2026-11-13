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

import java.sql.JDBCType;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.data.jdbc.JdbcBatchException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.tests.integration.data.jdbc.declarative.repository.BatchRepository;
import io.helidon.tests.integration.data.jdbc.declarative.repository.BatchRepository.ScalarChange;
import io.helidon.tests.integration.data.jdbc.support.DatabaseFixture;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
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

    @Override
    protected final JdbcClient.BatchResult insertSource(Iterable<String> rows) {
        return service(BatchRepository.class).insertIterable(rows);
    }

    @Override
    protected final JdbcClient.BatchResult insertOrderedSet(Set<String> rows) {
        return service(BatchRepository.class).insertSet(rows);
    }

    @Override
    protected final JdbcClient.BatchResult storeScalars(Iterable<ScalarChange> rows) {
        return service(BatchRepository.class).scalarRecords(rows);
    }

    /**
     * The same generated repository accepts reordered named SQL and compatible positional YAML replacements.
     */
    @Test
    protected final void configuredRepositoriesLoadYamlAcrossAllSupportedSourceShapes() {
        for (String variant : List.of("", "reordered/", "positional/")) {
            restartBatch(batchConfiguration(variant));
            BatchRepository repository = service(BatchRepository.class);
            DatabaseFixture fixture = service(DatabaseFixture.class);
            assertThat(repository.configuredInsert(oneShot(List.of("configured-one", "configured-two")), null)
                               .entries().size(), is(2));
            assertThat(fixture.committedByName("configured-one").orElseThrow().email(), is(Optional.empty()));
            assertThat(repository.configuredAuto(oneShot(List.of("configured-auto")), "auto@example.test")
                               .entries().size(), is(1));
            repository.configuredVoid(oneShot(List.of("configured-void")), null);
            assertThat(fixture.committedByName("configured-void").isPresent(), is(true));
            assertThat(repository.configuredSet(new LinkedHashSet<>(List.of("set-z", "set-a"))).entries().size(), is(2));
            assertThat(client().create("SELECT NAME FROM CONTACT WHERE NAME LIKE 'set-%' ORDER BY ID")
                               .map(String.class).list(), is(List.of("set-z", "set-a")));
            assertThat(repository.configuredRecords(oneShot(List.of(new BatchRepository.Change("record-one", null, 1),
                    new BatchRepository.Change("record-two", "two@example.test", 2))).iterator()).entries().size(), is(2));
            assertThat(fixture.committedByName("record-one").orElseThrow().email(), is(Optional.empty()));
            assertThat(repository.configuredArrays(new String[] {"one@example.test", null}, 1, 2).entries().size(), is(2));
            assertThat(repository.configuredDuplicates(oneShot(List.of(new BatchRepository.Replacement("renamed"))),
                    oneShot(List.of(new BatchRepository.Expected(1, "record-one"))).iterator()).entries().size(), is(1));
            assertThat(fixture.committedByName("renamed").orElseThrow().id(), is(1L));
            assertThat(repository.configuredAligned(oneShot(List.of(new BatchRepository.Change("aligned", null, 1))),
                    oneShot(List.of(new BatchRepository.Rename("excluded", 1))).iterator()).entries().size(), is(1));
            assertThat(repository.configuredAliases(oneShot(List.of("aliased")).iterator(), null, 2).entries().size(), is(1));
            assertThat(repository.configuredInherited(oneShot(List.of("inherited")), null).entries().size(), is(1));
            assertThat(repository.configuredWithoutNames(oneShot(List.of("without-metadata")), null).entries().size(), is(1));
            assertThat(fixture.committedByName("without-metadata").orElseThrow().email(), is(Optional.empty()));
            assertThat(repository.configuredBinary(oneShot(Arrays.asList(new byte[] {3, 4}, null)), 1).entries().size(), is(2));
            assertThat(client().create("SELECT BYTES_VALUE FROM SCALAR_VALUE WHERE ID = 1").map(byte[].class).optional(),
                       is(Optional.empty()));
            assertThat(repository.configuredBinaryConstant(new long[] {1}, new byte[] {5, 6}).entries().size(), is(1));
            assertThat(client().create("SELECT BYTES_VALUE FROM SCALAR_VALUE WHERE ID = 1").map(byte[].class).one(),
                       is(new byte[] {5, 6}));
            assertThat(repository.configuredBytes((byte) 7, (byte) 8).entries().size(), is(2));
            assertThat(client().create("SELECT BYTE_VALUE FROM SCALAR_VALUE WHERE ID = 1").map(Byte.class).one(), is((byte) 8));
            assertThat(repository.configuredScalars(oneShot(List.of(new ScalarChange(1, null, null, null, null, null,
                    null, null, null)))).entries().size(), is(1));
            assertThat(client().create("SELECT TIMESTAMP_VALUE FROM SCALAR_VALUE WHERE ID = 1")
                               .map(Timestamp.class).optional(), is(Optional.empty()));
        }
    }

    /**
     * Empty and failed capture performs no database work and leaves no executed prefix.
     */
    @Test
    protected final void configuredCaptureRejectsUnequalAndFailedSourcesBeforeExecution() {
        for (String variant : List.of("", "reordered/", "positional/")) {
            restartBatch(batchConfiguration(variant));
            BatchRepository repository = service(BatchRepository.class);
            DatabaseFixture fixture = service(DatabaseFixture.class);
            assertThat(repository.configuredInsert(oneShot(List.of()), null).totalUpdateCount().orElseThrow(), is(0L));
            assertThat(repository.configuredRecords(List.<BatchRepository.Change>of().iterator())
                               .totalUpdateCount().orElseThrow(), is(0L));
            assertThat(repository.configuredArrays(new String[0], new long[0]).totalUpdateCount().orElseThrow(), is(0L));
            assertThat(repository.configuredDuplicates(oneShot(List.of()), List.<BatchRepository.Expected>of().iterator())
                               .totalUpdateCount().orElseThrow(), is(0L));
            assertThrows(IllegalArgumentException.class,
                    () -> repository.configuredArrays(new String[] {"prefix@example.test", "late@example.test"}, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> repository.configuredDuplicates(oneShot(List.of(new BatchRepository.Replacement("prefix"))),
                            List.<BatchRepository.Expected>of().iterator()));
            assertThrows(NullPointerException.class, () -> repository.configuredInsert(null, null));
            assertThrows(NullPointerException.class,
                    () -> repository.configuredRecords(Arrays.asList((BatchRepository.Change) null).iterator()));
            IllegalStateException failure = new IllegalStateException("The configured batch source failed.");
            Iterable<String> broken = () -> new Iterator<>() {
                private int position;

                @Override
                public boolean hasNext() {
                    return position < 2;
                }

                @Override
                public String next() {
                    if (position++ == 0) {
                        return "configured-prefix";
                    }
                    throw failure;
                }
            };
            assertThat(assertThrows(IllegalStateException.class, () -> repository.configuredInsert(broken, null)),
                       sameInstance(failure));
            assertThat(fixture.committedByName("configured-prefix"), is(Optional.empty()));
            assertThat(fixture.committedByName("alpha").orElseThrow().email(), is(Optional.of("alpha@example.test")));
            assertThat(repository.configuredInsert(oneShot(List.of("configured-reused")), null).entries().size(), is(1));
            assertThat(fixture.committedByName("configured-reused").isPresent(), is(true));
        }
    }

    /**
     * Invalid deployment metadata fails at repository activation even when the requested batch would be empty.
     */
    @Test
    protected final void configuredRepositoriesRejectInvalidStylesAndCoverageAtActivation() {
        Config clean = batchConfiguration("");
        Map<String, String> invalid = Map.ofEntries(
                Map.entry("application.batches.insert", "INSERT INTO CONTACT (NAME, EMAIL) VALUES (:name, ?)"),
                Map.entry("application.batches.records", "UPDATE CONTACT SET NAME = :missing, EMAIL = :email WHERE ID = :id"),
                Map.entry("application.batches.arrays", "UPDATE CONTACT SET EMAIL = ? WHERE ID = ? OR ID = ?"),
                Map.entry("application.batches.names", "INSERT INTO CONTACT (NAME) VALUES (:name, :extra)"),
                Map.entry("application.batches.duplicates", "UPDATE CONTACT SET NAME = :name WHERE ID = :id AND NAME = :name"),
                Map.entry("application.batches.without-names", "INSERT INTO CONTACT (NAME, EMAIL) VALUES (:arg0, :arg1)"),
                Map.entry("application.batches.aliases", "UPDATE CONTACT SET NAME = ?, EMAIL = ? WHERE ID = ?"),
                Map.entry("application.batches.inherited", "INSERT INTO CONTACT (NAME, EMAIL) VALUES (?, ?)"),
                Map.entry("application.batches.bytes", " "));
        Iterable<String> forbidden = () -> {
            throw new AssertionError("Invalid configured SQL must fail before source traversal.");
        };
        for (var scenario : invalid.entrySet()) {
            Map<String, String> values = new HashMap<>(clean.asMap().get());
            values.put(scenario.getKey(), scenario.getValue());
            restartBatch(Config.just(ConfigSources.create(values)));
            assertThrows(IllegalArgumentException.class,
                    () -> service(BatchRepository.class).configuredInsert(forbidden, null));
            assertThrows(IllegalArgumentException.class,
                    () -> service(BatchRepository.class).configuredInsert(List.of(), null));
        }
        restartBatch(clean);
        assertThrows(DataException.class, () -> service(BatchRepository.Required.class).insert(forbidden));
        assertThat(service(BatchRepository.class).configuredInsert(oneShot(List.of("after-invalid")), null).entries().size(), is(1));
    }

    @Test
    protected final void configuredDefaultsExecuteOnlyWhenTheirKeyIsMissing() {
        Config clean = batchConfiguration("");
        Map<String, String> values = new HashMap<>(clean.asMap().get());
        values.remove("application.batches.insert");
        restartBatch(Config.just(ConfigSources.create(values)));
        assertThat(service(Config.class).get("application.batches.insert").exists(), is(false));
        service(BatchRepository.class).configuredInsert(oneShot(List.of("configured-default")), null);
        assertThat(service(DatabaseFixture.class).committedByName("configured-default").orElseThrow().email(), is(Optional.empty()));
        for (String shape : List.of("object", "list")) {
            Config invalid = Config.just(ConfigSources.classpath("configured-batches/invalid-" + shape + "/application.yaml"),
                    ConfigSources.create(clean.asMap().get()));
            assertThat(invalid.get("application.batches.insert").type(),
                       is(shape.equals("object") ? Config.Type.OBJECT : Config.Type.LIST));
            restartBatch(invalid);
            assertThrows(DataException.class, () -> service(BatchRepository.class).configuredInsert(List.of(), null));
        }
    }

    /**
     * Prepared repository batches retain propagation, caller-owned completion, and failure evidence.
     */
    @Test
    protected final void configuredTransactionsPreserveCommittedStateAndLaterReuse() {
        for (String variant : List.of("", "reordered/", "positional/")) {
            restartBatch(batchConfiguration(variant));
            BatchRepository repository = service(BatchRepository.class);
            DatabaseFixture fixture = service(DatabaseFixture.class);
            transactions().transaction(Tx.Type.REQUIRED, () -> repository.configuredInsert(List.of("configured-commit"), null));
            assertThat(fixture.committedByName("configured-commit").isPresent(), is(true));
            assertThrows(TxException.class, () -> transactions().transaction(Tx.Type.REQUIRED, () -> {
                repository.configuredInsert(List.of("configured-rollback"), null);
                throw new IllegalStateException("Roll back the configured batch.");
            }));
            assertThat(fixture.committedByName("configured-rollback"), is(Optional.empty()));
            JdbcBatchException partial = assertThrows(JdbcBatchException.class,
                    () -> repository.configuredInsert(Arrays.asList("batch-before", null, "batch-after"), null));
            assertThat(partial.result().entries().size(), is(3));
            assertThat(List.of("batch-before", "batch-after").stream()
                    .filter(name -> fixture.committedByName(name).isPresent()).toList(), is(committedAfterPartialFailure()));
            assertThrows(TxException.class, () -> transactions().transaction(Tx.Type.REQUIRED, () -> {
                repository.configuredInsert(List.of("configured-joined"), null);
                assertThrows(TxException.class, () -> transactions().transaction(Tx.Type.REQUIRED,
                        () -> repository.configuredInsert(Arrays.asList("configured-partial", null), null)));
                return null;
            }));
            assertThat(fixture.committedByName("configured-joined"), is(Optional.empty()));
            assertThat(fixture.committedByName("configured-partial"), is(Optional.empty()));
            transactions().transaction(Tx.Type.REQUIRED, () -> {
                repository.configuredInsert(List.of("configured-caught"), null);
                assertThrows(JdbcBatchException.class, () -> repository.configuredInsert(Arrays.asList((String) null), null));
                return null;
            });
            assertThat(fixture.committedByName("configured-caught").isPresent(), is(!constraintFailureAbortsTransaction()));
            for (Tx.Type type : List.of(Tx.Type.REQUIRED, Tx.Type.SUPPORTED, Tx.Type.MANDATORY, Tx.Type.NEW, Tx.Type.UNSUPPORTED)) {
                String name = "configured-" + type;
                assertThrows(TxException.class, () -> transactions().transaction(Tx.Type.REQUIRED, () -> {
                    repository.configuredInsert(List.of(name + "-outer"), null);
                    transactions().transaction(type, () -> repository.configuredInsert(List.of(name), null));
                    throw new IllegalStateException("Roll back the outer configured batch scope.");
                }));
                assertThat(fixture.committedByName(name + "-outer"), is(Optional.empty()));
                assertThat(fixture.committedByName(name).isPresent(), is(type == Tx.Type.NEW || type == Tx.Type.UNSUPPORTED));
            }
            assertThrows(TxException.class, () -> transactions().transaction(Tx.Type.MANDATORY,
                    () -> repository.configuredInsert(List.of("configured-mandatory"), null)));
            assertThrows(TxException.class, () -> transactions().transaction(Tx.Type.REQUIRED,
                    () -> transactions().transaction(Tx.Type.NEVER,
                            () -> repository.configuredInsert(List.of("configured-never"), null))));
            transactions().transaction(Tx.Type.NEVER, () -> repository.configuredInsert(List.of("configured-reuse"), null));
            assertThat(fixture.committedByName("configured-reuse").isPresent(), is(true));
        }
    }

    /**
     * Separate queries inspect stored rows after each generated or imperative batch completes.
     */
    @Test
    protected final void inferredAndExplicitBatchesMatchImperativeOutcomesAndCommittedState() {
        BatchRepository repository = service(BatchRepository.class);
        DatabaseFixture fixture = service(DatabaseFixture.class);
        JdbcClient.BatchResult imperative = client().create("INSERT INTO CONTACT (NAME, EMAIL) VALUES (?, ?)")
                .executeBatch(List.of("imperative-one", "imperative-two"),
                        (bindings, name) -> bindings.bind(1, name).bindNull(2, JDBCType.VARCHAR));
        int mode = 0;
        for (BiFunction<List<String>, String, JdbcClient.BatchResult> execute :
                List.<BiFunction<List<String>, String, JdbcClient.BatchResult>>of(
                        repository::insert, repository::insertAuto, repository::insertExplicit)) {
            List<String> names = List.of("mode-" + mode + "-one", "mode-" + mode + "-two");
            JdbcClient.BatchResult result = execute.apply(names, null);
            assertThat(result.totalUpdateCount(), is(imperative.totalUpdateCount()));
            assertThat(result.entries().stream().map(JdbcClient.BatchResult.Entry::status).toList(),
                       is(imperative.entries().stream().map(JdbcClient.BatchResult.Entry::status).toList()));
            for (String name : names) {
                assertThat(fixture.committedByName(name).orElseThrow().email(), is(Optional.empty()));
            }
            assertThat(execute.apply(List.of(), null).totalUpdateCount().orElseThrow(), is(0L));
            mode++;
        }
    }

    @Test
    protected final void executesRecordIteratorsAndAlignedRecordSources() {
        BatchRepository repository = service(BatchRepository.class);
        DatabaseFixture fixture = service(DatabaseFixture.class);
        JdbcClient.BatchResult iterator = repository.recordIterator(oneShot(List.of(
                new BatchRepository.Change("iterator-one", null, 1),
                new BatchRepository.Change("iterator-two", "two@example.test", 2))).iterator());
        assertThat(iterator.entries().size(), is(2));
        assertThat(fixture.committedByName("iterator-one").orElseThrow().email(), is(Optional.empty()));
        assertThat(fixture.committedByName("iterator-two").orElseThrow().email(), is(Optional.of("two@example.test")));
        JdbcClient.BatchResult aligned = repository.alignedRecords(oneShot(List.of(
                new BatchRepository.Change("aligned-one", "one@example.test", 1),
                new BatchRepository.Change("aligned-two", null, 2))), oneShot(List.of(
                new BatchRepository.Rename("excluded", 1), new BatchRepository.Rename("excluded", 2))).iterator());
        assertThat(aligned.entries().size(), is(2));
        assertThat(fixture.committedByName("aligned-one").orElseThrow().email(), is(Optional.of("one@example.test")));
        assertThat(fixture.committedByName("aligned-two").orElseThrow().email(), is(Optional.empty()));
        assertThat(repository.alignedRecords(oneShot(List.of()), List.<BatchRepository.Rename>of().iterator())
                           .totalUpdateCount().orElseThrow(), is(0L));
    }

    @Test
    protected final void executesObjectArraysAndNullableScalarIterables() {
        BatchRepository repository = service(BatchRepository.class);
        DatabaseFixture fixture = service(DatabaseFixture.class);
        assertThat(repository.emailArrays(new String[] {"one@example.test", null}, 1, 2).entries().size(), is(2));
        assertThat(fixture.committedByName("alpha").orElseThrow().email(), is(Optional.of("one@example.test")));
        assertThat(fixture.committedByName("beta").orElseThrow().email(), is(Optional.empty()));
        assertThat(repository.emailIterable(oneShot(Arrays.asList(null, "two@example.test")), 1, 2).entries().size(), is(2));
        assertThat(fixture.committedByName("alpha").orElseThrow().email(), is(Optional.empty()));
        assertThat(fixture.committedByName("beta").orElseThrow().email(), is(Optional.of("two@example.test")));
        assertThat(repository.emailArrays(new String[0], new long[0]).totalUpdateCount().orElseThrow(), is(0L));
    }

    @Test
    protected final void distinguishesBinaryElementsConstantsAndByteVarargs() {
        BatchRepository repository = service(BatchRepository.class);
        assertThat(repository.binaryValues(oneShot(List.of(new byte[] {1, 2}, new byte[] {3, 4})), 1)
                           .entries().size(), is(2));
        assertThat(client().create("SELECT BYTES_VALUE FROM SCALAR_VALUE WHERE ID = 1").map(byte[].class).one(),
                   is(new byte[] {3, 4}));
        assertThat(repository.binaryConstant(new long[] {1}, new byte[] {5, 6}).entries().size(), is(1));
        assertThat(client().create("SELECT BYTES_VALUE FROM SCALAR_VALUE WHERE ID = 1").map(byte[].class).one(),
                   is(new byte[] {5, 6}));
        assertThat(repository.binaryValues(oneShot(Arrays.asList((byte[]) null)), 1).entries().size(), is(1));
        assertThat(client().create("SELECT BYTES_VALUE FROM SCALAR_VALUE WHERE ID = 1").map(byte[].class).optional(),
                   is(Optional.empty()));
        assertThat(repository.byteValues((byte) 7, (byte) 8).entries().size(), is(2));
        assertThat(client().create("SELECT BYTE_VALUE FROM SCALAR_VALUE WHERE ID = 1").map(Byte.class).one(), is((byte) 8));
    }

    @Test
    protected final void rejectsNullAndUnequalSourcesBeforeApplyingAnyPrefix() {
        BatchRepository repository = service(BatchRepository.class);
        assertThrows(NullPointerException.class, () -> repository.recordIterator(null));
        assertThrows(NullPointerException.class,
                () -> repository.recordIterator(Arrays.asList((BatchRepository.Change) null).iterator()));
        assertThrows(NullPointerException.class, () -> repository.emailArrays(null, 1));
        assertThrows(NullPointerException.class, () -> repository.emailArrays(new String[] {"email"}, (long[]) null));
        assertThrows(IllegalArgumentException.class, () -> repository.emailArrays(new String[] {"prefix", "extra"}, 1));
        assertThrows(IllegalArgumentException.class, () -> repository.emailArrays(new String[0], 1));
        assertThrows(IllegalArgumentException.class, () -> repository.emailArrays(new String[] {"prefix"}, new long[0]));
        assertThrows(IllegalArgumentException.class, () -> repository.alignedRecords(oneShot(List.of(
                new BatchRepository.Change("record-prefix", null, 1), new BatchRepository.Change("record-extra", null, 2))),
                List.of(new BatchRepository.Rename("excluded", 1)).iterator()));
        assertThrows(NullPointerException.class, () -> repository.alignedRecords(oneShot(Arrays.asList(
                new BatchRepository.Change("record-prefix", null, 1), null)), List.of(
                new BatchRepository.Rename("excluded", 1), new BatchRepository.Rename("excluded", 2)).iterator()));
        DatabaseFixture fixture = service(DatabaseFixture.class);
        assertThat(fixture.committedByName("alpha").orElseThrow().email(), is(Optional.of("alpha@example.test")));
        assertThat(fixture.committedByName("beta").orElseThrow().email(), is(Optional.empty()));
        assertThat(fixture.committedByName("record-prefix"), is(Optional.empty()));
        repository.emailArrays(new String[] {"reused@example.test"}, 1);
        assertThat(fixture.committedByName("alpha").orElseThrow().email(), is(Optional.of("reused@example.test")));
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
    private Config batchConfiguration(String variant) {
        Map<String, String> values = new HashMap<>(service(Config.class).asMap().get());
        values.putAll(Config.just(ConfigSources.classpath("configured-batches/" + variant + "application.yaml")).asMap().get());
        return Config.just(ConfigSources.create(values));
    }

}
