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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxLifeCycle;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcTxSupportTest {

    @Test
    void requiredBeginsAndCommitsOneTransaction() {
        RecordingLifeCycle events = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(events));

        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "result"), is("result"));

        assertThat(events.eventKinds(), is(List.of("start:jdbc", "begin", "commit", "end")));
    }

    @Test
    void nestedRequiredJoinsAndFailureMarksOuterRollbackOnly() {
        RecordingLifeCycle events = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(events));

        assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
            support.transaction(Tx.Type.REQUIRED, () -> {
                throw new IllegalStateException("boom");
            });
            return null;
        }));

        assertThat(events.count("begin"), is(1L));
        assertThat(events.count("commit"), is(0L));
        assertThat(events.count("rollback"), is(1L));
        assertThat(events.count("start:jdbc"), is(2L));
        assertThat(events.count("end"), is(2L));
    }

    @Test
    void newSuspendsAndResumesOuterTransaction() {
        RecordingLifeCycle events = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(events));

        support.transaction(Tx.Type.REQUIRED, () -> {
            support.transaction(Tx.Type.NEW, () -> null);
            return null;
        });

        assertThat(events.eventKinds(),
                   is(List.of("start:jdbc",
                              "begin",
                              "start:jdbc",
                              "suspend",
                              "begin",
                              "commit",
                              "resume",
                              "end",
                              "commit",
                              "end")));
    }

    @Test
    void newOutsideATransactionBeginsAndCommits() {
        RecordingLifeCycle events = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(events));

        assertThat(support.transaction(Tx.Type.NEW, () -> "result"), is("result"));

        assertThat(events.eventKinds(), is(List.of("start:jdbc", "begin", "commit", "end")));
    }

    @Test
    void enforcesMandatoryAndNeverPropagation() {
        JdbcTxSupport support = support(List.of());

        TxException mandatoryFailure = assertThrows(TxException.class,
                                                    () -> support.transaction(Tx.Type.MANDATORY, () -> null));
        assertThat(mandatoryFailure.getMessage(),
                   is("@Tx.Mandatory requires an active local JDBC transaction."));
        support.transaction(Tx.Type.REQUIRED, () -> {
            assertThat(support.transaction(Tx.Type.MANDATORY, () -> "joined"), is("joined"));
            TxException neverFailure = assertThrows(TxException.class,
                                                    () -> support.transaction(Tx.Type.NEVER, () -> null));
            assertThat(neverFailure.getMessage(),
                       is("@Tx.Never cannot run inside an active local JDBC transaction."));
            return null;
        });
    }

    @Test
    void supportedNeverAndUnsupportedRunWithoutStartingATransaction() {
        RecordingLifeCycle events = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(events));

        assertThat(support.transaction(Tx.Type.SUPPORTED, () -> "supported"), is("supported"));
        assertThat(support.transaction(Tx.Type.NEVER, () -> "never"), is("never"));
        assertThat(support.transaction(Tx.Type.UNSUPPORTED, () -> "unsupported"), is("unsupported"));

        assertThat(events.count("begin"), is(0L));
        assertThat(events.count("commit"), is(0L));
        assertThat(events.count("rollback"), is(0L));
        assertThat(events.count("start:jdbc"), is(3L));
        assertThat(events.count("end"), is(3L));
    }

    @Test
    void caughtJoinedFailureStillMarksTransactionRollbackOnly() {
        RecordingLifeCycle events = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(events));

        assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
            assertThrows(TxException.class, () -> support.transaction(Tx.Type.SUPPORTED, () -> {
                throw new Exception("joined failure");
            }));
            return "ignored";
        }));

        assertThat(events.count("begin"), is(1L));
        assertThat(events.count("rollback"), is(1L));
        assertThat(events.count("commit"), is(0L));
    }

    @Test
    void unsupportedSuspendsWithoutStartingAnotherTransaction() {
        RecordingLifeCycle events = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(events));

        support.transaction(Tx.Type.REQUIRED, () -> {
            support.transaction(Tx.Type.UNSUPPORTED, () -> null);
            return null;
        });

        assertThat(events.eventKinds(),
                   is(List.of("start:jdbc",
                              "begin",
                              "start:jdbc",
                              "suspend",
                              "resume",
                              "end",
                              "commit",
                              "end")));
    }

    @Test
    void resumeFailureDoesNotReplaceTheTransactionFailure() {
        RecordingLifeCycle events = new RecordingLifeCycle() {
            @Override
            public void resume(String txIdentity) {
                super.resume(txIdentity);
                throw new IllegalStateException("resume failed");
            }
        };
        JdbcTxSupport support = support(List.of(events));

        TxException failure = assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
            support.transaction(Tx.Type.NEW, () -> {
                throw new IllegalArgumentException("task failed");
            });
            return null;
        }));

        assertThat(failure.getCause(), instanceOf(IllegalArgumentException.class));
        assertThat(failure.getSuppressed().length, is(1));
        assertThat(events.count("rollback"), is(2L));
    }

    @Test
    void caughtResumeFailureKeepsOuterTransactionForRollback() {
        AtomicBoolean failResume = new AtomicBoolean(true);
        RecordingLifeCycle events = new RecordingLifeCycle() {
            @Override
            public void resume(String txIdentity) {
                super.resume(txIdentity);
                if (failResume.getAndSet(false)) {
                    throw new IllegalStateException("resume failed");
                }
            }
        };
        JdbcTxSupport support = support(List.of(events));
        AtomicBoolean joinedAfterFailure = new AtomicBoolean();

        assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
            assertThrows(TxException.class, () -> support.transaction(Tx.Type.NEW, () -> null));
            support.transaction(Tx.Type.SUPPORTED, () -> {
                joinedAfterFailure.set(true);
                return null;
            });
            return null;
        }));

        assertThat(joinedAfterFailure.get(), is(true));
        assertThat(events.count("begin"), is(2L));
        assertThat(events.count("commit"), is(1L));
        assertThat(events.count("rollback"), is(1L));
    }

    @Test
    void suspendFailureRestoresOuterTransactionAndSkipsNestedTask() {
        AtomicBoolean failSuspend = new AtomicBoolean(true);
        RecordingLifeCycle events = new RecordingLifeCycle() {
            @Override
            public void suspend(String txIdentity) {
                super.suspend(txIdentity);
                if (failSuspend.getAndSet(false)) {
                    throw new IllegalStateException("suspend failed");
                }
            }
        };
        JdbcTxSupport support = support(List.of(events));
        AtomicBoolean nestedTaskInvoked = new AtomicBoolean();

        assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
            assertThrows(TxException.class, () -> support.transaction(Tx.Type.NEW, () -> {
                nestedTaskInvoked.set(true);
                return null;
            }));
            assertThat(support.transaction(Tx.Type.SUPPORTED, () -> "joined"), is("joined"));
            return null;
        }));

        assertThat(nestedTaskInvoked.get(), is(false));
        assertThat(events.count("begin"), is(1L));
        assertThat(events.count("resume"), is(1L));
        assertThat(events.count("rollback"), is(1L));
    }

    @Test
    void listenerFailureDoesNotSkipOtherListenersAndStateCanBeReused() {
        AtomicBoolean failBegin = new AtomicBoolean(true);
        RecordingLifeCycle failing = new RecordingLifeCycle() {
            @Override
            public void begin(String txIdentity) {
                super.begin(txIdentity);
                if (failBegin.getAndSet(false)) {
                    throw new IllegalStateException("begin failed");
                }
            }
        };
        RecordingLifeCycle following = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(failing, following));

        assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> null));
        assertThat(following.count("begin"), is(1L));
        assertThat(following.count("rollback"), is(1L));

        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "next"), is("next"));
        assertThat(following.count("begin"), is(2L));
        assertThat(following.count("commit"), is(1L));
    }

    @Test
    void startFailureCleansUpEveryListenerAndStateCanBeReused() {
        AtomicBoolean failStart = new AtomicBoolean(true);
        RecordingLifeCycle failing = new RecordingLifeCycle() {
            @Override
            public void start(String type) {
                super.start(type);
                if (failStart.getAndSet(false)) {
                    throw new IllegalStateException("start failed");
                }
            }
        };
        RecordingLifeCycle following = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(failing, following));

        assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> null));
        assertThat(following.eventKinds(), is(List.of("start:jdbc", "end")));

        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "next"), is("next"));
        assertThat(following.count("begin"), is(1L));
        assertThat(following.count("commit"), is(1L));
        assertThat(following.count("end"), is(2L));
    }

    @Test
    void commitFailureReachesEveryListenerAndLeavesNoThreadState() {
        AtomicBoolean failCommit = new AtomicBoolean(true);
        RecordingLifeCycle failing = new RecordingLifeCycle() {
            @Override
            public void commit(String txIdentity) {
                super.commit(txIdentity);
                if (failCommit.getAndSet(false)) {
                    throw new IllegalStateException("commit failed");
                }
            }
        };
        RecordingLifeCycle following = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(failing, following));

        TxException failure = assertThrows(TxException.class,
                                           () -> support.transaction(Tx.Type.REQUIRED, () -> null));
        assertThat(failure.getMessage(),
                   is("The local JDBC transaction was committed, but a later transaction lifecycle notification "
                              + "failed during commit. The committed work must not be retried automatically."));
        assertThat(failure.getCause().getCause().getMessage(), is("commit failed"));
        assertThat(following.count("commit"), is(1L));
        assertThat(following.count("end"), is(1L));

        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "next"), is("next"));
        assertThat(following.count("begin"), is(2L));
        assertThat(following.count("commit"), is(2L));
    }

    @Test
    void endFailureReachesEveryListenerAfterCompletionAndStateCanBeReused() {
        AtomicBoolean failEnd = new AtomicBoolean(true);
        RecordingLifeCycle failing = new RecordingLifeCycle() {
            @Override
            public void end() {
                super.end();
                if (failEnd.getAndSet(false)) {
                    throw new IllegalStateException("end failed");
                }
            }
        };
        RecordingLifeCycle following = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(failing, following));

        TxException failure = assertThrows(TxException.class,
                                           () -> support.transaction(Tx.Type.REQUIRED, () -> null));
        assertThat(failure.getMessage(),
                   is("The local JDBC transaction was committed, but a later transaction lifecycle notification "
                              + "failed during end. The committed work must not be retried automatically."));
        assertThat(failure.getCause().getCause().getMessage(), is("end failed"));
        assertThat(following.count("commit"), is(1L));
        assertThat(following.count("end"), is(1L));

        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "next"), is("next"));
        assertThat(following.count("commit"), is(2L));
        assertThat(following.count("end"), is(2L));
    }

    @Test
    void rollbackFailureIsSuppressedOnTheTaskFailure() {
        RecordingLifeCycle events = new RecordingLifeCycle() {
            @Override
            public void rollback(String txIdentity) {
                super.rollback(txIdentity);
                throw new IllegalStateException("rollback failed");
            }
        };
        JdbcTxSupport support = support(List.of(events));

        TxException failure = assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
            throw new IllegalArgumentException("task failed");
        }));

        assertThat(failure.getCause(), instanceOf(IllegalArgumentException.class));
        assertThat(failure.getSuppressed().length, is(1));
        assertThat(events.count("rollback"), is(1L));
    }

    @Test
    void interruptedTaskRestoresInterruptStatusAndRollsBack() {
        RecordingLifeCycle events = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(events));

        try {
            TxException failure = assertThrows(TxException.class,
                                               () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                   throw new InterruptedException("interrupted");
                                               }));

            assertThat(failure.getCause(), instanceOf(InterruptedException.class));
            assertThat(Thread.currentThread().isInterrupted(), is(true));
            assertThat(events.count("rollback"), is(1L));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void transactionContextIsNotInheritedByVirtualThreads() throws InterruptedException {
        JdbcTxSupport support = support(List.of());
        AtomicReference<Throwable> virtualThreadFailure = new AtomicReference<>();
        AtomicBoolean mandatoryRejected = new AtomicBoolean();

        support.transaction(Tx.Type.REQUIRED, () -> {
            Thread thread = Thread.ofVirtual().start(() -> {
                try {
                    support.transaction(Tx.Type.MANDATORY, () -> null);
                } catch (TxException _) {
                    mandatoryRejected.set(true);
                } catch (Throwable failure) {
                    virtualThreadFailure.set(failure);
                }
            });
            thread.join();
            return null;
        });

        assertThat(virtualThreadFailure.get(), is(nullValue()));
        assertThat(mandatoryRejected.get(), is(true));
    }

    @Test
    void reusesTheSameExecutorWorkerAfterSuccessAndFailure() throws Exception {
        JdbcTxSupport support = support(List.of());
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertThat(executor.submit(() -> support.transaction(Tx.Type.REQUIRED, () -> "success")).get(),
                       is("success"));

            ExecutionException failure = assertThrows(ExecutionException.class,
                                                      () -> executor.submit(
                                                              () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                                  throw new IllegalStateException("failure");
                                                              }))
                                                              .get());
            assertThat(failure.getCause(), instanceOf(TxException.class));

            assertThat(executor.submit(() -> {
                try {
                    support.transaction(Tx.Type.MANDATORY, () -> null);
                    return false;
                } catch (TxException _) {
                    return true;
                }
            }).get(), is(true));
            assertThat(executor.submit(() -> support.transaction(Tx.Type.REQUIRED, () -> "reused")).get(),
                       is("reused"));
        }
    }

    @Test
    void originalTaskFailureRemainsPrimaryWhenResumeAlsoFails() {
        IllegalArgumentException taskFailure = new IllegalArgumentException("task failed");
        RecordingLifeCycle events = new RecordingLifeCycle() {
            @Override
            public void resume(String txIdentity) {
                super.resume(txIdentity);
                throw new IllegalStateException("resume failed");
            }
        };
        JdbcTxSupport support = support(List.of(events));

        TxException failure = assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
            support.transaction(Tx.Type.NEW, () -> {
                throw taskFailure;
            });
            return null;
        }));

        assertThat(failure.getCause(), sameInstance(taskFailure));
        assertThat(failure.getSuppressed().length, is(1));
    }

    @Test
    void lifecycleFailuresNotifyListenersInEveryPositionAndAllowThreadReuse() {
        for (String event : List.of("start", "begin", "suspend", "resume", "commit", "rollback", "end")) {
            for (int failingPosition = 0; failingPosition < 3; failingPosition++) {
                List<RecordingLifeCycle> listeners = new ArrayList<>();
                for (int position = 0; position < 3; position++) {
                    listeners.add(position == failingPosition
                                          ? new OneShotFailingLifeCycle(event)
                                          : new RecordingLifeCycle());
                }
                JdbcTxSupport support = support(List.copyOf(listeners));

                assertThrows(RuntimeException.class, () -> exerciseLifecycleFailure(support, event));

                for (RecordingLifeCycle listener : listeners) {
                    assertThat(event + " was skipped for listener " + failingPosition,
                               listener.count(event.equals("start") ? "start:jdbc" : event),
                               is(1L));
                }
                assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
            }
        }
    }

    /**
     * Proves a fatal listener failure remains fatal regardless of whether a
     * recoverable listener fails before or after it. Dispatch must still reach
     * later listeners and leave the thread usable.
     */
    @Test
    void fatalListenerFailureOutranksPeerRuntimeFailureWithoutStoppingDispatch() {
        for (String event : List.of("start", "begin", "suspend", "resume", "commit", "rollback", "end")) {
            for (boolean fatalFirst : List.of(false, true)) {
                IllegalStateException runtimeFailure = new IllegalStateException(event + " runtime failure");
                OutOfMemoryError fatalFailure = new OutOfMemoryError(event + " fatal failure");
                RecordingLifeCycle runtimeListener = new OneShotFailingLifeCycle(event, runtimeFailure);
                RecordingLifeCycle fatalListener = new OneShotFailingLifeCycle(event, fatalFailure);
                RecordingLifeCycle following = new RecordingLifeCycle();
                List<RecordingLifeCycle> observers = fatalFirst
                        ? List.of(fatalListener, runtimeListener, following)
                        : List.of(runtimeListener, fatalListener, following);
                JdbcTxSupport support = support(observers);

                Throwable reportedFailure;
                if (event.equals("rollback")) {
                    TxException applicationFailure =
                            assertThrows(TxException.class, () -> exerciseLifecycleFailure(support, event));
                    assertThat(applicationFailure.getSuppressed().length, is(1));
                    reportedFailure = applicationFailure.getSuppressed()[0];
                } else {
                    reportedFailure = assertThrows(OutOfMemoryError.class,
                                                   () -> exerciseLifecycleFailure(support, event));
                }

                assertThat(reportedFailure, sameInstance(fatalFailure));
                assertThat(fatalFailure.getSuppressed().length, is(1));
                assertThat(fatalFailure.getSuppressed()[0], sameInstance(runtimeFailure));
                assertThat(following.count(event.equals("start") ? "start:jdbc" : event), is(1L));
                assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
            }
        }
    }

    /**
     * Proves a fatal completion-observer failure cannot be hidden by an
     * earlier unknown-outcome transaction exception.
     */
    @Test
    void fatalObserverFailureOutranksUnknownCompletionFailure() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true, false, false);
        doThrow(new SQLException("commit failed")).when(connection).commit();
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        OutOfMemoryError fatalFailure = new OutOfMemoryError("commit observer fatal failure");
        TxLifeCycle observer = new OneShotFailingLifeCycle("commit", fatalFailure);
        JdbcTxSupport support = new JdbcTxSupport(manager, List.of(manager, observer));

        OutOfMemoryError reportedFailure = assertThrows(OutOfMemoryError.class,
                                                        () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                            manager.acquire(dataSource).close();
                                                            return null;
                                                        }));

        assertThat(reportedFailure, sameInstance(fatalFailure));
        assertThat(fatalFailure.getSuppressed().length, is(1));
        assertThat(fatalFailure.getSuppressed()[0], instanceOf(TxException.class));
        assertThat(fatalFailure.getSuppressed()[0].getMessage(),
                   is("The local JDBC transaction commit failed, and the outcome is unknown."));
        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
    }

    /**
     * Proves two listeners returning the same failure instance cannot stop a
     * lifecycle event from reaching a later listener or poison the thread.
     */
    @Test
    void sharedListenerFailureDoesNotStopLifecycleDispatch() {
        for (String event : List.of("start", "begin", "suspend", "resume", "commit", "rollback", "end")) {
            IllegalStateException sharedFailure = new IllegalStateException(event + " failed");
            RecordingLifeCycle first = new OneShotFailingLifeCycle(event, sharedFailure);
            RecordingLifeCycle second = new OneShotFailingLifeCycle(event, sharedFailure);
            RecordingLifeCycle following = new RecordingLifeCycle();
            JdbcTxSupport support = support(List.of(first, second, following));

            RuntimeException reportedFailure =
                    assertThrows(RuntimeException.class, () -> exerciseLifecycleFailure(support, event));

            assertThat(following.count(event.equals("start") ? "start:jdbc" : event), is(1L));
            assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
            assertThat(reportedFailure, instanceOf(TxException.class));
            TxException failure = (TxException) reportedFailure;

            Throwable lifecycleFailure;
            if (event.equals("rollback")) {
                assertThat(failure.getSuppressed().length, is(1));
                lifecycleFailure = failure.getSuppressed()[0];
            } else if (event.equals("resume") || event.equals("commit") || event.equals("end")) {
                assertThat(failure.getMessage(),
                           is("The local JDBC transaction was committed, but a later transaction lifecycle "
                                      + "notification failed during " + event
                                      + ". The committed work must not be retried automatically."));
                lifecycleFailure = failure.getCause();
            } else {
                lifecycleFailure = failure;
            }
            assertThat(lifecycleFailure.getCause(), sameInstance(sharedFailure));
        }
    }

    /**
     * Proves a fatal end notification outranks a recoverable post-commit
     * notification while retaining the confirmed-commit diagnostic.
     */
    @Test
    void fatalEndFailureOutranksConfirmedCommitObserverFailure() {
        IllegalStateException commitFailure = new IllegalStateException("commit observer failed");
        OutOfMemoryError endFailure = new OutOfMemoryError("end observer failed");
        RecordingLifeCycle following = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(new OneShotFailingLifeCycle("commit", commitFailure),
                                               new OneShotFailingLifeCycle("end", endFailure),
                                               following));

        OutOfMemoryError reportedFailure = assertThrows(OutOfMemoryError.class,
                                                        () -> support.transaction(Tx.Type.REQUIRED, () -> null));

        assertThat(reportedFailure, sameInstance(endFailure));
        assertThat(endFailure.getSuppressed().length, is(1));
        assertThat(endFailure.getSuppressed()[0], instanceOf(TxException.class));
        assertThat(endFailure.getSuppressed()[0].getMessage(),
                   is("The local JDBC transaction was committed, but a later transaction lifecycle notification "
                              + "failed during commit. The committed work must not be retried automatically."));
        assertThat(endFailure.getSuppressed()[0].getCause().getCause(), sameInstance(commitFailure));
        assertThat(following.count("commit"), is(1L));
        assertThat(following.count("end"), is(1L));
        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
    }

    /**
     * Proves an application task failure remains primary when the later end
     * notification is fatal and all listeners still receive cleanup.
     */
    @Test
    void taskFailureRemainsPrimaryWhenEndFailsFatally() {
        IllegalArgumentException taskFailure = new IllegalArgumentException("task failed");
        OutOfMemoryError endFailure = new OutOfMemoryError("end observer failed");
        RecordingLifeCycle following = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(new OneShotFailingLifeCycle("end", endFailure), following));

        TxException reportedFailure = assertThrows(TxException.class,
                                                   () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                       throw taskFailure;
                                                   }));

        assertThat(reportedFailure.getCause(), sameInstance(taskFailure));
        assertThat(reportedFailure.getSuppressed().length, is(1));
        assertThat(reportedFailure.getSuppressed()[0], sameInstance(endFailure));
        assertThat(following.count("rollback"), is(1L));
        assertThat(following.count("end"), is(1L));
        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
    }

    /**
     * Proves fatal end cleanup outranks an earlier start infrastructure
     * failure, prevents task invocation, and leaves the thread reusable.
     */
    @Test
    void fatalEndCleanupFailureOutranksStartFailure() {
        IllegalStateException startFailure = new IllegalStateException("start observer failed");
        OutOfMemoryError endFailure = new OutOfMemoryError("end observer failed");
        AtomicBoolean invoked = new AtomicBoolean();
        RecordingLifeCycle following = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(new OneShotFailingLifeCycle("start", startFailure),
                                               new OneShotFailingLifeCycle("end", endFailure),
                                               following));

        OutOfMemoryError reportedFailure = assertThrows(OutOfMemoryError.class,
                                                        () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                            invoked.set(true);
                                                            return null;
                                                        }));

        assertThat(reportedFailure, sameInstance(endFailure));
        assertThat(endFailure.getSuppressed().length, is(1));
        assertThat(endFailure.getSuppressed()[0], instanceOf(TxException.class));
        assertThat(endFailure.getSuppressed()[0].getCause(), sameInstance(startFailure));
        assertThat(invoked.get(), is(false));
        assertThat(following.count("start:jdbc"), is(1L));
        assertThat(following.count("end"), is(1L));
        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
    }

    /**
     * Proves a fatal compensating resume failure outranks a recoverable
     * suspend failure before the requested nested task can run.
     */
    @Test
    void fatalCompensatingResumeFailureOutranksSuspendFailure() {
        IllegalStateException suspendFailure = new IllegalStateException("suspend observer failed");
        OutOfMemoryError resumeFailure = new OutOfMemoryError("resume observer failed");
        AtomicBoolean nestedInvoked = new AtomicBoolean();
        RecordingLifeCycle following = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(new OneShotFailingLifeCycle("suspend", suspendFailure),
                                               new OneShotFailingLifeCycle("resume", resumeFailure),
                                               following));

        OutOfMemoryError reportedFailure = assertThrows(OutOfMemoryError.class,
                                                        () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                            support.transaction(Tx.Type.NEW, () -> {
                                                                nestedInvoked.set(true);
                                                                return null;
                                                            });
                                                            return null;
                                                        }));

        assertThat(reportedFailure, sameInstance(resumeFailure));
        assertThat(resumeFailure.getSuppressed().length, is(1));
        assertThat(resumeFailure.getSuppressed()[0], instanceOf(TxException.class));
        assertThat(resumeFailure.getSuppressed()[0].getCause(), sameInstance(suspendFailure));
        assertThat(nestedInvoked.get(), is(false));
        assertThat(following.count("suspend"), is(1L));
        assertThat(following.count("resume"), is(1L));
        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
    }

    /**
     * Proves an application failure from a NEW task remains primary when
     * resuming its outer transaction fails fatally.
     */
    @Test
    void newTaskFailureRemainsPrimaryWhenResumeFailsFatally() {
        IllegalArgumentException taskFailure = new IllegalArgumentException("task failed");
        OutOfMemoryError resumeFailure = new OutOfMemoryError("resume observer failed");
        JdbcTxSupport support = support(List.of(new OneShotFailingLifeCycle("resume", resumeFailure)));

        TxException reportedFailure = assertThrows(TxException.class,
                                                   () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                       support.transaction(Tx.Type.NEW, () -> {
                                                           throw taskFailure;
                                                       });
                                                       return null;
                                                   }));

        assertThat(reportedFailure.getCause(), sameInstance(taskFailure));
        assertThat(reportedFailure.getSuppressed().length, is(1));
        assertThat(reportedFailure.getSuppressed()[0], sameInstance(resumeFailure));
        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
    }

    /**
     * Proves a fatal outer resume failure outranks infrastructure failure
     * while beginning a NEW transaction and leaves the thread reusable.
     */
    @Test
    void fatalResumeFailureOutranksNewInfrastructureFailure() {
        IllegalStateException beginFailure = new IllegalStateException("begin observer failed");
        OutOfMemoryError resumeFailure = new OutOfMemoryError("resume observer failed");
        AtomicBoolean nestedInvoked = new AtomicBoolean();
        AtomicBoolean outerBegun = new AtomicBoolean();
        AtomicBoolean failNestedBegin = new AtomicBoolean(true);
        RecordingLifeCycle beginObserver = new RecordingLifeCycle() {
            @Override
            public void begin(String txIdentity) {
                super.begin(txIdentity);
                if (outerBegun.getAndSet(true) && failNestedBegin.getAndSet(false)) {
                    throw beginFailure;
                }
            }
        };
        JdbcTxSupport support = support(List.of(beginObserver,
                                               new OneShotFailingLifeCycle("resume", resumeFailure)));

        OutOfMemoryError reportedFailure = assertThrows(OutOfMemoryError.class,
                                                        () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                            support.transaction(Tx.Type.NEW, () -> {
                                                                nestedInvoked.set(true);
                                                                return null;
                                                            });
                                                            return null;
                                                        }));

        assertThat(reportedFailure, sameInstance(resumeFailure));
        assertThat(resumeFailure.getSuppressed().length, is(1));
        assertThat(resumeFailure.getSuppressed()[0], instanceOf(TxException.class));
        assertThat(resumeFailure.getSuppressed()[0].getCause(), sameInstance(beginFailure));
        assertThat(nestedInvoked.get(), is(false));
        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
    }

    /**
     * Proves an application failure from an UNSUPPORTED task remains primary
     * when restoring the suspended outer transaction fails fatally.
     */
    @Test
    void unsupportedTaskFailureRemainsPrimaryWhenResumeFailsFatally() {
        IllegalArgumentException taskFailure = new IllegalArgumentException("task failed");
        OutOfMemoryError resumeFailure = new OutOfMemoryError("resume observer failed");
        JdbcTxSupport support = support(List.of(new OneShotFailingLifeCycle("resume", resumeFailure)));

        TxException reportedFailure = assertThrows(TxException.class,
                                                   () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                       support.transaction(Tx.Type.UNSUPPORTED, () -> {
                                                           throw taskFailure;
                                                       });
                                                       return null;
                                                   }));

        assertThat(reportedFailure.getCause(), sameInstance(taskFailure));
        assertThat(reportedFailure.getSuppressed().length, is(1));
        assertThat(reportedFailure.getSuppressed()[0], sameInstance(resumeFailure));
        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
    }

    /**
     * Proves rollback initiated by an application failure preserves that
     * failure even when the rollback observer reports a fatal error.
     */
    @Test
    void taskFailureRemainsPrimaryWhenRollbackFailsFatally() {
        IllegalArgumentException taskFailure = new IllegalArgumentException("task failed");
        OutOfMemoryError rollbackFailure = new OutOfMemoryError("rollback observer failed");
        JdbcTxSupport support = support(List.of(new OneShotFailingLifeCycle("rollback", rollbackFailure)));

        TxException reportedFailure = assertThrows(TxException.class,
                                                   () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                       throw taskFailure;
                                                   }));

        assertThat(reportedFailure.getCause(), sameInstance(taskFailure));
        assertThat(reportedFailure.getSuppressed().length, is(1));
        assertThat(reportedFailure.getSuppressed()[0], sameInstance(rollbackFailure));
        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
    }

    /**
     * Proves a fatal rollback failure outranks the infrastructure exception
     * which caused begin compensation before any task work executes.
     */
    @Test
    void fatalRollbackFailureOutranksBeginInfrastructureFailure() {
        IllegalStateException beginFailure = new IllegalStateException("begin observer failed");
        OutOfMemoryError rollbackFailure = new OutOfMemoryError("rollback observer failed");
        AtomicBoolean invoked = new AtomicBoolean();
        JdbcTxSupport support = support(List.of(new OneShotFailingLifeCycle("begin", beginFailure),
                                               new OneShotFailingLifeCycle("rollback", rollbackFailure)));

        OutOfMemoryError reportedFailure = assertThrows(OutOfMemoryError.class,
                                                        () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                            invoked.set(true);
                                                            return null;
                                                        }));

        assertThat(reportedFailure, sameInstance(rollbackFailure));
        assertThat(rollbackFailure.getSuppressed().length, is(1));
        assertThat(rollbackFailure.getSuppressed()[0], instanceOf(TxException.class));
        assertThat(rollbackFailure.getSuppressed()[0].getCause(), sameInstance(beginFailure));
        assertThat(invoked.get(), is(false));
        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
    }

    /**
     * Proves a fatal rollback failure outranks a synthetic rollback-only
     * exception when the outer application task itself completed normally.
     */
    @Test
    void fatalRollbackFailureOutranksSyntheticRollbackOnlyFailure() {
        OutOfMemoryError rollbackFailure = new OutOfMemoryError("rollback observer failed");
        JdbcTxSupport support = support(List.of(new OneShotFailingLifeCycle("rollback", rollbackFailure)));

        OutOfMemoryError reportedFailure = assertThrows(OutOfMemoryError.class,
                                                        () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                            assertThrows(TxException.class,
                                                                         () -> support.transaction(
                                                                                 Tx.Type.SUPPORTED,
                                                                                 () -> {
                                                                                     throw new IllegalStateException(
                                                                                             "joined task failed");
                                                                                 }));
                                                            return null;
                                                        }));

        assertThat(reportedFailure, sameInstance(rollbackFailure));
        assertThat(rollbackFailure.getSuppressed().length, is(1));
        assertThat(rollbackFailure.getSuppressed()[0], instanceOf(TxException.class));
        assertThat(rollbackFailure.getSuppressed()[0].getMessage(),
                   is("The local JDBC transaction was marked for rollback."));
        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
    }

    /**
     * Proves one throwable reused by the task and end observer cannot trigger
     * self-suppression, skip a later listener, or poison thread state.
     */
    @Test
    void sharedTaskAndEndFailureDoesNotAttemptSelfSuppression() {
        TxException sharedFailure = new TxException("shared failure");
        RecordingLifeCycle following = new RecordingLifeCycle();
        JdbcTxSupport support = support(List.of(new OneShotFailingLifeCycle("end", sharedFailure), following));

        TxException reportedFailure = assertThrows(TxException.class,
                                                   () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                       throw sharedFailure;
                                                   }));

        assertThat(reportedFailure, sameInstance(sharedFailure));
        assertThat(sharedFailure.getSuppressed().length, is(0));
        assertThat(following.count("end"), is(1L));
        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "reused"), is("reused"));
    }

    /**
     * Drives the transaction shape which delivers one selected lifecycle event.
     *
     * @param support transaction support
     * @param event event configured to fail
     */
    private static void exerciseLifecycleFailure(JdbcTxSupport support, String event) {
        switch (event) {
        case "start", "begin", "commit", "end" ->
            support.transaction(Tx.Type.REQUIRED, () -> null);
        case "rollback" ->
            support.transaction(Tx.Type.REQUIRED, () -> {
                throw new IllegalArgumentException("force rollback");
            });
        case "suspend" ->
            support.transaction(Tx.Type.REQUIRED, () ->
                    support.transaction(Tx.Type.NEW, () -> null));
        case "resume" ->
            support.transaction(Tx.Type.REQUIRED, () ->
                    support.transaction(Tx.Type.NEW, () -> null));
        default -> throw new AssertionError("Unknown lifecycle event " + event);
        }
    }

    /**
     * Creates transaction support with its dedicated connection manager also
     * present in the lifecycle listener list used by production injection.
     *
     * @param observers test lifecycle observers
     * @return local JDBC transaction support
     */
    private static JdbcTxSupport support(List<? extends TxLifeCycle> observers) {
        JdbcTransactionConnectionManager connectionManager = new JdbcTransactionConnectionManager();
        List<TxLifeCycle> listeners = new ArrayList<>(observers);
        listeners.add(connectionManager);
        return new JdbcTxSupport(connectionManager, List.copyOf(listeners));
    }

    private static class RecordingLifeCycle implements TxLifeCycle {
        /**
         * Events recorded in delivery order.
         */
        private final List<String> events = new ArrayList<>();

        @Override
        public void start(String type) {
            Objects.requireNonNull(type, "The transaction type must not be null.");
            events.add("start:" + type);
        }

        @Override
        public void end() {
            events.add("end");
        }

        @Override
        public void begin(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
            assertThat(txIdentity.isBlank(), is(false));
            events.add("begin:" + txIdentity);
        }

        @Override
        public void commit(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
            events.add("commit:" + txIdentity);
        }

        @Override
        public void rollback(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
            events.add("rollback:" + txIdentity);
        }

        @Override
        public void suspend(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
            events.add("suspend:" + txIdentity);
        }

        @Override
        public void resume(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
            events.add("resume:" + txIdentity);
        }

        /**
         * Removes transaction identities so tests can compare event flow.
         *
         * @return normalized event kinds
         */
        private List<String> eventKinds() {
            return events.stream().map(event -> event.substring(0, event.indexOf(':') < 0
                    ? event.length()
                    : event.indexOf(':'))).map(kind -> kind.equals("start") ? "start:jdbc" : kind).toList();
        }

        /**
         * Counts normalized events of one kind.
         *
         * @param kind event kind
         * @return matching event count
         */
        private long count(String kind) {
            return eventKinds().stream().filter(kind::equals).count();
        }
    }

    /**
     * Listener which fails one selected event exactly once.
     */
    private static final class OneShotFailingLifeCycle extends RecordingLifeCycle {
        /**
         * Event which fails on its first delivery.
         */
        private final String failingEvent;

        /**
         * Failure thrown for the configured event.
         */
        private final Throwable failure;

        /**
         * Whether the configured failure has already occurred.
         */
        private boolean failed;

        private OneShotFailingLifeCycle(String failingEvent) {
            this(failingEvent, new IllegalStateException(failingEvent + " failed"));
        }

        private OneShotFailingLifeCycle(String failingEvent, Throwable failure) {
            this.failingEvent = failingEvent;
            this.failure = failure;
        }

        @Override
        public void start(String type) {
            super.start(type);
            fail("start");
        }

        @Override
        public void begin(String txIdentity) {
            super.begin(txIdentity);
            fail("begin");
        }

        @Override
        public void suspend(String txIdentity) {
            super.suspend(txIdentity);
            fail("suspend");
        }

        @Override
        public void resume(String txIdentity) {
            super.resume(txIdentity);
            fail("resume");
        }

        @Override
        public void commit(String txIdentity) {
            super.commit(txIdentity);
            fail("commit");
        }

        @Override
        public void rollback(String txIdentity) {
            super.rollback(txIdentity);
            fail("rollback");
        }

        @Override
        public void end() {
            super.end();
            fail("end");
        }

        /**
         * Fails the configured event on its first delivery.
         *
         * @param event delivered event
         */
        private void fail(String event) {
            if (!failed && failingEvent.equals(event)) {
                failed = true;
                if (failure instanceof Error error) {
                    throw error;
                }
                throw (RuntimeException) failure;
            }
        }
    }

}
