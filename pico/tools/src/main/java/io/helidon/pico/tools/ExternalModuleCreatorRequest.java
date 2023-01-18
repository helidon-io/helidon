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

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.builder.Builder;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.pico.QualifierAndValue;

/**
 * The request payload that is used by {@link ExternalModuleCreator}.
 *
 * Note that the thread context classloader should be setup appropriately so that service types can be resolved
 * based upon the packages requested to scan.
 */
@Builder
public interface ExternalModuleCreatorRequest extends GeneralCreatorRequest {

    /**
     * The set of packages to analyze and eventually generate pico activators against.
     *
     * @return the list of package names to analyze and target for activator creation
     */
    List<String> packageNamesToScan();

    /**
     * Optionally, provides a means to map additional qualifiers to service types.
     *
     * @return any qualifiers that should be mapped into the generated services.
     */
    Map<String, Set<QualifierAndValue>> serviceTypeToQualifiersMap();

    /**
     * Config options w.r.t. planned activator creation.
     *
     * @return the config options for activator creation
     */
    ActivatorCreatorConfigOptions activatorCreatorConfigOptions();

    /**
     * Optionally, set this to allow inner classes to be processed for potential pico activators.
     *
     * @return allows inner classes to be processed for potential pico activators
     */
    @ConfiguredOption("true")
    boolean innerClassesProcessed();

}
