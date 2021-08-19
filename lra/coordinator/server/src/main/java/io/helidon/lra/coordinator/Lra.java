/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.lra.coordinator;

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
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.common.http.Headers;
import io.helidon.config.Config;
import io.helidon.metrics.RegistryFactory;
import io.helidon.webclient.WebClientRequestHeaders;

import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_ENDED_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

class Lra {

    private static final Logger LOGGER = Logger.getLogger(Lra.class.getName());
    private final LazyValue<URI> coordinatorURL;

    private long timeout;
    private URI parentId;
    private final Set<String> compensatorLinks = Collections.synchronizedSet(new HashSet<>());

    private final String lraId;
    private final Config config;

    private final List<Lra> children = Collections.synchronizedList(new ArrayList<>());

    private final List<Participant> participants = new CopyOnWriteArrayList<>();

    private final AtomicReference<LRAStatus> status = new AtomicReference<>(LRAStatus.Active);

    private final Lock lock = new ReentrantLock();

    private boolean isChild;
    private long whenReadyToDelete = 0;

    private final MetricRegistry registry = RegistryFactory.getInstance()
            .getRegistry(MetricRegistry.Type.APPLICATION);
    private final Counter lraCtr = registry.counter("lractr");
    private final Timer.Context lraLifeSpanTmr = registry.timer("lralifespantmr").time();

    Lra(CoordinatorService coordinatorService, String lraUUID, Config config) {
        lraId = lraUUID;
        this.config = config;
        lraCtr.inc();
        coordinatorURL = LazyValue.create(coordinatorService.getCoordinatorURL());
    }

    Lra(CoordinatorService coordinatorService, String lraUUID, URI parentId, Config config) {
        lraId = lraUUID;
        this.parentId = parentId;
        this.config = config;
        lraCtr.inc();
        coordinatorURL = LazyValue.create(coordinatorService.getCoordinatorURL());
    }

    String lraId() {
        return lraId;
    }

    String parentId() {
        return Optional.ofNullable(parentId).map(URI::toASCIIString).orElse(null);
    }

    boolean isChild() {
        return isChild;
    }

    void setChild(boolean child) {
        isChild = child;
    }

    long getTimeout() {
        return timeout;
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

    void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    List<Participant> getParticipants() {
        return this.participants;
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
            Participant participant = new Participant(config);
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

    void addChild(Lra lra) {
        children.add(lra);
        lra.isChild = true;
    }

    Function<WebClientRequestHeaders, Headers> headers() {
        return headers -> {
            headers.add(LRA_HTTP_CONTEXT_HEADER, lraContextId());
            headers.add(LRA_HTTP_ENDED_CONTEXT_HEADER, lraContextId());
            Optional.ofNullable(parentId)
                    .map(URI::toASCIIString)
                    .ifPresent(s -> headers.add(LRA_HTTP_PARENT_CONTEXT_HEADER, s));
            headers.add(LRA_HTTP_RECOVERY_HEADER, lraContextId() + "/recovery");
            return headers;
        };
    }

    void close() {
        Set<LRAStatus> allowedStatuses = Set.of(LRAStatus.Active, LRAStatus.Closing);
        if (LRAStatus.Closing != status.updateAndGet(old -> allowedStatuses.contains(old) ? LRAStatus.Closing : old)) {
            LOGGER.warning("Can't close LRA, it's already " + status.get().name() + " " + this.lraId);
            return;
        }
        lraLifeSpanTmr.close();
        if (lock.tryLock()) {
            try {
                sendComplete();
                // needs to go before nested close, so we know if nested was already closed
                // or not(non closed nested can't get @Forget call)
                forgetNested();
                for (Lra nestedLra : children) {
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
            LOGGER.warning("Can't cancel LRA, it's already " + status.get().name() + " " + this.lraId);
            return;
        }
        lraLifeSpanTmr.close();
        for (Lra nestedLra : children) {
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

    void timeout() {
        for (Lra nestedLra : children) {
            if (nestedLra.participants.stream().anyMatch(p -> p.state().isFinal() || p.isListenerOnly())) {
                nestedLra.timeout();
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

    private boolean forgetNested() {
        for (Lra nestedLra : children) {
            //dont do forget not yet closed nested lra
            if (nestedLra.status.get() != LRAStatus.Closed) continue;
            boolean allDone = true;
            for (Participant participant : nestedLra.participants) {
                if (participant.getForgetURI().isEmpty() || participant.isForgotten()) continue;
                allDone = participant.sendForget(nestedLra) && allDone;
            }
            if (!allDone) return false;
        }
        return true;
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

    private boolean trySendForgetLRA() {
        boolean allFinished = true;
        for (Participant participant : participants) {
            if (participant.getForgetURI().isEmpty() || participant.isForgotten()) continue;
            if (Set.of(
                    Participant.Status.FAILED_TO_COMPLETE,
                    Participant.Status.FAILED_TO_COMPENSATE
            ).contains(participant.state())) {
                allFinished = participant.sendForget(this) && allFinished;
            }
        }
        return allFinished;
    }

    AtomicReference<LRAStatus> status() {
        return status;
    }

    String lraContextId() {
        return coordinatorURL.get().toASCIIString() + "/" + lraId;
    }

    private void sendComplete() {
        boolean allClosed = true;
        for (Participant participant : participants) {
            if (participant.isInEndStateOrListenerOnly() && !isChild) {
                continue;
            }
            allClosed = participant.sendComplete(this) && allClosed;
        }
        if (allClosed) {
            this.status().compareAndSet(LRAStatus.Closing, LRAStatus.Closed);
        }
    }

    private void sendCancel() {
        boolean allDone = true;
        for (Participant participant : participants) {
            if (participant.isInEndStateOrListenerOnly() && !isChild) {
                continue;
            }
            allDone = participant.sendCancel(this) && allDone;
        }
        if (allDone) {
            this.status().compareAndSet(LRAStatus.Cancelling, LRAStatus.Cancelled);
        }
    }

    private boolean trySendAfterLRA() {
        boolean allSent = true;
        for (Participant participant : participants) {
            allSent = participant.trySendAfterLRA(this) && allSent;
        }
        return allSent;
    }

    boolean isReadyToDelete() {
        return whenReadyToDelete != 0 && whenReadyToDelete < System.currentTimeMillis();
    }

    void markForDeletion() {
        // delete after 10 minutes
        whenReadyToDelete = (10 * 60 * 1000) + System.currentTimeMillis();
    }
}
