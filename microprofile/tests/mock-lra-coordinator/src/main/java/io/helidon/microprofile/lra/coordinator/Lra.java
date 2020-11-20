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
package io.helidon.microprofile.lra.coordinator;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlRootElement;

import org.eclipse.microprofile.lra.annotation.LRAStatus;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_ENDED_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
class Lra {

    private static final Logger LOGGER = Logger.getLogger(Lra.class.getName());

    private long timeout;
    private URI parentId;
    private final Set<String> compensatorLinks = new HashSet<>();

    @XmlID
    private String lraId;

    @XmlIDREF
    private final List<Lra> children = new ArrayList<>();

    @XmlElement
    private final List<Participant> participants = new CopyOnWriteArrayList<>();

    private final AtomicReference<LRAStatus> status = new AtomicReference<>(LRAStatus.Active);

    private boolean isChild;
    private long whenReadyToDelete = 0;

    Lra(String lraUUID) {
        lraId = lraUUID;
    }

    Lra(String lraUUID, URI parentId) {
        lraId = lraUUID;
        this.parentId = parentId;
    }

    Lra() {
    }

    String lraId() {
        return lraId;
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
            Participant participant = new Participant();
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

    MultivaluedMap<String, Object> headers() {
        MultivaluedMap<String, Object> multivaluedMap = new MultivaluedHashMap<>(4);
        multivaluedMap.add(LRA_HTTP_CONTEXT_HEADER, lraId);
        multivaluedMap.add(LRA_HTTP_ENDED_CONTEXT_HEADER, lraId);
        Optional.ofNullable(parentId)
                .map(URI::toASCIIString)
                .ifPresent(s -> multivaluedMap.add(LRA_HTTP_PARENT_CONTEXT_HEADER, s));
        multivaluedMap.add(LRA_HTTP_RECOVERY_HEADER, lraId);
        return multivaluedMap;
    }

    void close() {
        Set<LRAStatus> allowedStatuses = Set.of(LRAStatus.Active, LRAStatus.Closing);
        if (LRAStatus.Closing != status.updateAndGet(old -> allowedStatuses.contains(old) ? LRAStatus.Closing : old)) {
            LOGGER.warning("Can't close LRA, it's already " + status.get().name() + " " + this.lraId);
            return;
        }
        sendComplete();
        // needs to go before nested close, so we know if nested was already closed
        // or not(non closed nested can't get @Forget call)
        forgetNested();
        for (Lra nestedLra : children) {
            nestedLra.close();
        }
        trySendAfterLRA();
        markForDeletion();
    }

    void cancel() {
        Set<LRAStatus> allowedStatuses = Set.of(LRAStatus.Active, LRAStatus.Cancelling);
        if (LRAStatus.Cancelling != status.updateAndGet(old -> allowedStatuses.contains(old) ? LRAStatus.Cancelling : old)
                && !isChild) { // nested can be compensated even if closed
            LOGGER.warning("Can't cancel LRA, it's already " + status.get().name() + " " + this.lraId);
            return;
        }
        for (Lra nestedLra : children) {
            nestedLra.cancel();
        }
        sendCancel();
        trySendAfterLRA();
        tryForget();
        markForDeletion();
    }

    void timeout() {
        for (Lra nestedLra : children) {
            if (nestedLra.participants.stream().anyMatch(p -> p.state().isFinal() || p.isListenerOnly())) {
                nestedLra.timeout();
            }
        }
        cancel();
        trySendAfterLRA();
    }

    boolean forgetNested() {
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

    boolean tryForget() {
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

    boolean trySendAfterLRA() {
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
