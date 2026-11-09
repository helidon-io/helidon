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
package io.helidon.data.jdbc;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcStreamingTest {
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet result;
    private JdbcClient client;
    private JdbcTransactionConnectionManager manager;
    private JdbcTxSupport transactions;

    @BeforeEach
    void prepareQuery() throws Exception {
        DataSource source = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        result = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(result);
        when(statement.getLargeUpdateCount()).thenReturn(-1L);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("VALUE");
        when(result.next()).thenReturn(true, true, true, false);
        when(result.getString(1)).thenReturn("value");
        manager = new JdbcTransactionConnectionManager();
        transactions = new JdbcTxSupport(manager, List.of(manager));
        client = new JdbcClientImpl(JdbcClientConfig.builder().dataSource("test").buildPrototype(), source, manager,
                                    JdbcClientConfigSupport.cachePolicy(JdbcClientConfig.builder().dataSource("test").buildPrototype()));
    }

    /**
     * The callback sees an executed query while mapping remains demand driven.
     */
    @Test
    void executesBeforeCallbackAndMapsOnlyDemandedRows() throws Exception {
        AtomicInteger mapped = new AtomicInteger();
        JdbcClient.Rows<String> rows = client.create("SELECT VALUE").map(row -> {
            mapped.incrementAndGet();
            return row.get(1, String.class);
        });
        rows.useStream(stream -> {
            try {
                verify(statement).execute();
                verify(result, never()).next();
            } catch (SQLException failure) {
                throw new AssertionError(failure);
            }
            assertThat(stream.isParallel(), is(false));
            assertThat(stream.limit(1).toList(), is(List.of("value")));
        });
        assertThat(mapped.get(), is(1));
        verify(statement).close();
        verify(connection).close();
        assertThrows(IllegalStateException.class, rows::list);
    }

    /**
     * Every consumption mode closes resources, including a callback that never reads.
     */
    @Test
    void closesFullEmptyAndExplicitlyClosedTraversals() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            prepareQuery();
            int selected = mode;
            client.create("SELECT VALUE").map(String.class).useStream(stream -> {
                switch (selected) {
                case 0 -> assertThat(stream.toList().size(), is(3));
                case 1 -> { }
                case 2 -> stream.close();
                default -> throw new AssertionError();
                }
            });
            verify(statement).close();
            verify(connection).close();
            verify(statement).getMoreResults();
        }
    }

    /**
     * Escaped sources cannot read JDBC after the callback or from another thread.
     */
    @Test
    void confinesStreamsIteratorsAndSpliterators() throws Exception {
        AtomicReference<Iterator<String>> retained = new AtomicReference<>();
        client.create("SELECT VALUE").map(String.class).useStream(stream -> retained.set(stream.iterator()));
        assertThrows(IllegalStateException.class, () -> retained.get().hasNext());
        prepareQuery();
        client.create("SELECT VALUE").map(String.class).useStream(stream -> {
            Spliterator<String> source = stream.spliterator();
            assertThat(source.trySplit(), nullValue());
            CompletableFuture<Void> offThread = CompletableFuture.runAsync(() -> {
                assertThrows(IllegalStateException.class, () -> source.tryAdvance(_ -> { }));
                assertThrows(IllegalStateException.class, stream::close);
            });
            try {
                offThread.get(5, TimeUnit.SECONDS);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            } finally {
                offThread.cancel(true);
            }
            assertThat(source.tryAdvance(_ -> { }), is(true));
        });
    }

    /**
     * Retained streams and spliterators cannot extend JDBC access beyond the callback.
     */
    @Test
    void expiresRetainedStreamsAndSpliterators() throws Exception {
        AtomicReference<Stream<String>> stream = new AtomicReference<>();
        client.create("SELECT VALUE").map(String.class).useStream(stream::set);
        assertThrows(IllegalStateException.class, () -> stream.get().findFirst());
        prepareQuery();
        AtomicReference<Spliterator<String>> source = new AtomicReference<>();
        client.create("SELECT VALUE").map(String.class).useStream(current -> source.set(current.spliterator()));
        assertThrows(IllegalStateException.class, () -> source.get().tryAdvance(_ -> { }));
    }

    /**
     * A nonnull wrapper can carry SQL NULL while an entirely caught application failure remains unobservable.
     */
    @Test
    void permitsNullableWrappersAndCaughtApplicationExceptions() throws Exception {
        when(result.getString(1)).thenReturn(null);
        client.create("SELECT VALUE").map(row -> row.optional(1, String.class)).useStream(stream -> {
            try {
                throw new IllegalArgumentException("The application handled this failure.");
            } catch (IllegalArgumentException _) {
                assertThat(stream.toList(), is(List.of(Optional.empty(), Optional.empty(), Optional.empty())));
            }
        });
    }

    /**
     * A mapper failure remains primary even when caught and followed by another exception.
     */
    @Test
    void retainsCaughtMappingFailures() {
        IllegalArgumentException first = new IllegalArgumentException("Mapping failed.");
        IllegalStateException later = new IllegalStateException("The callback failed later.");
        JdbcClient.Rows<String> rows = client.create("SELECT VALUE").map(_ -> { throw first; });
        assertThat(assertThrows(IllegalArgumentException.class, () -> rows.useStream(stream -> {
            assertThat(assertThrows(IllegalArgumentException.class, stream::count), sameInstance(first));
            assertThrows(IllegalStateException.class, stream::findFirst);
            throw later;
        })), sameInstance(first));
        assertThat(List.of(first.getSuppressed()), is(List.of(later)));
    }

    /**
     * A mapper cannot hide JDBC conversion failures or required SQL NULL failures.
     */
    @Test
    void retainsFailuresCaughtInsideMapper() throws Exception {
        when(result.getString(1)).thenThrow(new SQLException("Secret driver message.", "22000"));
        assertThrows(DataException.class, () -> client.create("SELECT VALUE").map(row -> {
            assertThrows(DataException.class, () -> row.get(1, String.class));
            assertThrows(IllegalStateException.class, () -> row.get(1, String.class));
            return "fallback";
        }).useStream(Stream::count));
    }

    /**
     * Caught label and name metadata failures remain terminal failures and prevent transaction commits.
     */
    @Test
    void retainsCaughtMetadataFailures() throws Exception {
        for (boolean transactional : new boolean[] {false, true}) {
            for (boolean physicalName : new boolean[] {false, true}) {
                for (boolean optional : new boolean[] {false, true}) {
                    for (boolean fatal : new boolean[] {false, true}) {
                        prepareQuery();
                        ResultSetMetaData metadata = result.getMetaData();
                        Throwable original = fatal ? new AssertionError("Metadata failed fatally.")
                                : new IllegalArgumentException("Secret metadata failure.");
                        Class<? extends Throwable> expected = fatal ? AssertionError.class : IllegalStateException.class;
                        if (physicalName) {
                            when(metadata.getColumnLabel(1)).thenReturn("");
                            when(metadata.getColumnName(1)).thenThrow(original);
                        } else {
                            when(metadata.getColumnLabel(1)).thenThrow(original);
                        }
                        AtomicReference<Throwable> observed = new AtomicReference<>();
                        Runnable operation = () -> client.create("SELECT VALUE").map(row -> {
                            observed.set(assertThrows(expected, () -> {
                                if (optional) {
                                    row.optional("VALUE", String.class);
                                } else {
                                    row.get("VALUE", String.class);
                                }
                            }));
                            return "fallback";
                        }).useStream(Stream::findFirst);
                        if (transactional) {
                            when(connection.getAutoCommit()).thenReturn(true, false);
                            assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
                                assertThat(assertThrows(expected, operation::run), sameInstance(observed.get()));
                                return null;
                            }));
                            verify(connection).rollback();
                            verify(connection, never()).commit();
                        } else {
                            assertThat(assertThrows(expected, operation::run), sameInstance(observed.get()));
                        }
                        if (fatal) {
                            assertThat(observed.get(), sameInstance(original));
                        } else {
                            String action = physicalName ? "name" : "label";
                            assertThat(observed.get().getMessage(), is("The JDBC provider encountered an exception of type '"
                                    + IllegalArgumentException.class.getName()
                                    + "' while reading a JDBC result column " + action + "."));
                            assertThat(observed.get().getCause(), nullValue());
                            StringWriter rendered = new StringWriter();
                            observed.get().printStackTrace(new PrintWriter(rendered));
                            assertThat(rendered.toString(), not(containsString(original.getMessage())));
                        }
                        verify(result, times(1)).next();
                        verify(result, never()).getString(1);
                        verify(result).close();
                        verify(statement).close();
                        verify(connection).close();
                    }
                }
            }
        }
    }

    /**
     * A metadata failure rejects subsequent row reads and traversal before JDBC access.
     */
    @Test
    void rejectsAccessAfterCaughtMetadataFailure() throws Exception {
        for (boolean physicalName : new boolean[] {false, true}) {
            prepareQuery();
            ResultSetMetaData metadata = result.getMetaData();
            if (physicalName) {
                when(metadata.getColumnLabel(1)).thenReturn("");
                when(metadata.getColumnName(1)).thenThrow(new IllegalArgumentException("Secret name failure."));
            } else {
                when(metadata.getColumnLabel(1)).thenThrow(new IllegalArgumentException("Secret label failure."));
            }
            AtomicReference<IllegalStateException> observed = new AtomicReference<>();
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client.create("SELECT VALUE")
                    .map(row -> {
                        observed.set(assertThrows(IllegalStateException.class, () -> row.get("VALUE", String.class)));
                        assertThrows(NullPointerException.class, () -> row.get("VALUE", null));
                        assertThrows(IllegalStateException.class, () -> row.get(1, String.class));
                        assertThrows(IllegalStateException.class, () -> row.optional("VALUE", String.class));
                        return "fallback";
                    }).useStream(stream -> {
                        Iterator<String> iterator = stream.iterator();
                        assertThrows(IllegalStateException.class, iterator::next);
                        assertThrows(IllegalStateException.class, iterator::hasNext);
                        assertThrows(IllegalStateException.class, iterator::next);
                    }));
            assertThat(failure, sameInstance(observed.get()));
            verify(metadata, times(1)).getColumnLabel(1);
            verify(metadata, physicalName ? times(1) : never()).getColumnName(1);
            verify(result, times(1)).next();
            verify(result, never()).getString(1);
            verify(result).close();
            verify(statement).close();
            verify(connection).close();
        }
    }

    /**
     * JDK close order and suppression survive explicit close caught by the application.
     */
    @Test
    void retainsCaughtCloseFailuresAcrossPrimitivePipelines() {
        List<Integer> order = new ArrayList<>();
        IllegalStateException first = new IllegalStateException("The first close failed.");
        IllegalArgumentException second = new IllegalArgumentException("The second close failed.");
        assertThat(assertThrows(IllegalStateException.class,
                () -> client.create("SELECT VALUE").map(String.class).useStream(stream -> {
                    var pipeline = stream.mapToInt(String::length).onClose(() -> {
                        order.add(1);
                        throw first;
                    }).onClose(() -> {
                        order.add(2);
                        throw second;
                    });
                    assertThrows(IllegalStateException.class, pipeline::close);
                })), sameInstance(first));
        assertThat(order, is(List.of(1, 2)));
        assertThat(List.of(first.getSuppressed()), is(List.of(second)));
    }

    /**
     * Validation does not acquire JDBC resources or consume a query terminal.
     */
    @Test
    void validatesNullAndRejectsGeneratedKeysBeforeExecution() throws Exception {
        JdbcClient.Rows<String> rows = client.create("SELECT VALUE").map(String.class);
        assertThrows(NullPointerException.class, () -> rows.useStream(null));
        rows.useStream(_ -> { });
        assertThrows(UnsupportedOperationException.class, () -> client.create("INSERT VALUE")
                .generatedKeys().map(row -> row.get(1, String.class)).useStream(_ -> { }));
        verify(statement, times(1)).execute();
    }

    /**
     * Reservations survive exhaustion and closure without poisoning rejected nested validation.
     */
    @Test
    void reservesTransactionUntilTerminalCleanup() throws Exception {
        when(connection.getAutoCommit()).thenReturn(true, false);
        transactions.transaction(Tx.Type.REQUIRED, () -> {
            client.create("SELECT VALUE").map(String.class).useStream(stream -> {
                assertThat(stream.count(), is(3L));
                stream.close();
                assertThrows(IllegalStateException.class, () -> client.create("SELECT VALUE").map(String.class).list());
                assertThrows(IllegalStateException.class, () -> transactions.transaction(Tx.Type.NEW, () -> null));
                assertThrows(IllegalStateException.class, () -> transactions.transaction(Tx.Type.UNSUPPORTED, () -> null));
            });
            client.create("SELECT VALUE").map(String.class).list();
            return null;
        });
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    /**
     * Catching the terminal failure cannot permit the surrounding transaction to commit.
     */
    @Test
    void caughtFailureMarksRollbackWithoutLifecycleFailure() throws Exception {
        when(connection.getAutoCommit()).thenReturn(true, false);
        assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
            assertThrows(DataException.class, () -> client.create("SELECT VALUE").map(_ -> null)
                    .useStream(stream -> assertThrows(DataException.class, stream::count)));
            client.create("SELECT VALUE").map(String.class).list();
            return null;
        }));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    /**
     * A fatal cleanup error takes precedence while remaining resources still close.
     */
    @Test
    void preservesFatalCleanupPrecedence() throws Exception {
        AssertionError fatal = new AssertionError("Closing failed fatally.");
        doThrow(fatal).when(statement).close();
        IllegalStateException application = new IllegalStateException("Processing failed.");
        assertThat(assertThrows(AssertionError.class, () -> client.create("SELECT VALUE").map(String.class)
                .useStream(_ -> { throw application; })), sameInstance(fatal));
        verify(connection).close();
    }
    /**
     * Setup failures skip the callback and still close every acquired resource.
     */
    @Test
    void closesAfterSetupFailureAndRejectsWrongChannels() throws Exception {
        when(statement.execute()).thenThrow(new SQLException("Secret execution failure.", "42000"));
        assertThrows(DataException.class, () -> client.create("SELECT VALUE").map(String.class)
                .useStream(_ -> { throw new AssertionError("The callback must not run."); }));
        verify(statement).close();
        verify(connection).close();
        prepareQuery();
        when(statement.execute()).thenReturn(false);
        assertThrows(DataException.class, () -> client.create("SELECT VALUE").map(String.class)
                .useStream(_ -> { throw new AssertionError("The callback must not run."); }));
        verify(statement).close();
    }

    /**
     * Early closure still rejects additional result channels without mapping discarded rows.
     */
    @Test
    void validatesFollowingChannelsWithoutTraversal() throws Exception {
        when(statement.getMoreResults()).thenReturn(true, false);
        assertThrows(DataException.class, () -> client.create("SELECT VALUE").map(_ -> {
            throw new AssertionError("Discarded rows must not be mapped.");
        }).useStream(_ -> { }));
        verify(result, never()).next();
        verify(statement).close();
        verify(connection).close();
    }

    /**
     * A cleanup failure cannot replace an earlier application failure or prevent later cleanup.
     */
    @Test
    void preservesApplicationIdentityAndSanitizesCleanup() throws Exception {
        IllegalStateException application = new IllegalStateException("The callback failed.");
        doThrow(new SQLException("Secret result close.", "42000")).when(result).close();
        doThrow(new SQLException("Secret statement close.", "42000")).when(statement).close();
        assertThat(assertThrows(IllegalStateException.class, () -> client.create("SELECT VALUE").map(String.class)
                .useStream(_ -> { throw application; })), sameInstance(application));
        verify(connection).close();
        assertThat(application.getSuppressed().length, is(1));
    }

    /**
     * Invalid row arguments remain recoverable, while required SQL NULL permanently fails processing.
     */
    @Test
    void separatesValidationFromRequiredNullFailures() throws Exception {
        client.create("SELECT VALUE").map(row -> {
            assertThrows(IllegalArgumentException.class, () -> row.get(0, String.class));
            assertThrows(NullPointerException.class, () -> row.get(1, null));
            return row.get(1, String.class);
        }).useStream(stream -> assertThat(stream.count(), is(3L)));
        prepareQuery();
        when(result.getString(1)).thenReturn(null);
        assertThrows(DataException.class, () -> client.create("SELECT VALUE").map(row -> {
            assertThrows(DataException.class, () -> row.get(1, String.class));
            return "fallback";
        }).useStream(Stream::count));
    }

    /**
     * Failures from buffered stream processing are retained even when traversal itself completed.
     */
    @Test
    void retainsBufferedPipelineFailures() {
        IllegalArgumentException comparison = new IllegalArgumentException("Comparison failed.");
        assertThat(assertThrows(IllegalArgumentException.class, () -> client.create("SELECT VALUE").map(String.class)
                .useStream(stream -> assertThrows(IllegalArgumentException.class,
                        () -> stream.sorted((_, _) -> { throw comparison; }).toList()))), sameInstance(comparison));
    }

    /**
     * Fatal close handlers take precedence without forming cyclic suppression graphs.
     */
    @Test
    void preservesFatalCloseHandlerPrecedence() {
        IllegalStateException ordinary = new IllegalStateException("Closing failed.");
        AssertionError fatal = new AssertionError("Closing failed fatally.");
        assertThat(assertThrows(AssertionError.class, () -> client.create("SELECT VALUE").map(String.class)
                .useStream(stream -> stream.onClose(() -> { throw ordinary; })
                        .onClose(() -> { throw fatal; }))), sameInstance(fatal));
        assertThat(List.of(fatal.getSuppressed()), is(List.of(ordinary)));
        assertThat(ordinary.getSuppressed().length, is(0));
    }

    /**
     * Existing implementations retain a compatible default and validate null before rejection.
     */
    @Test
    void preservesUnsupportedDefaultForAlternateRows() {
        JdbcClient.Rows<String> rows = new JdbcClient.Rows<>() {
            @Override
            public String one() { return "value"; }

            @Override
            public Optional<String> optional() { return Optional.of("value"); }

            @Override
            public List<String> list() { return List.of("value"); }
        };
        assertThrows(NullPointerException.class, () -> rows.useStream(null));
        assertThrows(UnsupportedOperationException.class, () -> rows.useStream(_ -> { }));
    }

    /**
     * A sorting failure raised by an iterator also crosses the provider processing boundary.
     */
    @Test
    void retainsBufferedIteratorFailures() {
        IllegalArgumentException comparison = new IllegalArgumentException("Comparison failed.");
        assertThat(assertThrows(IllegalArgumentException.class, () -> client.create("SELECT VALUE").map(String.class)
                .useStream(stream -> {
                    var iterator = stream.sorted((_, _) -> { throw comparison; }).iterator();
                    assertThrows(IllegalArgumentException.class, iterator::hasNext);
                })), sameInstance(comparison));
    }

    /**
     * Caught validation of linked or consumed stages does not become a processing failure.
     */
    @Test
    void keepsCaughtStreamStateValidationRecoverable() {
        client.create("SELECT VALUE").map(String.class).useStream(stream -> {
            var filtered = stream.filter(_ -> true);
            assertThrows(IllegalStateException.class, stream::count);
            assertThat(filtered.sequential().count(), is(3L));
            assertThrows(IllegalStateException.class, filtered::count);
        });
    }

    /**
     * Cleanup closes a failed channel without retrying result advancement.
     */
    @Test
    void doesNotRetryFailedResultAdvancementDuringCleanup() throws Exception {
        when(statement.getMoreResults()).thenThrow(new SQLException("Secret advancement failure.", "42000"));
        assertThrows(DataException.class, () -> client.create("SELECT VALUE").map(String.class)
                .useStream(stream -> assertThrows(DataException.class, stream::count)));
        verify(statement, times(1)).getMoreResults();
        verify(result).close();
        verify(statement).close();
        verify(connection).close();
    }

    /**
     * The adapter preserves JDK reduction identities without allowing null mapper results.
     */
    @Test
    void preservesJdkReductionIdentitySemantics() throws Exception {
        client.create("SELECT VALUE").map(String.class).useStream(stream ->
                assertThat(stream.reduce(null, (_, value) -> value), is("value")));
        prepareQuery();
        client.create("SELECT VALUE").map(String.class).useStream(stream ->
                assertThat(stream.limit(0).reduce(null, (_, value) -> value, (_, value) -> value), nullValue()));
    }

    /**
     * Buffered processing failures retain their identity even when they resemble iterator exhaustion.
     */
    @Test
    void retainsCaughtNoSuchElementProcessingFailures() throws Exception {
        for (boolean transactional : new boolean[] {false, true}) {
            for (int kind = 0; kind < 4; kind++) {
                prepareQuery();
                int selected = kind;
                NoSuchElementException failure = new NoSuchElementException("Buffered processing failed.");
                Runnable operation = () -> client.create("SELECT VALUE").map(String.class).useStream(stream -> {
                    Iterator<?> iterator = switch (selected) {
                    case 0 -> stream.sorted((_, _) -> { throw failure; }).iterator();
                    case 1 -> stream.mapToInt(String::length).sorted().map(_ -> { throw failure; }).iterator();
                    case 2 -> stream.mapToLong(String::length).sorted().map(_ -> { throw failure; }).iterator();
                    case 3 -> stream.mapToDouble(String::length).sorted().map(_ -> { throw failure; }).iterator();
                    default -> throw new AssertionError();
                    };
                    assertThat(assertThrows(NoSuchElementException.class, iterator::next), sameInstance(failure));
                });
                if (transactional) {
                    when(connection.getAutoCommit()).thenReturn(true, false);
                    assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
                        assertThat(assertThrows(NoSuchElementException.class, operation::run), sameInstance(failure));
                        return null;
                    }));
                    verify(connection).rollback();
                    verify(connection, never()).commit();
                } else {
                    assertThat(assertThrows(NoSuchElementException.class, operation::run), sameInstance(failure));
                }
                verify(statement).close();
                verify(connection).close();
            }
        }
    }

    /**
     * Ordinary exhaustion remains recoverable and does not mark a transaction for rollback.
     */
    @Test
    void permitsCaughtIteratorExhaustionForEveryAdapter() throws Exception {
        for (int kind = 0; kind < 4; kind++) {
            prepareQuery();
            int selected = kind;
            when(connection.getAutoCommit()).thenReturn(true, false);
            transactions.transaction(Tx.Type.REQUIRED, () -> {
                client.create("SELECT VALUE").map(String.class).useStream(stream -> {
                    Iterator<?> iterator = switch (selected) {
                    case 0 -> stream.iterator();
                    case 1 -> stream.mapToInt(String::length).iterator();
                    case 2 -> stream.mapToLong(String::length).iterator();
                    case 3 -> stream.mapToDouble(String::length).iterator();
                    default -> throw new AssertionError();
                    };
                    for (int index = 0; index < 3; index++) {
                        iterator.next();
                    }
                    assertThrows(NoSuchElementException.class, iterator::next);
                    assertThrows(NoSuchElementException.class, iterator::next);
                });
                return null;
            });
            verify(connection).commit();
            verify(connection, never()).rollback();
        }
    }

    /**
     * Sorted spliterators preserve natural comparator metadata and can reconstruct JDK streams.
     */
    @Test
    void forwardsSortedSpliteratorComparatorsForEveryAdapter() throws Exception {
        for (int kind = 0; kind < 4; kind++) {
            prepareQuery();
            int selected = kind;
            client.create("SELECT VALUE").map(String.class).useStream(stream -> {
                switch (selected) {
                case 0 -> {
                    var source = stream.sorted().spliterator();
                    assertThat(source.hasCharacteristics(Spliterator.SORTED), is(true));
                    assertThat(source.getComparator(), nullValue());
                    assertThat(StreamSupport.stream(source, false).count(), is(3L));
                }
                case 1 -> {
                    var source = stream.mapToInt(String::length).sorted().spliterator();
                    assertThat(source.getComparator(), nullValue());
                    assertThat(StreamSupport.intStream(source, false).count(), is(3L));
                }
                case 2 -> {
                    var source = stream.mapToLong(String::length).sorted().spliterator();
                    assertThat(source.getComparator(), nullValue());
                    assertThat(StreamSupport.longStream(source, false).count(), is(3L));
                }
                case 3 -> {
                    var source = stream.mapToDouble(String::length).sorted().spliterator();
                    assertThat(source.getComparator(), nullValue());
                    assertThat(StreamSupport.doubleStream(source, false).count(), is(3L));
                }
                default -> throw new AssertionError();
                }
            });
        }
    }

}
