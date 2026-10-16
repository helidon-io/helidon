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

package io.helidon.webserver.grpc;

import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Schedules gRPC context expiration without running application cancellation listeners on the shared timer.
 */
final class GrpcDeadlineExecutor extends ScheduledThreadPoolExecutor {
    private static final ThreadFactory EXPIRY_THREADS = Thread.ofVirtual()
            .name("helidon-grpc-server-deadline-expiry-", 0)
            .factory();

    GrpcDeadlineExecutor() {
        super(1, Thread.ofPlatform().daemon().name("grpc-server-deadline", 0).factory());
        setRemoveOnCancelPolicy(true);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        var task = new DeadlineTask(command);
        task.scheduled = super.schedule(task::expire, delay, unit);
        if (task.isCancelled()) {
            task.scheduled.cancel(false);
        }
        return task;
    }

    private static final class DeadlineTask extends FutureTask<Void> implements ScheduledFuture<Void> {
        private volatile ScheduledFuture<?> scheduled;

        private DeadlineTask(Runnable command) {
            super(command, null);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return scheduled.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed other) {
            return scheduled.compareTo(other instanceof DeadlineTask task ? task.scheduled : other);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            ScheduledFuture<?> timer = scheduled;
            if (timer != null) {
                timer.cancel(false);
            }
            return cancelled;
        }

        private void expire() {
            if (!isDone()) {
                // A normally completed call cancels its timer; only actual expiry creates a virtual thread.
                EXPIRY_THREADS.newThread(this).start();
            }
        }
    }
}
