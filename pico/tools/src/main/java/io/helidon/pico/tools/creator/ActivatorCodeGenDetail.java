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

package io.helidon.pico.tools.creator;

import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.spi.ext.Dependencies;

/**
 * The specifics for a single {@link ServiceProvider} that was codegen'ed.
 *
 * @see ActivatorCreatorResponse#getServiceTypeDetails()
 */
public interface ActivatorCodeGenDetail extends GeneralCodeGenDetail {

    /**
     * @return Additional meta-information describing what is offered by the generated service.
     */
    ServiceInfo getServiceInfo();

    /**
     * @return Additional meta-information describing what the generated service depends upon.
     */
    Dependencies getDependencies();

    /**
     * @return The source code generated for the {@link ServiceProvider}/{@link io.helidon.pico.Activator}.
     */
    String getBody();

}
