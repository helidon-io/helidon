/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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
package io.helidon.lra.coordinator;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Timer;

import org.eclipse.microprofile.lra.annotation.LRAStatus;

class LraImpl implements Lra {

    private static final System.Logger LOGGER = System.getLogger(LraImpl.class.getName());

    private final LazyValue<URI> coordinatorURL;
    private final Set<String> compensatorLinks = Collections.synchronizedSet(new HashSet<>());
    private final String lraId;
    private final Config config;
    private final List<LraImpl> children = Collections.synchronizedList(new ArrayList<>());
    private final List<ParticipantImpl> participants = new CopyOnWriteArrayList<>();
    private final AtomicReference<LRAStatus> status = new AtomicReference<>(LRAStatus.Active);
    private final Lock lock = new ReentrantLock();
    private final MeterRegistry registry = Metrics.globalRegistry();
    private final Counter lraCtr = registry.getOrCreate(Counter.builder("lractr"));
    private final Timer lrfLifeSpanTmr = registry.getOrCreate(Timer.builder("lralifespantmr"));
    private final Timer.Sample lraLifeSpanTmrSample = Timer.start(registry);
    private long timeout;
    private URI parentId;
    private boolean isChild;
    private long whenReadyToDelete = 0;

    LraImpl(CoordinatorService coordinatorService, String lraUUID, Config config) {
        lraId = lraUUID;
        this.config = config;
        lraCtr.increment();
        coordinatorURL = LazyValue.create(coordinatorService.coordinatorURL());
    }

    LraImpl(CoordinatorService coordinatorService, String lraUUID, URI parentId, Config config) {
        lraId = lraUUID;
        this.parentId = parentId;
        this.config = config;
        lraCtr.increment();
        coordinatorURL = LazyValue.create(coordinatorService.coordinatorURL());
    }

    @Override
    public String lraId() {
        return lraId;
    }

    @Override
    public String parentId() {
        return Optional.ofNullable(parentId).map(URI::toASCIIString).orElse(null);
    }

    @Override
    public boolean isChild() {
        return isChild;
    }

    void setChild(boolean child) {
        isChild = child;
    }

    @Override
    public long timeout() {
        return timeout;
    }

    void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public List<Participant> participants() {
        return this.participants.stream().map(Participant.class::cast).toList();
    }

    @Override
    public LRAStatus status() {
        return lraStatus().get();
    }

    void setStatus(LRAStatus status) {
        this.status.set(status);
    }

    long getWhenReadyToDelete() {
        return this.whenReadyToDelete;
    }

    void setWhenReadyToDelete(long whenReadyToDelete) {
        this.whenReadyToDelete = whenReadyToDelete;
    }

    void addParticipant(ParticipantImpl participant) {
        this.participants.add(participant);
    }

    void setupTimeout(long timeLimit) {
        if (timeLimit != 0) {
            this.timeout = System.currentTimeMillis() + timeLimit;
        } else {
            this.timeout = 0;
        }
    }

    boolean checkTimeout() {
        return timeout > 0 && timeout < System.currentTimeMillis();
    }

    void addParticipant(String compensatorLink) {
        if (compensatorLinks.add(compensatorLink)) {
            ParticipantImpl participant = new ParticipantImpl(config);
            participant.parseCompensatorLinks(compensatorLink);
            participants.add(participant);
        }
    }

    void removeParticipant(String compensatorUrl) {
        Set<Participant> forRemove = participants.stream()
                .filter(p -> p.equalCompensatorUris(compensatorUrl))
                .collect(Collectors.toSet());
        forRemove.forEach(participants::remove);
    }

    void addChild(LraImpl lra) {
        children.add(lra);
        lra.isChild = true;
    }

    Consumer<ClientRequestHeaders> headers() {
        return headers -> {
            headers.add(LRA_HTTP_CONTEXT_HEADER_NAME, lraContextId());
            headers.add(LRA_HTTP_ENDED_CONTEXT_HEADER_NAME, lraContextId());
            Optional.ofNullable(parentId)
                    .map(URI::toASCIIString)
                    .ifPresent(s -> headers.add(LRA_HTTP_PARENT_CONTEXT_HEADER_NAME, s));
            headers.add(LRA_HTTP_RECOVERY_HEADER_NAME, lraContextId() + "/recovery");
        };
    }

    void close() {
        Set<LRAStatus> allowedStatuses = Set.of(LRAStatus.Active, LRAStatus.Closing);
        if (LRAStatus.Closing != status.updateAndGet(old -> allowedStatuses.contains(old) ? LRAStatus.Closing : old)) {
            LOGGER.log(Level.WARNING, "Can't close LRA, it's already " + status.get().name() + " " + this.lraId);
            return;
        }
        lraLifeSpanTmrSample.stop(lrfLifeSpanTmr);
        if (lock.tryLock()) {
            try {
                sendComplete();
                // needs to go before nested close, so we know if nested was already closed
                // or not(non closed nested can't get @Forget call)
                forgetNested();
                for (LraImpl nestedLra : children) {
                    nestedLra.close();
                }
                trySendAfterLRA();
                markForDeletion();
            } finally {
                lock.unlock();
            }
        }
    }

    void cancel() {
        Set<LRAStatus> allowedStatuses = Set.of(LRAStatus.Active, LRAStatus.Cancelling);
        if (LRAStatus.Cancelling != status.updateAndGet(old -> allowedStatuses.contains(old) ? LRAStatus.Cancelling : old)
                && !isChild) { // nested can be compensated even if closed
            LOGGER.log(Level.WARNING, "Can't cancel LRA, it's already " + status.get().name() + " " + this.lraId);
            return;
        }
        lraLifeSpanTmrSample.stop(lrfLifeSpanTmr);
        for (LraImpl nestedLra : children) {
            nestedLra.cancel();
        }
        if (lock.tryLock()) {
            try {
                sendCancel();
                trySendAfterLRA();
                trySendForgetLRA();
                markForDeletion();
            } finally {
                lock.unlock();
            }
        }
    }

    void triggerTimeout() {
        for (LraImpl nestedLra : children) {
            if (nestedLra.participants.stream().anyMatch(p -> p.state().isFinal() || p.isListenerOnly())) {
                nestedLra.triggerTimeout();
            }
        }
        cancel();
        if (lock.tryLock()) {
            try {
                trySendAfterLRA();
            } finally {
                lock.unlock();
            }
        }
    }

    boolean tryAfter() {
        if (lock.tryLock()) {
            try {
                return trySendAfterLRA();
            } finally {
                lock.unlock();
            }
        }
        return false;
    }

    boolean tryForget() {
        if (lock.tryLock()) {
            try {
                return trySendForgetLRA();
            } finally {
                lock.unlock();
            }
        }
        return false;
    }

    AtomicReference<LRAStatus> lraStatus() {
        return status;
    }

    String lraContextId() {
        return coordinatorURL.get().toASCIIString() + "/" + lraId;
    }

    boolean isReadyToDelete() {
        return whenReadyToDelete != 0 && whenReadyToDelete < System.currentTimeMillis();
    }

    void markForDeletion() {
        // delete after 10 minutes
        whenReadyToDelete = (10 * 60 * 1000) + System.currentTimeMillis();
    }

    private boolean forgetNested() {
        for (LraImpl nestedLra : children) {
            //don't do forget not yet closed nested lra
            if (nestedLra.status.get() != LRAStatus.Closed) {
                continue;
            }
            boolean allDone = true;
            for (ParticipantImpl participant : nestedLra.participants) {
                if (participant.forgetURI().isEmpty() || participant.isForgotten()) {
                    continue;
                }
                allDone = participant.sendForget(nestedLra) && allDone;
            }
            if (!allDone) {
                return false;
            }
        }
        return true;
    }

    private boolean trySendForgetLRA() {
        boolean allFinished = true;
        for (ParticipantImpl participant : participants) {
            if (participant.forgetURI().isEmpty() || participant.isForgotten()) {
                continue;
            }
            if (Set.of(
                    ParticipantImpl.Status.FAILED_TO_COMPLETE,
                    ParticipantImpl.Status.FAILED_TO_COMPENSATE
            ).contains(participant.state())) {
                allFinished = participant.sendForget(this) && allFinished;
            }
        }
        return allFinished;
    }

    private void sendComplete() {
        boolean allClosed = true;
        for (ParticipantImpl participant : participants) {
            if (participant.isInEndStateOrListenerOnly() && !isChild) {
                continue;
            }
            allClosed = participant.sendComplete(this) && allClosed;
        }
        if (allClosed) {
            this.lraStatus().compareAndSet(LRAStatus.Closing, LRAStatus.Closed);
        }
    }

    private void sendCancel() {
        boolean allDone = true;
        for (ParticipantImpl participant : participants) {
            if (participant.isInEndStateOrListenerOnly() && !isChild) {
                continue;
            }
            allDone = participant.sendCancel(this) && allDone;
        }
        if (allDone) {
            this.lraStatus().compareAndSet(LRAStatus.Cancelling, LRAStatus.Cancelled);
        }
    }

    private boolean trySendAfterLRA() {
        boolean allSent = true;
        for (ParticipantImpl participant : participants) {
            allSent = participant.trySendAfterLRA(this) && allSent;
        }
        return allSent;
    }
}
