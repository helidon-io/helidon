/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.net.URI;
import java.util.Optional;

/**
 * LRA participant managed by coordinator.
 */
public interface Participant {

    /**
     * Invoked when closed 200, 202, 409, 410.
     *
     * @return optional uri
     */
    Optional<URI> completeURI();

    /**
     * Invoked when cancelled 200, 202, 409, 410.
     *
     * @return optional uri
     */
    Optional<URI> compensateURI();

    /**
     * Invoked when finalized 200.
     *
     * @return optional uri
     */
    Optional<URI> afterURI();

    /**
     * Invoked when cleaning up 200, 410.
     *
     * @return optional uri
     */
    Optional<URI> forgetURI();

    /**
     * Directly updates status of participant 200, 202, 410.
     *
     * @return optional uri
     */
    Optional<URI> statusURI();
}
