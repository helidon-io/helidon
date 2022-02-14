/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

package io.helidon.lra.coordinator.client;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.reactive.Single;

import org.eclipse.microprofile.lra.annotation.LRAStatus;

/**
 * Abstraction over specific coordinator.
 */
public interface CoordinatorClient {

    /**
     * Prefix of headers which should be propagated to the coordinator.
     */
    String CONF_KEY_COORDINATOR_HEADERS_PROPAGATION_PREFIX = "mp.lra.coordinator.headers-propagation.prefix";

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
     * Default coordinator url.
     */
    String CONF_DEFAULT_COORDINATOR_URL = "http://localhost:8070/lra-coordinator";

    /**
     * Initialization of the properties provided by LRA client.
     *
     * @param coordinatorUriSupplier url of the coordinator
     * @param timeout                general timeout for coordinator calls
     * @param timeoutUnit            timeout unit for coordinator calls
     */
    void init(Supplier<URI> coordinatorUriSupplier, long timeout, TimeUnit timeoutUnit);

    /**
     * Ask coordinator to start new LRA and return its id.
     *
     * @param clientID id specifying originating method/resource
     * @param timeout  after what time should be LRA cancelled automatically
     * @return id of the new LRA
     * @deprecated Use {@link io.helidon.lra.coordinator.client.CoordinatorClient#start(String, PropagatedHeaders, long)} instead
     */
    @Deprecated
    default Single<URI> start(String clientID, long timeout) {
        return start(clientID, PropagatedHeaders.noop(), timeout);
    }

    /**
     * Ask coordinator to start new LRA and return its id.
     *
     * @param clientID id specifying originating method/resource
     * @param headers  headers to be propagated to the coordinator
     * @param timeout  after what time should be LRA cancelled automatically
     * @return id of the new LRA
     */
    Single<URI> start(String clientID, PropagatedHeaders headers, long timeout);

    /**
     * Ask coordinator to start new LRA and return its id.
     *
     * @param parentLRA in case new LRA should be a child of already existing one
     * @param clientID  id specifying originating method/resource
     * @param timeout   after what time should be LRA cancelled automatically
     * @return id of the new LRA
     * @deprecated Use
     * {@link io.helidon.lra.coordinator.client.CoordinatorClient#start(java.net.URI, String, PropagatedHeaders, long)} instead
     */
    @Deprecated
    default Single<URI> start(URI parentLRA, String clientID, long timeout) {
        return start(parentLRA, clientID, PropagatedHeaders.noop(), timeout);
    }

    /**
     * Ask coordinator to start new LRA and return its id.
     *
     * @param parentLRA in case new LRA should be a child of already existing one
     * @param clientID  id specifying originating method/resource
     * @param headers   headers to be propagated to the coordinator
     * @param timeout   after what time should be LRA cancelled automatically
     * @return id of the new LRA
     */
    Single<URI> start(URI parentLRA, String clientID, PropagatedHeaders headers, long timeout);

    /**
     * Join existing LRA with participant.
     *
     * @param lraId       id of existing LRA
     * @param timeLimit   time limit in milliseconds after which should be LRA cancelled, 0 means never
     * @param participant participant metadata with URLs to be called when complete/compensate ...
     * @return recovery URI if supported by coordinator or empty
     * @deprecated Use
     * {@link io.helidon.lra.coordinator.client.CoordinatorClient#join(java.net.URI, PropagatedHeaders, long, Participant)} instead
     */
    @Deprecated
    default Single<Optional<URI>> join(URI lraId, long timeLimit, Participant participant) {
        return join(lraId, PropagatedHeaders.noop(), timeLimit, participant);
    }

    /**
     * Join existing LRA with participant.
     *
     * @param lraId       id of existing LRA
     * @param headers     headers to be propagated to the coordinator
     * @param timeLimit   time limit in milliseconds after which should be LRA cancelled, 0 means never
     * @param participant participant metadata with URLs to be called when complete/compensate ...
     * @return recovery URI if supported by coordinator or empty
     */
    Single<Optional<URI>> join(URI lraId, PropagatedHeaders headers, long timeLimit, Participant participant);

    /**
     * Cancel LRA if its active. Should cause coordinator to compensate its participants.
     *
     * @param lraId id of the LRA to be cancelled
     * @return single future of the cancel call
     * @deprecated Use
     * {@link io.helidon.lra.coordinator.client.CoordinatorClient#cancel(java.net.URI, PropagatedHeaders)} instead
     */
    @Deprecated
    default Single<Void> cancel(URI lraId) {
        return cancel(lraId, PropagatedHeaders.noop());
    }

    /**
     * Cancel LRA if its active. Should cause coordinator to compensate its participants.
     *
     * @param lraId   id of the LRA to be cancelled
     * @param headers headers to be propagated to the coordinator
     * @return single future of the cancel call
     */
    Single<Void> cancel(URI lraId, PropagatedHeaders headers);

    /**
     * Close LRA if its active. Should cause coordinator to complete its participants.
     *
     * @param lraId id of the LRA to be closed
     * @return single future of the cancel call
     * @deprecated Use
     * {@link io.helidon.lra.coordinator.client.CoordinatorClient#close(java.net.URI, PropagatedHeaders)} instead
     */
    @Deprecated
    default Single<Void> close(URI lraId) {
        return close(lraId, PropagatedHeaders.noop());
    }

    /**
     * Close LRA if its active. Should cause coordinator to complete its participants.
     *
     * @param lraId   id of the LRA to be closed
     * @param headers headers to be propagated to the coordinator
     * @return single future of the cancel call
     */
    Single<Void> close(URI lraId, PropagatedHeaders headers);

    /**
     * Leave LRA. Supplied participant won't be part of specified LRA any more,
     * no compensation or completion will be executed on it.
     *
     * @param lraId       id of the LRA that should be left by supplied participant
     * @param participant participant which will leave
     * @return single future of the cancel call
     * @deprecated Use
     * {@link io.helidon.lra.coordinator.client.CoordinatorClient#leave(java.net.URI, PropagatedHeaders, Participant)} instead
     */
    @Deprecated
    default Single<Void> leave(URI lraId, Participant participant) {
        return leave(lraId, PropagatedHeaders.noop(), participant);
    }

    /**
     * Leave LRA. Supplied participant won't be part of specified LRA any more,
     * no compensation or completion will be executed on it.
     *
     * @param lraId       id of the LRA that should be left by supplied participant
     * @param headers     headers to be propagated to the coordinator
     * @param participant participant which will leave
     * @return single future of the cancel call
     */
    Single<Void> leave(URI lraId, PropagatedHeaders headers, Participant participant);

    /**
     * Return status of specified LRA.
     *
     * @param lraId id of the queried LRA
     * @return {@link org.eclipse.microprofile.lra.annotation.LRAStatus} of the queried LRA
     * @deprecated Use
     * {@link io.helidon.lra.coordinator.client.CoordinatorClient#status(java.net.URI, PropagatedHeaders)} instead
     */
    @Deprecated
    default Single<LRAStatus> status(URI lraId) {
        return status(lraId, PropagatedHeaders.noop());
    }

    /**
     * Return status of specified LRA.
     *
     * @param lraId   id of the queried LRA
     * @param headers headers to be propagated to the coordinator
     * @return {@link org.eclipse.microprofile.lra.annotation.LRAStatus} of the queried LRA
     */
    Single<LRAStatus> status(URI lraId, PropagatedHeaders headers);
}
