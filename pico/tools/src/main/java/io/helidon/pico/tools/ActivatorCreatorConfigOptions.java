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

import io.helidon.builder.Builder;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.pico.PicoServicesConfig;

/**
 * These options are expected to have an affinity match to "permit" properties found within
 * {@link io.helidon.pico.PicoServicesConfig}. These are used to fine tune the type of code generated.
 *
 * @see ActivatorCreator
 */
@Builder
public interface ActivatorCreatorConfigOptions {

    /**
     * This option (use -A at compile time) should be set to opt into {@link io.helidon.pico.Application} stub
     * creation.
     */
    String TAG_APPLICATION_PRE_CREATE = PicoServicesConfig.NAME + ".application.pre.create";

    /**
     * Should jsr-330 be followed in strict accordance. The default here is actually set to false for two reasons:
     * <ol>
     * <li> It is usually not what people expect (i.e., losing @inject on overridden injectable setter methods), and
     * <li> The implementation will e slightly more performant (i.e., the "rules" governing jsr-330 requires that base classes
     * are injected prior to derived classes. This coupled with point 1 requires special additional book-keeping to be
     * managed by the activators that are generated).
     * </ol>
     *
     * @return true if strict mode is in effect
     */
    boolean isSupportsJsr330InStrictMode();

    /**
     * Should a {@link io.helidon.pico.Module} be created during activator creation. The default is true.
     *
     * @return true if the module should be created
     */
    @ConfiguredOption("true")
    boolean isModuleCreated();

    /**
     * Should a stub {@link io.helidon.pico.Application} be created during activator creation. The default is false.
     * This feature can opt'ed in by using {@link #TAG_APPLICATION_PRE_CREATE}.  Pre-req requires that this can
     * only be enabled if {@link #isModuleCreated()} is also enabled.
     *
     * @return true if the application should be created
     */
    boolean isApplicationPreCreated();

}
