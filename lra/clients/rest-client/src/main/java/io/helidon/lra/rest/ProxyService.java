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

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Future;

import static io.helidon.lra.rest.ParticipantProxyResource.LRA_PROXY_PATH;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

@ApplicationScoped
public class ProxyService {
    private static List<ParticipantProxy> participants; // TODO figure out why ProxyService is constructed twice

    @Inject
    private LRAClient lraClient;

    private UriBuilder uriBuilder;

    @PostConstruct
    void init() {
        if (participants == null) { // TODO figure out why ProxyService is constructed twice
            participants = new ArrayList<>();
        }

        int httpPort = Integer.getInteger("thorntail.http.port", 8081);
        String httpHost = System.getProperty("thorntail.http.host", "localhost");

        // TODO if the proxy is restarted on a different endpoint it should notify the recovery coordinator

        uriBuilder = UriBuilder.fromPath(LRA_PROXY_PATH + "/{lra}/{pid}");
        uriBuilder.scheme("http")
                .host(httpHost)
                .port(httpPort);
    }

    private ParticipantProxy getProxy(URI lraId, String participantId) {
        int i = participants.indexOf(new ParticipantProxy(lraId, participantId));

        return (i == -1 ? null : participants.get(i));
    }

    private ParticipantProxy recreateProxy(URI lraId, String participantId) {
        return new ParticipantProxy(lraId, participantId);
    }

    Response notifyParticipant(URI lraId, String participantId, String participantData, boolean compensate) {
        ParticipantProxy proxy = getProxy(lraId, participantId);

        if (proxy == null) {
            /*
             * must be in a recovery scenario so recreate the proxy from the registered data
             */
            proxy = recreateProxy(lraId, participantId);
        }

        LRAProxyParticipant participant = proxy.getParticipant();

        if (participant == null && participantData != null && participantData.length() > 0) {
            participant = deserializeParticipant(lraId, participantData).orElse(null);
        }

        if (participant != null) {
            Future<Void> future = null;

            try {
                if (compensate) {
                    // let any NotFoundException propagate back to the coordinator
                    future = participant.compensateWork(lraId);
                } else {
                    // let any NotFoundException propagate back to the coordinator
                    future = participant.completeWork(lraId);
                }
            } catch (Exception e) {
                return Response.ok().entity(compensate ? ParticipantStatus.FailedToCompensate
                        : ParticipantStatus.FailedToComplete).build();
            } finally {
                if (future == null) {
                    participants.remove(proxy); // we definitively know the outcome
                } else {
                    proxy.setFuture(future, compensate); // remember the future so that we can report its progress
                }
            }

            if (future != null) {
                return Response.accepted().build();
            }

            return Response.ok().build();
        } else {
//    todo        LRAProxyLogger.logger.errorf("TODO recovery: null participant for callback %s", lraId.toASCIIString());
        }

        return Response.status(NOT_FOUND).build();
    }

    void notifyForget(URI lraId, String participantId) {
        ParticipantProxy proxy = getProxy(lraId, participantId);

        if (proxy != null) {
            participants.remove(proxy);
        }
    }

    ParticipantStatus getStatus(URI lraId, String participantId) throws Exception, Exception {
        ParticipantProxy proxy = getProxy(lraId, participantId);

        if (proxy == null) {
            String errorMsg = String.format("Cannot find participant proxy for LRA id %s, participant id %%s",
                    lraId, participantId);
            throw new NotFoundException(errorMsg,
                    Response.status(NOT_FOUND).entity(errorMsg).build());
        }

        Optional<ParticipantStatus> status = proxy.getStatus();

        // null status implies that the participant is still active
        return status.orElseThrow(Exception::new);
    }

    public URI joinLRA(LRAProxyParticipant participant, URI lraId) {
        return joinLRA(participant, lraId, 0L, ChronoUnit.SECONDS);
    }

    public URI joinLRA(LRAProxyParticipant participant, URI lraId, Long timeLimit, ChronoUnit unit) {
        // TODO if lraId == null then register a join all new LRAs
        ParticipantProxy proxy = new ParticipantProxy(lraId, UUID.randomUUID().toString(), participant);

        try {
            String pId = proxy.getParticipantId();
            String lra = URLEncoder.encode(lraId.toASCIIString(), StandardCharsets.UTF_8.name());

            participants.add(proxy);

            Optional<String> compensatorData = serializeParticipant(participant);
            URI participantUri = uriBuilder.build(lra, pId);
            long timeLimitInSeconds = Duration.of(timeLimit, unit).getSeconds();

            URI participantRecoveryUrl
                    = lraClient.joinLRA(lraId, timeLimitInSeconds, participantUri, compensatorData.orElse(null));
            return participantRecoveryUrl;
        } catch (Exception e) {
            throw new WebApplicationException(e, Response.status(0)
                .entity(lraId + ": Exception whilst joining with this LRA").build());
        }
    }

    private static Optional<String> serializeParticipant(final Serializable object) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);

            return Optional.of(Base64.getEncoder().encodeToString(baos.toByteArray()));
        } catch (final IOException e) {
//   todo         LRAProxyLogger.i18NLogger.error_cannotSerializeParticipant(e.toString(), e);

            return Optional.empty();
        }
    }

    private static Optional<LRAProxyParticipant> deserializeParticipant(URI lraId, final String objectAsString) {
        return Optional.empty(); // TODO
/*        final byte[] data = Base64.getDecoder().decode(objectAsString);

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return Optional.of((LRAParticipant) ois.readObject());
        } catch (final IOException | ClassNotFoundException e) {
            for (LRAParticipantDeserializer ds : deserializers) {
                LRAParticipant participant = ds.deserialize(lraId, data);

                if (participant != null) {
                    return Optional.of(participant);
                }
            }

            LRAProxyLogger.i18NLogger.warn_cannotDeserializeParticipant(lraId.toExternalForm(),
                    deserializers.size() == 0 ? "null" : deserializers.get(0).getClass().getCanonicalName(),
                    e.getMessage());

            return Optional.empty();
        }*/
    }
}
