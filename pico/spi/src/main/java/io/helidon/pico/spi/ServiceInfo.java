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

package io.helidon.pico.spi;

import java.util.Optional;
import java.util.Set;

/**
 * Describes a managed service or injection point.
 */
public interface ServiceInfo extends ServiceInfoBasics {

    /**
     * The managed services external contracts / interfaces. These should also be contained within
     * {@link #contractsImplemented()}. External contracts are from other modules other than the module containing
     * the implementation typically.
     *
     * @return the service external contracts implemented
     */
    Set<String> externalContractsImplemented();

    /**
     * The management agent (i.e., the activator) that is responsible for creating and activating - typically build-time created.
     *
     * @return the activator type name
     */
    String activatorTypeName();

    /**
     * The name of the ascribed module, if known.
     *
     * @return the module name
     */
    Optional<String> moduleName();

    /**
     * Determines whether this service info matches the criteria for injection.
     *
     * @param criteria the criteria to compare against
     * @return true if matches
     */
    boolean matches(ServiceInfo criteria);

}
