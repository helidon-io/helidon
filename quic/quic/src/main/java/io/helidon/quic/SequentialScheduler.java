/*
 * Copyright (c) 2016, 2026 Oracle and/or its affiliates.
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

package io.helidon.quic;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.Api;

import static java.util.Objects.requireNonNull;

/**
 * A scheduler of ( repeatable ) tasks that MUST be run sequentially.
 *
 * <p> This class can be used as a synchronization aid that assists a number of
 * parties in running a task in a mutually exclusive fashion.
 *
 * <p> To run the task, a party invokes {@code runOrSchedule}. To permanently
 * prevent the task from subsequent runs, the party invokes {@code stop}.
 *
 * <p> The parties can, but do not have to, operate in different threads.
 *
 * <p> The task can be either synchronous ( completes when its {@code run}
 * method returns ), or asynchronous ( completed when its
 * {@code DeferredCompleter} is explicitly completed ).
 *
 * <p> The next run of the task will not begin until the previous run has
 * finished.
 *
 * <p> The task may invoke {@code runOrSchedule} itself, which may be a normal
 * situation.
 */
@Api.Internal
public final class SequentialScheduler {

    /*
       Since the task is fixed and known beforehand, no blocking synchronization
       (locks, queues, etc.) is required. The job can be done solely using
       nonblocking primitives.

       The machinery below addresses two problems:

         1. Running the task in a sequential order (no concurrent runs):

                begin, end, begin, end...

         2. Avoiding indefinite recursion:

                begin
                  end
                    begin
                      end
                        ...

       Problem #1 is solved with a finite state machine with 4 states:

           BEGIN, AGAIN, END, and STOP.

       Problem #2 is solved with a "state modifier" OFFLOAD.

       Parties invoke `runOrSchedule()` to signal the task must run. A party
       that has invoked `runOrSchedule()` either begins the task or exploits the
       party that is either beginning the task or ending it.

       The party that is trying to end the task either ends it or begins it
       again.

       To avoid indefinite recursion, before re-running the task the
       TryEndDeferredCompleter sets the OFFLOAD bit, signalling to its "child"
       TryEndDeferredCompleter that this ("parent") TryEndDeferredCompleter is
       available and the "child" must offload the task on to the "parent". Then
       a race begins. Whichever invocation of TryEndDeferredCompleter.complete
       manages to unset OFFLOAD bit first does not do the work.

       There is at most 1 thread that is beginning the task and at most 2
       threads that are trying to end it: "parent" and "child". In case of a
       synchronous task "parent" and "child" are the same thread.
     */

    private static final int OFFLOAD = 1;
    private static final int AGAIN = 2;
    private static final int BEGIN = 4;
    private static final int STOP = 8;
    private static final int END = 16;
    private final AtomicInteger state = new AtomicInteger(END);
    private final RestartableTask restartableTask;
    private final DeferredCompleter completer;
    private final SchedulableTask schedulableTask;

    /**
     * Creates a scheduler for the supplied restartable task.
     *
     * @param restartableTask task body invoked by this scheduler
     */
    SequentialScheduler(RestartableTask restartableTask) {
        this.restartableTask = requireNonNull(restartableTask);
        this.completer = new TryEndDeferredCompleter();
        this.schedulableTask = new SchedulableTask();
    }

    /**
     * Creates a scheduler for the supplied restartable task.
     *
     * @param restartableTask task body invoked by the scheduler
     * @return scheduler instance
     */
    public static SequentialScheduler create(RestartableTask restartableTask) {
        return new SequentialScheduler(restartableTask);
    }

    /**
     * Returns a new {@code SequentialScheduler} that executes the provided
     * {@code mainLoop} from within a {@link LockingRestartableTask}.
     *
     * @param mainLoop The main loop of the new sequential scheduler
     * @return a new {@code SequentialScheduler} that executes the provided
     *        {@code mainLoop} from within a {@link LockingRestartableTask}.
     * <p>Note: This is equivalent to calling
     *        {@code new SequentialScheduler(new LockingRestartableTask(mainLoop))}
     *        The main loop must not perform any blocking operation.
     */
    public static SequentialScheduler lockingScheduler(Runnable mainLoop) {
        return new SequentialScheduler(LockingRestartableTask.create(mainLoop));
    }

    /**
     * Runs or schedules the task to be run.
     *
     * @implSpec The recursion which is possible here must be bounded:
     *
     *        <pre>{@code
     *            this.runOrSchedule()
     *                completer.complete()
     *                    this.runOrSchedule()
     *                        ...
     *        }</pre>
     * @implNote The recursion in this implementation has the maximum
     *        depth of 1.
     */
    public void runOrSchedule() {
        runOrSchedule(schedulableTask, null);
    }

    /**
     * Executes or schedules the task to be executed in the provided executor.
     *
     * <p> This method can be used when potential executing from a calling
     * thread is not desirable.
     *
     * @param executor An executor in which to execute the task, if the task needs
     *                to be executed.
     */
    public void runOrSchedule(Executor executor) {
        runOrSchedule(schedulableTask, requireNonNull(executor, "executor"));
    }

    /**
     * Tells whether, or not, this scheduler has been permanently stopped.
     *
     * <p> Should be used from inside the task to poll the status of the
     * scheduler, pretty much the same way as it is done for threads:
     * <pre>{@code
     *    if (!Thread.currentThread().isInterrupted()) {
     *        ...
     *    }
     * }</pre>
     *
     * @return {@code true} if the scheduler has been permanently stopped
     */
    public boolean isStopped() {
        return state.get() == STOP;
    }

    /**
     * Stops this scheduler.  Subsequent invocations of {@code runOrSchedule}
     * are effectively no-ops.
     *
     * <p> If the task has already begun, this invocation will not affect it,
     * unless the task itself uses {@code isStopped()} method to check the state
     * of the handler.
     */
    public void stop() {
        state.set(STOP);
    }

    private void runOrSchedule(SchedulableTask task, Executor executor) {
        while (true) {
            int s = state.get();
            if (s == END) {
                if (state.compareAndSet(END, BEGIN)) {
                    break;
                }
            } else if ((s & BEGIN) != 0) {
                // Tries to change the state to AGAIN, preserving OFFLOAD bit
                if (state.compareAndSet(s, AGAIN | (s & OFFLOAD))) {
                    return;
                }
            } else if ((s & AGAIN) != 0 || s == STOP) {
                /* In the case of AGAIN the scheduler does not provide
                   happens-before relationship between actions prior to
                   runOrSchedule() and actions that happen in task.run().
                   The reason is that no volatile write is done in this case,
                   and the call piggybacks on the call that has actually set
                   AGAIN state. */
                return;
            } else {
                // Non-existent state, or the one that cannot be offloaded
                throw new InternalError(String.valueOf(s));
            }
        }
        if (executor == null) {
            task.run();
        } else {
            try {
                executor.execute(task);
            } catch (RuntimeException | Error failure) {
                boolean replay = false;
                int current = state.get();
                while (current != STOP
                        && current != END
                        && !state.compareAndSet(current, END)) {
                    current = state.get();
                }
                if (current != STOP && current != END && (current & AGAIN) != 0) {
                    replay = true;
                }
                if (replay) {
                    try {
                        runOrSchedule();
                    } catch (Throwable replayFailure) {
                        if (failure != replayFailure) {
                            failure.addSuppressed(replayFailure);
                        }
                    }
                }
                throw failure;
            }
        }
    }

    /**
     * A restartable task.
     */
    @FunctionalInterface
    public interface RestartableTask {

        /**
         * The body of the task.
         *
         * @param taskCompleter A completer that must be invoked once, and only once,
         *                     when this task is logically finished
         */
        void run(DeferredCompleter taskCompleter);
    }

    /**
     * An interface to signal the completion of a {@link RestartableTask}.
     *
     * <p> The invocation of {@code complete} completes the task. The invocation
     * of {@code complete} may restart the task, if an attempt has previously
     * been made to run the task while it was already running.
     *
     * <p>Note: {@code DeferredCompleter} is useful when a task is not necessary
     *        complete when its {@code run} method returns, but will complete at a
     *        later time, and maybe in different thread. This type exists for
     *        readability purposes at use-sites only.
     */
    public abstract static class DeferredCompleter {

        /**
         * Extensible from this (outer) class ONLY.
         */
        private DeferredCompleter() {
        }

        /**
         * Completes the task. Must be called once, and once only.
         */
        public abstract void complete();
    }

    /**
     * A simple and self-contained task that completes once its {@code run}
     * method returns.
     */
    public abstract static class CompleteRestartableTask
            implements RestartableTask {
        /**
         * Creates a complete restartable task.
         */
        protected CompleteRestartableTask() {
        }

        @Override
        public final void run(DeferredCompleter taskCompleter) {
            try {
                run();
            } finally {
                taskCompleter.complete();
            }
        }

        /**
         * The body of the task.
         */
        protected abstract void run();
    }

    /**
     * A task that runs its main loop within a  block protected by a lock to provide
     * memory visibility between runs. Since the main loop can't run concurrently,
     * the lock shouldn't be contended and no deadlock should ever be possible.
     */
    public static final class LockingRestartableTask
            extends CompleteRestartableTask {

        private final Runnable mainLoop;
        private final Lock lock = new ReentrantLock();

        /**
         * Creates a locking restartable task.
         *
         * @param mainLoop runnable containing the task body
         */
        private LockingRestartableTask(Runnable mainLoop) {
            this.mainLoop = requireNonNull(mainLoop, "mainLoop");
        }

        /**
         * Creates a locking restartable task.
         *
         * @param mainLoop runnable containing the task body
         * @return locking restartable task
         */
        public static LockingRestartableTask create(Runnable mainLoop) {
            return new LockingRestartableTask(mainLoop);
        }

        @Override
        protected void run() {
            // The logics of the sequential scheduler should ensure that
            // the restartable task is running in only one thread at
            // a given time: there should never be contention.
            boolean locked = lock.tryLock();
            try {
                mainLoop.run();
            } finally {
                if (locked) {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * An auxiliary task that starts the restartable task:
     * {@code restartableTask.run(completer)}.
     */
    private final class SchedulableTask implements Runnable {
        @Override
        public void run() {
            restartableTask.run(completer);
        }
    }

    /**
     * The only concrete {@code DeferredCompleter} implementation.
     */
    private class TryEndDeferredCompleter extends DeferredCompleter {

        @Override
        public void complete() {
            while (true) {
                int s;
                s = state.get();
                while ((s & OFFLOAD) != 0) {
                    // Tries to offload ending of the task to the parent
                    if (state.compareAndSet(s, s & ~OFFLOAD)) {
                        return;
                    }
                    s = state.get();
                }
                while (true) {
                    if ((s & OFFLOAD) != 0) {
                        /* OFFLOAD bit can never be observed here. Otherwise
                           it would mean there is another invocation of
                           "complete" that can run the task. */
                        throw new InternalError(String.valueOf(s));
                    }
                    if (s == BEGIN) {
                        if (state.compareAndSet(BEGIN, END)) {
                            return;
                        }
                    } else if (s == AGAIN) {
                        if (state.compareAndSet(AGAIN, BEGIN | OFFLOAD)) {
                            break;
                        }
                    } else if (s == STOP) {
                        return;
                    } else if (s == END) {
                        throw new IllegalStateException("Duplicate completion");
                    } else {
                        // Non-existent state
                        throw new InternalError(String.valueOf(s));
                    }
                    s = state.get();
                }
                restartableTask.run(completer);
            }
        }
    }
}
