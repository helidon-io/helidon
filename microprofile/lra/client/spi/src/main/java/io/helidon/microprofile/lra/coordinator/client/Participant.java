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

/**
 * Participant metadata needed by LRA coordinator.
 */
public interface Participant {

    /**
     * URI of participant's method annotated with {@link org.eclipse.microprofile.lra.annotation.Compensate @Compensate}.
     * Expected to be called by coordinator in case LRA participant is enrolled in is cancelled.
     *
     * @return URI of JaxRs compensating resource or empty
     */
    Optional<URI> compensate();

    /**
     * URI of participant's method annotated with {@link org.eclipse.microprofile.lra.annotation.Complete @Complete}.
     * Expected to be called by coordinator in case LRA participant is enrolled in is completed.
     *
     * @return URI of JaxRs completion resource or empty
     */
    Optional<URI> complete();

    /**
     * URI of participant's method annotated with {@link org.eclipse.microprofile.lra.annotation.Forget @Forget}.
     * Expected to be called by coordinator in case LRA closing/completing takes longer,
     * e.g. complete/compensate methods are not independent and coordinator needs to recover (wait and query)
     * actual status of participant.
     *
     * @return URI of JaxRs forget resource or empty
     */
    Optional<URI> forget();

    /**
     * URI of participant's method annotated with {@link org.eclipse.microprofile.lra.annotation.ws.rs.Leave @Leave}.
     *
     * @return URI of JaxRs leave resource or empty
     */
    Optional<URI> leave();

    /**
     * URI of participant's method annotated with {@link org.eclipse.microprofile.lra.annotation.AfterLRA @AfterLRA}.
     *
     * @return URI of JaxRs after LRA resource or empty
     */
    Optional<URI> after();

    /**
     * URI of participant's method annotated with {@link org.eclipse.microprofile.lra.annotation.Status @Status}.
     * Expected to be called by coordinator in case it needs to ask for participant status.
     *
     * @return URI of JaxRs status resource or empty
     */
    Optional<URI> status();
}
