/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

import io.helidon.pico.Contract;

/**
 * Implementors of this contract are responsible for creating the {@link io.helidon.pico.Application} instance.
 * This is used by Pico's maven-plugin.
 */
@Contract
public interface ApplicationCreator {

    /**
     * Used to create the {@link io.helidon.pico.Application} source for the entire
     * application / assembly.
     *
     * @param request the request for what to generate
     * @return the result from the create operation
     */
    ApplicationCreatorResponse createApplication(
            ApplicationCreatorRequest request);

}
