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
 */
package io.helidon.lra.rest;

import org.eclipse.microprofile.lra.annotation.ParticipantStatus;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class ParticipantProxy {
    private URI lraId;
    private String participantId;
    private LRAProxyParticipant participant;
    private Future<Void> future;
    private boolean compensate;

    ParticipantProxy(URI lraId, String participantId, LRAProxyParticipant participant) {
        this.lraId = lraId;
        this.participantId = participantId;
        this.participant = participant;
    }

    ParticipantProxy(URI lraId, String participantId) {
        this.lraId = lraId;
        this.participantId = participantId;
    }


    private URI getLraId() {
        return lraId;
    }

    String getParticipantId() {
        return participantId;
    }

    LRAProxyParticipant getParticipant() {
        return participant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ParticipantProxy)) {
            return false;
        }

        ParticipantProxy that = (ParticipantProxy) o;

        return getLraId().equals(that.getLraId()) && getParticipantId().equals(that.getParticipantId());
    }

    @Override
    public int hashCode() {
        int result = getLraId().hashCode();
        result = 31 * result + getParticipantId().hashCode();
        return result;
    }

     void setFuture(Future<Void> future, boolean compensate) {
        this.future = future;
        this.compensate = compensate;
    }

    private ParticipantStatus getExpectedStatus() {
        return compensate ? ParticipantStatus.Compensated : ParticipantStatus.Completed;
    }

    private ParticipantStatus getCurrentStatus() {
        return compensate ? ParticipantStatus.Compensating : ParticipantStatus.Completing;
    }

    private ParticipantStatus getFailedStatus() {
        return compensate ? ParticipantStatus.FailedToCompensate : ParticipantStatus.FailedToComplete;
    }

    Optional<ParticipantStatus> getStatus() throws Exception {
        if (future == null) {
            return Optional.empty();
        }

        if (future.isDone()) {
            try {
                future.get();

                return Optional.of(getExpectedStatus());
            } catch (ExecutionException e) {
//    todo            LRAProxyLogger.i18NLogger.error_participantExceptionOnCompletion(participant.getClass().getName(), e);
                return Optional.of(getFailedStatus());
            } catch (InterruptedException e) {
                // the only recourse is to retry of mark as failed
                return Optional.of(getFailedStatus()); // interpret as failure
            }
        } else if (future.isCancelled()) {
            // the participant canceled it so assume it finished early
            return Optional.of(getExpectedStatus()); // success
        } else {
            return Optional.of(getCurrentStatus()); // still in progress
        }
    }
}
