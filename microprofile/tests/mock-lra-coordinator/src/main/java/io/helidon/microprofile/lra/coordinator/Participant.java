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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.Link;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;

import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Active;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Compensated;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Compensating;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Completed;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Completing;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.FailedToCompensate;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.FailedToComplete;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.valueOf;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_ENDED_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
class Participant {

    private static final int RETRY_CNT = 30;

    private static final Logger LOGGER = Logger.getLogger(Participant.class.getName());
    private final AtomicReference<CompensateStatus> compensateCalled = new AtomicReference<>(CompensateStatus.NOT_SENT);
    private final AtomicReference<ForgetStatus> forgetCalled = new AtomicReference<>(ForgetStatus.NOT_SENT);
    private final AtomicReference<AfterLraStatus> afterLRACalled = new AtomicReference<>(AfterLraStatus.NOT_SENT);
    private final AtomicReference<SendingStatus> sendingStatus = new AtomicReference<>(SendingStatus.NOT_SENDING);
    private final AtomicInteger remainingCloseAttempts = new AtomicInteger(RETRY_CNT);
    private final AtomicInteger remainingAfterLraAttempts = new AtomicInteger(RETRY_CNT);

    private final AtomicReference<Status> status = new AtomicReference<>(Status.ACTIVE);

    enum Status {
        ACTIVE(Active, null, null, false, Set.of(Completing, Compensating)),

        COMPENSATED(Compensated, null, null, true, Set.of()),
        COMPLETED(Completed, null, null, true, Set.of()),
        FAILED_TO_COMPENSATE(FailedToCompensate, null, null, true, Set.of()),
        FAILED_TO_COMPLETE(FailedToComplete, null, null, true, Set.of()),

        CLIENT_COMPENSATING(Compensating, COMPENSATED, FAILED_TO_COMPENSATE, false, Set.of(Compensated, FailedToCompensate)),
        CLIENT_COMPLETING(Completing, COMPLETED, FAILED_TO_COMPLETE, false, Set.of(Completed, FailedToComplete)),
        COMPENSATING(Compensating, COMPENSATED, FAILED_TO_COMPENSATE, false, Set.of(Compensated, FailedToCompensate)),
        COMPLETING(Completing, COMPLETED, FAILED_TO_COMPLETE, false, Set.of(Completed, FailedToComplete));

        private final ParticipantStatus participantStatus;
        private final Status successFinalStatus;
        private final Status failedFinalStatus;
        private final boolean finalState;
        private final Set<ParticipantStatus> validNextStates;

        Status(ParticipantStatus participantStatus,
               Status successFinalStatus,
               Status failedFinalStatus,
               boolean finalState,
               Set<ParticipantStatus> validNextStates) {
            this.participantStatus = participantStatus;
            this.successFinalStatus = successFinalStatus;
            this.failedFinalStatus = failedFinalStatus;
            this.finalState = finalState;
            this.validNextStates = validNextStates;
        }

        public ParticipantStatus participantStatus() {
            return participantStatus;
        }

        public boolean isFinal() {
            return finalState;
        }

        public boolean validateNextStatus(ParticipantStatus participantStatus) {
            return validNextStates.contains(participantStatus);
        }

        public Optional<ParticipantStatus> successFinalStatus() {
            return Optional.ofNullable(successFinalStatus.participantStatus());
        }

        public Optional<ParticipantStatus> failedFinalStatus() {
            return Optional.ofNullable(failedFinalStatus.participantStatus);
        }
    }

    enum SendingStatus {
        SENDING, NOT_SENDING;
    }

    enum AfterLraStatus {
        NOT_SENT, SENDING, SENT;
    }

    enum ForgetStatus {
        NOT_SENT, SENDING, SENT;
    }

    enum CompensateStatus {
        NOT_SENT, SENDING, SENT;
    }

    @XmlElement
    @XmlJavaTypeAdapter(Link.JaxbAdapter.class)
    private final List<Link> compensatorLinks = new ArrayList<>(5);

    void parseCompensatorLinks(String compensatorLinks) {
        Stream.of(compensatorLinks.split(","))
                .filter(s -> !s.isBlank())
                .map(Link::valueOf)
                .forEach(this.compensatorLinks::add);
    }

    Optional<Link> getCompensatorLink(String rel) {
        return compensatorLinks.stream().filter(l -> rel.equals(l.getRel())).findFirst();
    }

    /**
     * Invoked when closed 200, 202, 409, 410.
     */
    public Optional<URI> getCompleteURI() {
        return getCompensatorLink("complete").map(Link::getUri);
    }

    /**
     * Invoked when cancelled 200, 202, 409, 410.
     */
    public Optional<URI> getCompensateURI() {
        return getCompensatorLink("compensate").map(Link::getUri);
    }

    /**
     * Invoked when finalized 200.
     */
    public Optional<URI> getAfterURI() {
        return getCompensatorLink("after").map(Link::getUri);
    }

    /**
     * Invoked when cleaning up 200, 410.
     */
    public Optional<URI> getForgetURI() {
        return getCompensatorLink("forget").map(Link::getUri);
    }

    /**
     * Directly updates status of participant 200, 202, 410.
     */
    public Optional<URI> getStatusURI() {
        return getCompensatorLink("status").map(Link::getUri);
    }

    Status state() {
        return status.get();
    }

    boolean isForgotten() {
        return forgetCalled.get() == ForgetStatus.SENT;
    }

    boolean isListenerOnly() {
        return getCompleteURI().isEmpty() && getCompensateURI().isEmpty();
    }

    boolean isInEndStateOrListenerOnly() {
        return isListenerOnly() || status.get().isFinal();
    }

    boolean sendCancel(Lra lra) {
        Optional<URI> endpointURI = getCompensateURI();
        if (!sendingStatus.compareAndSet(SendingStatus.NOT_SENDING, SendingStatus.SENDING)) return false;
        if (!compensateCalled.compareAndSet(CompensateStatus.NOT_SENT, CompensateStatus.SENDING)) return false;
        try {
            if (!status.get().equals(Status.ACTIVE)) {// call for client status only on retries
                // If the participant does not support idempotency then it MUST be able to report its status
                // by annotating one of the methods with the @Status annotation which should report the status
                // in case we can't retrieve status from participant just retry n times
                ParticipantStatus reportedClientStatus = retrieveStatus(lra, Compensating).orElse(null);
                if (reportedClientStatus == Compensated) {
                    LOGGER.log(Level.INFO, "Participant reports it is compensated.");
                    status.set(Status.COMPENSATED);
                    return true;
                } else if (reportedClientStatus == FailedToCompensate) {
                    LOGGER.log(Level.INFO, "Participant reports it failed to compensate.");
                    status.set(Status.FAILED_TO_COMPENSATE);
                    return true;
                } else if (remainingCloseAttempts.decrementAndGet() <= 0) {
                    LOGGER.log(Level.INFO, "Participant didnt report final status after {0} status call retries.",
                            new Object[] {RETRY_CNT});
                    status.set(Status.FAILED_TO_COMPENSATE);
                    return true;
                }
            }

            WebClientResponse response = WebClient.builder()
                    .baseUri(endpointURI.get())
                    .build()
                    .put()
                    .headers(lra.headers())
                    .submit(LRAStatus.Cancelled.name())
                    .await(500, TimeUnit.MILLISECONDS);

            switch (response.status().code()) {
                // complete or compensated
                case 200:
                case 410:
                    LOGGER.log(Level.INFO, "Compensated participant of LRA {0} {1}",
                            new Object[] {lra.lraId(), this.getCompensateURI()});
                    status.set(Status.COMPENSATED);
                    compensateCalled.set(CompensateStatus.SENT);
                    return true;

                // retryable
                case 202:
                    // Still compensating, check with @Status later
                    this.status.set(Status.CLIENT_COMPENSATING);
                    return false;
                case 409:
                case 404:
                case 503:
                default:
                    throw new Exception(response.status().code() + " " + response.status().reasonPhrase());
            }

        } catch (Exception e) {
            if (remainingCloseAttempts.decrementAndGet() <= 0) {
                LOGGER.log(Level.WARNING, "Failed to compensate participant of LRA {0} {1} {2}",
                        new Object[] {lra.lraId(), this.getCompensateURI(), e.getMessage()});
                status.set(Status.FAILED_TO_COMPENSATE);
            } else {
                status.set(Status.COMPENSATING);
            }

        } finally {
            sendingStatus.set(SendingStatus.NOT_SENDING);
            compensateCalled.compareAndSet(CompensateStatus.SENDING, CompensateStatus.NOT_SENT);
        }
        return false;
    }

    boolean sendComplete(Lra lra) {
        Optional<URI> endpointURI = getCompleteURI();
        if (!sendingStatus.compareAndSet(SendingStatus.NOT_SENDING, SendingStatus.SENDING)) return false;
        try {
            if (status.get().isFinal()) {
                return true;
            } else if (!status.get().equals(Status.ACTIVE)) {// call for client status only on retries
                // If the participant does not support idempotency then it MUST be able to report its status
                // by annotating one of the methods with the @Status annotation which should report the status
                // in case we can't retrieve status from participant just retry n times
                ParticipantStatus reportedClientStatus = retrieveStatus(lra, Completing).orElse(null);
                if (reportedClientStatus == Completed) {
                    LOGGER.log(Level.INFO, "Participant reports it is completed.");
                    status.set(Status.COMPLETED);
                    return true;
                } else if (reportedClientStatus == FailedToComplete) {
                    LOGGER.log(Level.INFO, "Participant reports it failed to complete.");
                    status.set(Status.FAILED_TO_COMPLETE);
                    return true;
                } else if (reportedClientStatus == Completing) {
                    LOGGER.log(Level.INFO, "Participant reports it is still completing.");
                    status.set(Status.CLIENT_COMPLETING);
                    return false;
                } else if (remainingCloseAttempts.decrementAndGet() <= 0) {
                    LOGGER.log(Level.INFO, "Participant didnt report final status after {0} status call retries.",
                            new Object[] {RETRY_CNT});
                    status.set(Status.FAILED_TO_COMPLETE);
                    return true;
                }
            }
            WebClientResponse response = WebClient
                    .builder()
                    .baseUri(endpointURI.get())
                    .build()
                    .put()
                    .headers(lra.headers())
                    .submit(LRAStatus.Closed.name())
                    .await(500, TimeUnit.MILLISECONDS);

            switch (response.status().code()) {
                // complete or compensated
                case 200:
                case 410:
                    status.set(Status.COMPLETED);
                    return true;

                // retryable
                case 202:
                    // Still completing, check with @Status later
                    this.status.set(Status.CLIENT_COMPLETING);
                    return false;
                case 409:
                case 404:
                case 503:
                default:
                    throw new Exception(response.status().code() + " " + response.status().reasonPhrase());
            }

        } catch (Exception e) {
            if (remainingCloseAttempts.decrementAndGet() <= 0) {
                LOGGER.log(Level.WARNING, "Failed to complete participant of LRA {0} {1} {2}",
                        new Object[] {lra.lraId(), this.getCompleteURI(), e.getMessage()});
                status.set(Status.FAILED_TO_COMPLETE);
            } else {
                status.set(Status.COMPLETING);
            }
        } finally {
            sendingStatus.set(SendingStatus.NOT_SENDING);
        }
        return false;
    }

    boolean trySendAfterLRA(Lra lra) {
        // Participant in right state
        if (!isInEndStateOrListenerOnly()) return false;
        // LRA in right state
        if (!(Set.of(LRAStatus.Closed, LRAStatus.Cancelled).contains(lra.status().get()))) return false;

        try {
            Optional<URI> afterURI = getAfterURI();
            if (afterURI.isPresent() && afterLRACalled.compareAndSet(AfterLraStatus.NOT_SENT, AfterLraStatus.SENDING)) {
                WebClientResponse response = WebClient.builder()
                        .baseUri(afterURI.get())
                        .build()
                        .put()
                        .headers(lra.headers())
                        .submit(lra.status().get().name())
                        .await(500, TimeUnit.MILLISECONDS);

                if (response.status().code() == 200) {
                    afterLRACalled.set(AfterLraStatus.SENT);
                } else if (remainingAfterLraAttempts.decrementAndGet() <= 0) {
                    afterLRACalled.set(AfterLraStatus.SENT);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error when sending after lra", e);
        }
        return afterLRACalled.get() == AfterLraStatus.SENT;
    }


    Optional<ParticipantStatus> retrieveStatus(Lra lra, ParticipantStatus inProgressStatus) {
        Optional<URI> statusURI = this.getStatusURI();
        if (statusURI.isPresent()) {
            try {
                WebClientResponse response = WebClient
                        .builder()
                        .baseUri(statusURI.get())
                        .build()
                        .get()
                        .headers(h -> {
                            // Dont send parent!
                            h.add(LRA_HTTP_CONTEXT_HEADER, lra.lraId());
                            h.add(LRA_HTTP_RECOVERY_HEADER, lra.lraId());
                            h.add(LRA_HTTP_ENDED_CONTEXT_HEADER, lra.lraId());
                            return h;
                        })
                        .request()
                        .await(500, TimeUnit.MILLISECONDS);

                switch (response.status().code()) {
                    case 202:
                        return Optional.of(inProgressStatus);
                    case 410: //GONE
                        //Completing -> FailedToComplete ...
                        return status.get().failedFinalStatus();
                    case 503:
                    case 500:
                        LOGGER.log(Level.SEVERE, "Client reports unexpected status {0}, current participant state is {1}",
                                new Object[] {503, status.get()});
                        return Optional.empty();
                    default:
                        ParticipantStatus reportedStatus = valueOf(response.content().as(String.class).await());
                        Status currentStatus = status.get();
                        if (currentStatus.validateNextStatus(reportedStatus)) {
                            return Optional.of(reportedStatus);
                        } else {
                            LOGGER.log(Level.WARNING, "Client reports unexpected status {0}, current participant state is {1}",
                                    new Object[] {reportedStatus, currentStatus});
                            return Optional.empty();
                        }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error when getting participant status. " + statusURI, e);
            }
        }
        return Optional.empty();
    }

    boolean sendForget(Lra lra) {
        if (!forgetCalled.compareAndSet(ForgetStatus.NOT_SENT, ForgetStatus.SENDING)) return false;
        try {
            WebClientResponse response = WebClient
                    .builder()
                    .baseUri(getForgetURI().get())
                    .build()
                    .delete()
                    .headers(lra.headers())
                    .submit()
                    .await(500, TimeUnit.MILLISECONDS);

            int responseStatus = response.status().code();
            if (responseStatus == 200 || responseStatus == 410) {
                forgetCalled.set(ForgetStatus.SENT);
            } else {
                throw new Exception("Unexpected response from participant " + response.status().code());
            }
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Unable to send forget of lra {0} to {1}",
                    new Object[] {lra.lraId(), getForgetURI().get()});
            forgetCalled.set(ForgetStatus.NOT_SENT);
        }
        return forgetCalled.get() == ForgetStatus.SENT;
    }

    boolean equalCompensatorUris(String compensatorUris) {
        Set<Link> links = Arrays.stream(compensatorUris.split(","))
                .map(Link::valueOf)
                .collect(Collectors.toSet());

        for (Link link : links) {
            Optional<Link> participantsLink = getCompensatorLink(link.getRel());
            if (participantsLink.isEmpty()) {
                continue;
            }

            if (Objects.equals(participantsLink.get().getUri(), link.getUri())) {
                return true;
            }
        }

        return false;
    }
}
