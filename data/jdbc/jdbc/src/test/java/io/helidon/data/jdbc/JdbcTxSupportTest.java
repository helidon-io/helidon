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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

class JdbcTxSupportTest {

    @Test
    void requiredBeginsAndCommitsOneTransaction() {
        RecordingLifeCycle events = new RecordingLifeCycle();
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));

        assertThat(support.transaction(Tx.Type.REQUIRED, () -> "result"), is("result"));

        assertThat(events.eventKinds(), is(List.of("start:jdbc", "begin", "commit", "end")));
    }

    @Test
    void nestedRequiredJoinsAndFailureMarksOuterRollbackOnly() {
        RecordingLifeCycle events = new RecordingLifeCycle();
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));

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
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));

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
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));

        assertThat(support.transaction(Tx.Type.NEW, () -> "result"), is("result"));

        assertThat(events.eventKinds(), is(List.of("start:jdbc", "begin", "commit", "end")));
    }

    @Test
    void enforcesMandatoryAndNeverPropagation() {
        JdbcTxSupport support = new JdbcTxSupport(List.of());

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
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));

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
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));

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
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));

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
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));

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
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));
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
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));
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
        JdbcTxSupport support = new JdbcTxSupport(List.of(failing, following));

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
        JdbcTxSupport support = new JdbcTxSupport(List.of(failing, following));

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
        JdbcTxSupport support = new JdbcTxSupport(List.of(failing, following));

        assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> null));
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
        JdbcTxSupport support = new JdbcTxSupport(List.of(failing, following));

        assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> null));
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
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));

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
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));

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
        JdbcTxSupport support = new JdbcTxSupport(List.of());
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
        JdbcTxSupport support = new JdbcTxSupport(List.of());
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
        JdbcTxSupport support = new JdbcTxSupport(List.of(events));

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
                JdbcTxSupport support = new JdbcTxSupport(List.copyOf(listeners));

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

    private static class RecordingLifeCycle implements TxLifeCycle {
        /**
         * Events recorded in delivery order.
         */
        private final List<String> events = new ArrayList<>();

        @Override
        public void start(String type) {
            events.add("start:" + type);
        }

        @Override
        public void end() {
            events.add("end");
        }

        @Override
        public void begin(String txIdentity) {
            assertThat(txIdentity.isBlank(), is(false));
            events.add("begin:" + txIdentity);
        }

        @Override
        public void commit(String txIdentity) {
            events.add("commit:" + txIdentity);
        }

        @Override
        public void rollback(String txIdentity) {
            events.add("rollback:" + txIdentity);
        }

        @Override
        public void suspend(String txIdentity) {
            events.add("suspend:" + txIdentity);
        }

        @Override
        public void resume(String txIdentity) {
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
         * Whether the configured failure has already occurred.
         */
        private boolean failed;

        private OneShotFailingLifeCycle(String failingEvent) {
            this.failingEvent = failingEvent;
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
                throw new IllegalStateException(event + " failed");
            }
        }
    }

}
