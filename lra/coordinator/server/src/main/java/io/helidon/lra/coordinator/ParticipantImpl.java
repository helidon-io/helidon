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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;

import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;

import static io.helidon.lra.coordinator.LraImpl.LRA_HTTP_CONTEXT_HEADER_NAME;
import static io.helidon.lra.coordinator.LraImpl.LRA_HTTP_ENDED_CONTEXT_HEADER_NAME;
import static io.helidon.lra.coordinator.LraImpl.LRA_HTTP_RECOVERY_HEADER_NAME;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Active;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Compensated;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Compensating;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Completed;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Completing;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.FailedToCompensate;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.FailedToComplete;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.valueOf;

class ParticipantImpl implements Participant {

    private static final int RETRY_CNT = 60;
    private static final int SYNCHRONOUS_RETRY_CNT = 5;

    private static final System.Logger LOGGER = System.getLogger(Participant.class.getName());
    private final AtomicReference<CompensateStatus> compensateCalled = new AtomicReference<>(CompensateStatus.NOT_SENT);
    private final AtomicReference<ForgetStatus> forgetCalled = new AtomicReference<>(ForgetStatus.NOT_SENT);
    private final AtomicReference<AfterLraStatus> afterLRACalled = new AtomicReference<>(AfterLraStatus.NOT_SENT);
    private final AtomicReference<SendingStatus> sendingStatus = new AtomicReference<>(SendingStatus.NOT_SENDING);
    private final AtomicInteger remainingCloseAttempts = new AtomicInteger(RETRY_CNT);
    private final AtomicInteger remainingAfterLraAttempts = new AtomicInteger(RETRY_CNT);

    private final AtomicReference<Status> status = new AtomicReference<>(Status.ACTIVE);
    private final Map<String, URI> compensatorLinks = new HashMap<>();
    private final long timeout;
    private final WebClient webClient = WebClient.builder().build();

    ParticipantImpl(Config config) {
        timeout = config.get("timeout")
                .asLong()
                .orElse(500L);
    }

    @Override
    public Optional<URI> completeURI() {
        return compensatorLink("complete");
    }

    @Override
    public Optional<URI> compensateURI() {
        return compensatorLink("compensate");
    }

    @Override
    public Optional<URI> afterURI() {
        return compensatorLink("after");
    }

    @Override
    public Optional<URI> forgetURI() {
        return compensatorLink("forget");
    }

    @Override
    public Optional<URI> statusURI() {
        return compensatorLink("status");
    }

    void parseCompensatorLinks(String compensatorLinks) {
        Stream.of(compensatorLinks.split(","))
                .filter(s -> !s.isBlank())
                .map(Link::valueOf)
                .forEach(link -> this.compensatorLinks.put(link.rel(), link.uri()));
    }

    Optional<URI> compensatorLink(String rel) {
        return Optional.ofNullable(compensatorLinks.get(rel));
    }

    void completeURI(URI completeURI) {
        compensatorLinks.put("complete", completeURI);
    }

    void compensateURI(URI compensateURI) {
        compensatorLinks.put("compensate", compensateURI);
    }

    void afterURI(URI afterURI) {
        compensatorLinks.put("after", afterURI);
    }

    void forgetURI(URI forgetURI) {
        compensatorLinks.put("forget", forgetURI);
    }

    void statusURI(URI statusURI) {
        compensatorLinks.put("status", statusURI);
    }

    CompensateStatus compensateStatus() {
        return this.compensateCalled.get();
    }

    void compensateStatus(CompensateStatus compensateStatus) {
        this.compensateCalled.set(compensateStatus);
    }

    void status(Status status) {
        this.status.set(status);
    }

    ForgetStatus forgetStatus() {
        return this.forgetCalled.get();
    }

    void forgetStatus(ForgetStatus forgetStatus) {
        this.forgetCalled.set(forgetStatus);
    }

    AfterLraStatus afterLraStatus() {
        return this.afterLRACalled.get();
    }

    void afterLraStatus(AfterLraStatus afterLraStatus) {
        this.afterLRACalled.set(afterLraStatus);
    }

    SendingStatus sendingStatus() {
        return this.sendingStatus.get();
    }

    void sendingStatus(SendingStatus sendingStatus) {
        this.sendingStatus.set(sendingStatus);
    }

    int remainingCloseAttempts() {
        return this.remainingCloseAttempts.get();
    }

    void remainingCloseAttempts(int remainingCloseAttempts) {
        this.remainingCloseAttempts.set(remainingCloseAttempts);
    }

    int remainingAfterAttempts() {
        return this.remainingAfterLraAttempts.get();
    }

    void remainingAfterAttempts(int remainingAfterAttempts) {
        this.remainingAfterLraAttempts.set(remainingAfterAttempts);
    }

    Status state() {
        return status.get();
    }

    boolean isForgotten() {
        return forgetCalled.get() == ForgetStatus.SENT;
    }

    boolean isListenerOnly() {
        return completeURI().isEmpty() && compensateURI().isEmpty();
    }

    boolean isInEndStateOrListenerOnly() {
        return isListenerOnly() || status.get().isFinal();
    }

    boolean sendCancel(LraImpl lra) {
        Optional<URI> endpointURI = compensateURI();
        for (AtomicInteger i = new AtomicInteger(0); i.getAndIncrement() < SYNCHRONOUS_RETRY_CNT;) {
            if (!sendingStatus.compareAndSet(SendingStatus.NOT_SENDING, SendingStatus.SENDING)) {
                return false;
            }
            if (!compensateCalled.compareAndSet(CompensateStatus.NOT_SENT, CompensateStatus.SENDING)) {
                return false;
            }
            LOGGER.log(Level.DEBUG, () -> "Sending compensate, sync retry: " + i.get()
                    + ", status: " + status.get().name()
                    + " statusUri: " + statusURI().map(URI::toASCIIString).orElse(null));
            HttpClientResponse response = null;
            try {
                // call for client status only on retries and when status uri is known
                if (!status.get().equals(Status.ACTIVE) && statusURI().isPresent()) {
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
                    } else if (reportedClientStatus == Active) {
                        // last call didn't reach participant, try call again
                    } else if (reportedClientStatus == Completed && lra.isChild()) {
                        // completed participant can be compensated again in case of nested tx
                    } else if (reportedClientStatus == Compensating) {
                        LOGGER.log(Level.INFO, "Participant reports it is still compensating.");
                        status.set(Status.CLIENT_COMPENSATING);
                        return false;
                    } else if (remainingCloseAttempts.decrementAndGet() <= 0) {
                        LOGGER.log(Level.INFO, "Participant didnt report final status after {0} status call retries.",
                                   new Object[] {RETRY_CNT});
                        status.set(Status.FAILED_TO_COMPENSATE);
                        return true;
                    } else {
                        // Unknown status, lets try in next recovery cycle
                        LOGGER.log(Level.INFO, "Unknown status of " + lra.lraId());
                        return false;
                    }
                }

                response = webClient.put()
                        .uri(endpointURI.get())
                        .headers(lra.headers())
                        .submit(LRAStatus.Cancelled.name());

                // When timeout occur we loose track of the participant status
                // next retry will attempt to retrieve participant status if status uri is available

                switch (response.status().code()) {
                // complete or compensated
                case 200:
                case 410:
                    LOGGER.log(Level.INFO, "Compensated participant of LRA {0} {1}",
                               new Object[] {lra.lraId(), this.compensateURI()});
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
                LOGGER.log(Level.WARNING,
                           () -> "Can't reach participant's compensate endpoint: "
                                   + endpointURI.map(URI::toASCIIString).orElse("unknown"), e);
                if (remainingCloseAttempts.decrementAndGet() <= 0) {
                    LOGGER.log(Level.WARNING, "Failed to compensate participant of LRA {0} {1} {2}",
                               new Object[] {lra.lraId(), this.compensateURI(), e.getMessage()});
                    status.set(Status.FAILED_TO_COMPENSATE);
                } else {
                    status.set(Status.COMPENSATING);
                }

            } finally {
                Optional.ofNullable(response).ifPresent(HttpClientResponse::close);
                sendingStatus.set(SendingStatus.NOT_SENDING);
                compensateCalled.compareAndSet(CompensateStatus.SENDING, CompensateStatus.NOT_SENT);
            }
        }
        return false;
    }

    boolean sendComplete(LraImpl lra) {
        Optional<URI> endpointURI = completeURI();
        for (AtomicInteger i = new AtomicInteger(0); i.getAndIncrement() < SYNCHRONOUS_RETRY_CNT;) {
            if (!sendingStatus.compareAndSet(SendingStatus.NOT_SENDING, SendingStatus.SENDING)) {
                return false;
            }
            LOGGER.log(Level.DEBUG, () -> "Sending complete, sync retry: " + i.get()
                    + ", status: " + status.get().name()
                    + " statusUri: " + statusURI().map(URI::toASCIIString).orElse(null));
            HttpClientResponse response = null;
            try {
                if (status.get().isFinal()) {
                    return true;
                    // call for client status only on retries and when status uri is known
                } else if (!status.get().equals(Status.ACTIVE) && statusURI().isPresent()) {
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
                    } else if (reportedClientStatus == Active) {
                        // last call didn't reach participant, try call again
                    } else if (reportedClientStatus == Completing) {
                        LOGGER.log(Level.INFO, "Participant reports it is still completing.");
                        status.set(Status.CLIENT_COMPLETING);
                        return false;
                    } else if (remainingCloseAttempts.decrementAndGet() <= 0) {
                        LOGGER.log(Level.INFO, "Participant didnt report final status after {0} status call retries.",
                                   new Object[] {RETRY_CNT});
                        status.set(Status.FAILED_TO_COMPLETE);
                        return true;
                    } else {
                        // Unknown status, lets try in next recovery cycle
                        return false;
                    }
                }
                response = webClient.put()
                        .uri(endpointURI.get())
                        .headers(lra.headers())
                        .submit(LRAStatus.Closed.name());
                // When timeout occur we loose track of the participant status
                // next retry will attempt to retrieve participant status if status uri is available

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
                LOGGER.log(Level.WARNING,
                           () -> "Can't reach participant's complete endpoint: " + endpointURI.map(URI::toASCIIString)
                                   .orElse("unknown"), e);
                if (remainingCloseAttempts.decrementAndGet() <= 0) {
                    LOGGER.log(Level.WARNING, "Failed to complete participant of LRA {0} {1} {2}",
                               new Object[] {lra.lraId(),
                                       this.completeURI(), e.getMessage()});
                    status.set(Status.FAILED_TO_COMPLETE);
                } else {
                    status.set(Status.COMPLETING);
                }
            } finally {
                Optional.ofNullable(response).ifPresent(HttpClientResponse::close);
                sendingStatus.set(SendingStatus.NOT_SENDING);
            }
        }
        return false;
    }

    boolean trySendAfterLRA(LraImpl lra) {
        for (int i = 0; i < SYNCHRONOUS_RETRY_CNT; i++) {
            // Participant in right state
            if (!isInEndStateOrListenerOnly()) {
                return false;
            }
            // LRA in right state
            if (!(Set.of(LRAStatus.Closed, LRAStatus.Cancelled).contains(lra.lraStatus().get()))) {
                return false;
            }

            HttpClientResponse response = null;
            try {
                Optional<URI> afterURI = afterURI();
                if (afterURI.isPresent() && afterLRACalled.compareAndSet(AfterLraStatus.NOT_SENT, AfterLraStatus.SENDING)) {
                    response = webClient.put()
                            .uri(afterURI.get())
                            .headers(lra.headers())
                            .submit(lra.lraStatus().get().name());

                    if (response.status().code() == 200) {
                        afterLRACalled.set(AfterLraStatus.SENT);
                    } else if (remainingAfterLraAttempts.decrementAndGet() <= 0) {
                        afterLRACalled.set(AfterLraStatus.SENT);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error when sending after lra", e);
                if (remainingAfterLraAttempts.decrementAndGet() <= 0) {
                    afterLRACalled.set(AfterLraStatus.SENT);
                } else {
                    afterLRACalled.set(AfterLraStatus.NOT_SENT);
                }
            } finally {
                Optional.ofNullable(response).ifPresent(HttpClientResponse::close);
            }
            if (afterLRACalled.get() == AfterLraStatus.SENT) {
                return true;
            }
        }
        return false;
    }

    Optional<ParticipantStatus> retrieveStatus(LraImpl lra, ParticipantStatus inProgressStatus) {
        URI statusURI = this.statusURI().get();
        try (HttpClientResponse response = webClient.get()
                .uri(statusURI)
                .headers(h -> {
                    // Dont send parent!
                    h.add(LRA_HTTP_CONTEXT_HEADER_NAME, lra.lraContextId());
                    h.add(LRA_HTTP_RECOVERY_HEADER_NAME, lra.lraContextId() + "/recovery");
                    h.add(LRA_HTTP_ENDED_CONTEXT_HEADER_NAME, lra.lraContextId());
                })
                .request()) {

            int code = response.status().code();
            switch (code) {
            case 202:
                return Optional.of(inProgressStatus);
            case 410: //GONE
                //Completing -> FailedToComplete ...
                return status.get().failedFinalStatus();
            case 503:
            case 500:
                throw new IllegalStateException(String.format("Client reports unexpected status %s %s, "
                                                                      + "current participant state is %s, "
                                                                      + "lra: %s "
                                                                      + "status uri: %s",
                                                              code,
                                                              response.as(String.class),
                                                              status.get(),
                                                              lra.lraId(),
                                                              statusURI.toASCIIString()));
            default:
                ParticipantStatus reportedStatus = valueOf(response.as(String.class));
                Status currentStatus = status.get();
                if (currentStatus.validateNextStatus(reportedStatus)) {
                    return Optional.of(reportedStatus);
                } else {
                    LOGGER.log(Level.WARNING,
                               "Client reports unexpected status {0} {1}, "
                                       + "current participant state is {2}, "
                                       + "lra: {3} "
                                       + "status uri: {4}",
                               new Object[] {code, reportedStatus, currentStatus, lra.lraId(), statusURI.toASCIIString()});
                    return Optional.empty();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error when getting participant status. " + statusURI, e);
            // skip dependent compensation call, another retry with status call might be luckier
            throw e;
        }
    }

    boolean sendForget(LraImpl lra) {
        if (!forgetCalled.compareAndSet(ForgetStatus.NOT_SENT, ForgetStatus.SENDING)) {
            return false;
        }
        try (
                HttpClientResponse response = webClient.delete()
                        .uri(forgetURI().get())
                        .headers(lra.headers())
                        .request()) {

            int responseStatus = response.status().code();
            if (responseStatus == 200 || responseStatus == 410) {
                forgetCalled.set(ForgetStatus.SENT);
            } else {
                throw new Exception("Unexpected response from participant " + response.status().code());
            }
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Unable to send forget of lra {0} to {1}",
                       new Object[] {lra.lraId(), forgetURI().get()});
            forgetCalled.set(ForgetStatus.NOT_SENT);
        }
        return forgetCalled.get() == ForgetStatus.SENT;
    }

    boolean equalCompensatorUris(String compensatorUris) {
        Set<Link> links = Arrays.stream(compensatorUris.split(","))
                .map(Link::valueOf)
                .collect(Collectors.toSet());

        for (Link link : links) {
            Optional<URI> participantsLink = compensatorLink(link.rel());
            if (participantsLink.isEmpty()) {
                continue;
            }

            if (Objects.equals(participantsLink.get(), link.uri())) {
                return true;
            }
        }

        return false;
    }

    enum Status {
        ACTIVE(Active, null, null, false, Set.of(Completing, Compensating)),

        COMPENSATED(Compensated, null, null, true, Set.of(Compensated)),
        COMPLETED(Completed, null, null, true, Set.of(Completed)),
        FAILED_TO_COMPENSATE(FailedToCompensate, null, null, true, Set.of()),
        FAILED_TO_COMPLETE(FailedToComplete, null, null, true, Set.of()),

        CLIENT_COMPENSATING(Compensating, COMPENSATED, FAILED_TO_COMPENSATE, false,
                            Set.of(Active, Compensated, FailedToCompensate)),
        CLIENT_COMPLETING(Completing, COMPLETED, FAILED_TO_COMPLETE, false, Set.of(Active, Completed, FailedToComplete)),
        COMPENSATING(Compensating, COMPENSATED, FAILED_TO_COMPENSATE, false, Set.of(Active, Compensated, FailedToCompensate)),
        COMPLETING(Completing, COMPLETED, FAILED_TO_COMPLETE, false, Set.of(Active, Completed, FailedToComplete));

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

        ParticipantStatus participantStatus() {
            return participantStatus;
        }

        boolean isFinal() {
            return finalState;
        }

        boolean validateNextStatus(ParticipantStatus participantStatus) {
            return validNextStates.contains(participantStatus);
        }

        Optional<ParticipantStatus> successFinalStatus() {
            return Optional.ofNullable(successFinalStatus.participantStatus());
        }

        Optional<ParticipantStatus> failedFinalStatus() {
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
}
