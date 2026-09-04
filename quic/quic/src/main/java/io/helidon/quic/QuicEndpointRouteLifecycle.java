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

package io.helidon.quic;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Coordinates endpoint-route ownership and removal completion.
 *
 * <p>Active owner and connection-ID count operations are serialized by the endpoint route lock. A removal observer that
 * wins the initial-to-removed state transition clears the initial owner outside that lock; the winning state transition
 * prevents any concurrent route operation from becoming active. Removal observation and final completion coordinate
 * through the lifecycle and completion state handles.
 */
final class QuicEndpointRouteLifecycle {
    private static final VarHandle STATE;
    private static final VarHandle COMPLETION;
    private static final CompletionStage<Void> SUCCESS_STAGE = CompletableFuture.completedStage(null);

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            STATE = lookup.findVarHandle(QuicEndpointRouteLifecycle.class, "state", State.class);
            COMPLETION = lookup.findVarHandle(QuicEndpointRouteLifecycle.class, "completion", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile State state = State.NEW;
    private QuicPacketReceiver owner;
    private volatile Object completion;
    private int connectionIdRouteCount;

    QuicEndpointRouteLifecycle(QuicPacketReceiver initialOwner) {
        owner = Objects.requireNonNull(initialOwner, "initialOwner");
    }

    boolean register(QuicPacketReceiver owner, int connectionIdRouteCount) {
        if (connectionIdRouteCount < 1) {
            throw new IllegalArgumentException("A connection requires at least one connection-ID route");
        }
        boolean registered = this.owner == owner && STATE.compareAndSet(this, State.NEW, State.ACTIVE);
        if (registered) {
            this.connectionIdRouteCount = connectionIdRouteCount;
        }
        return registered;
    }

    QuicPacketReceiver owner() {
        State current = state;
        return current == State.NEW || current == State.ACTIVE ? owner : null;
    }

    boolean isOwner(QuicPacketReceiver receiver) {
        return state == State.ACTIVE && owner == receiver;
    }

    boolean transfer(QuicPacketReceiver expectedOwner, QuicPacketReceiver newOwner) {
        Objects.requireNonNull(newOwner, "newOwner");
        if (!isOwner(expectedOwner)) {
            return false;
        }
        owner = newOwner;
        return true;
    }

    void connectionIdAdded(QuicPacketReceiver expectedOwner) {
        if (!isOwner(expectedOwner)) {
            throw new IllegalStateException("Connection-ID route added for a stale owner");
        }
        connectionIdRouteCount++;
    }

    boolean retireConnectionId(QuicPacketReceiver expectedOwner) {
        if (!isOwner(expectedOwner) || connectionIdRouteCount <= 1) {
            return false;
        }
        connectionIdRouteCount--;
        return true;
    }

    boolean beginRemoval(QuicPacketReceiver expectedOwner) {
        State current = state;
        boolean removing = (current == State.NEW || current == State.ACTIVE)
                && owner == expectedOwner
                && STATE.compareAndSet(this, current, State.REMOVING);
        if (removing) {
            owner = null;
            connectionIdRouteCount = 0;
        }
        return removing;
    }

    void completeRemoval() {
        if (STATE.compareAndSet(this, State.REMOVING, State.REMOVED)) {
            publishCompletion(null);
        }
    }

    CompletionStage<Void> whenRemoved() {
        if (state == State.NEW && STATE.compareAndSet(this, State.NEW, State.REMOVED)) {
            owner = null;
            publishCompletion(null);
        }
        for (;;) {
            Object current = completion;
            if (current == Completion.SUCCESS) {
                return SUCCESS_STAGE;
            }
            if (current instanceof CompletableFuture<?> currentFuture) {
                @SuppressWarnings("unchecked")
                CompletableFuture<Void> removed = (CompletableFuture<Void>) currentFuture;
                return removed.minimalCompletionStage();
            }
            CompletableFuture<Void> removed = new CompletableFuture<>();
            if (current instanceof Throwable failure) {
                removed.completeExceptionally(failure);
            }
            if (COMPLETION.compareAndSet(this, current, removed)) {
                return removed.minimalCompletionStage();
            }
        }
    }

    void completeRemovalExceptionally(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        if (STATE.compareAndSet(this, State.REMOVING, State.REMOVED)) {
            publishCompletion(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private void publishCompletion(Throwable failure) {
        Object result = failure == null ? Completion.SUCCESS : failure;
        for (;;) {
            Object current = completion;
            if (current == null) {
                if (COMPLETION.compareAndSet(this, null, result)) {
                    return;
                }
            } else if (current instanceof CompletableFuture<?> currentFuture) {
                CompletableFuture<Void> removed = (CompletableFuture<Void>) currentFuture;
                if (failure == null) {
                    removed.complete(null);
                } else {
                    removed.completeExceptionally(failure);
                }
                return;
            } else {
                return;
            }
        }
    }

    private enum State {
        NEW,
        ACTIVE,
        REMOVING,
        REMOVED
    }

    private enum Completion {
        SUCCESS
    }
}
