/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.helidon.microprofile.lra.coordinator.client;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.lra.annotation.LRAStatus;

/**
 * Abstraction over specific coordinator.
 */
public interface CoordinatorClient {

    /**
     * URL of the coordinator to be used for orchestrating Long Running Actions.
     */
    String CONF_KEY_COORDINATOR_URL = "mp.lra.coordinator.url";
    /**
     * Timeout for synchronous communication with coordinator.
     */
    String CONF_KEY_COORDINATOR_TIMEOUT = "mp.lra.coordinator.timeout";
    /**
     * Timeout unit for synchronous communication with coordinator.
     * Values of enum {@link java.util.concurrent.TimeUnit} are expected.
     */
    String CONF_KEY_COORDINATOR_TIMEOUT_UNIT = "mp.lra.coordinator.timeout-unit";

    /**
     * Initialization of the properties provided by LRA client.
     *
     * @param coordinatorUri url of the coordinator
     * @param timeout        general timeout for coordinator calls
     * @param timeoutUnit    timeout unit for coordinator calls
     */
    void init(String coordinatorUri, long timeout, TimeUnit timeoutUnit);

    /**
     * Ask coordinator to start new LRA and return its id.
     *
     * @param parentLRA in case new LRA should be a child of already existing one
     * @param clientID  id specifying originating method/resource
     * @param timeout   after what time should be LRA cancelled automatically
     * @return id of the new LRA
     */
    URI start(URI parentLRA, String clientID, Long timeout);

    /**
     * Join existing LRA with participant.
     *
     * @param lraId       id of existing LRA
     * @param timeLimit   time limit in milliseconds after which should be LRA cancelled, 0 means never
     * @param participant participant metadata with URLs to be called when complete/compensate ...
     * @return recovery URI if supported by coordinator or empty
     */
    Optional<URI> join(URI lraId, Long timeLimit, Participant participant);

    /**
     * Cancel LRA if its active. Should cause coordinator to compensate its participants.
     *
     * @param lraId id of the LRA to be cancelled
     */
    void cancel(URI lraId);

    /**
     * Close LRA if its active. Should cause coordinator to complete its participants.
     *
     * @param lraId id of the LRA to be closed
     */
    void close(URI lraId);

    /**
     * Leave LRA. Supplied participant won't be part of specified LRA any more,
     * no compensation or completion will be executed on it.
     *
     * @param lraId       id of the LRA that should be left by supplied participant
     * @param participant participant which will leave LRA
     */
    void leave(URI lraId, Participant participant);

    /**
     * Return status of specified LRA.
     *
     * @param lraId id of the queried LRA
     * @return {@link org.eclipse.microprofile.lra.annotation.LRAStatus} of the queried LRA
     */
    LRAStatus status(URI lraId);

    /**
     * Adapt all calls that may come from coordinator.
     *
     * @param headers editable call headers
     */
    void preprocessHeaders(Headers headers);
}
