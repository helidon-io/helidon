/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico;

import java.util.Optional;

/**
 * Tracks the transformations of {@link ServiceProvider}'s {@link ActivationStatus} in lifecycle activity (i.e., activation
 * startup and deactivation shutdown).
 *
 * @see Activator
 * @see DeActivator
 */
public interface ActivationLog {

    /**
     * Expected to be called during service creation and activation to capture the activation log transcripts.
     *
     * @param entry the log entry to record
     * @return the (perhaps decorated) activation log entry
     */
    ActivationLogEntry record(ActivationLogEntry entry);

    /**
     * Optionally provide a means to query the activation log, if query is possible. If query is not possible then an empty
     * will be returned.
     *
     * @return the optional query API of log activation records
     */
    default Optional<ActivationLogQuery> toQuery() {
        return Optional.empty();
    }

}
