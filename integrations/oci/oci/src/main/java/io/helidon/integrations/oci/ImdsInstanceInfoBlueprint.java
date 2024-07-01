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

package io.helidon.integrations.oci;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import jakarta.json.JsonObject;

/**
 * Instance information retrieved from Imds
 */
@Prototype.Blueprint
@Prototype.Configured
interface ImdsInstanceInfoBlueprint {
    /**
     * Display Name.
     *
     * @return Display Name of the Instance
     */
    @Option.Configured
    String displayName();

    /**
     * Host Name.
     *
     * @return Host Name of the Instance
     */
    @Option.Configured
    String hostName();

    /**
     * Canonical Region Name.
     *
     * @return Canonical Region Name of where the Instance exists
     */
    @Option.Configured
    String canonicalRegionName();

    /**
     * Region Name.
     *
     * @return Short Region Name of where the Instance exists
     */
    @Option.Configured
    String region();

    /**
     * Oci Availability Domain Name.
     *
     * @return Physical Availaibility Domain Name where the Instance exists
     */
    @Option.Configured
    String ociAdName();

    /**
     * Fault Domain Name.
     *
     * @return Fault Domain Name where the Instance exists
     */
    @Option.Configured
    String faultDomain();

    /**
     * Compartment Id
     *
     * @return Compartment Id where the Instance was provisioned.
     */
    @Option.Configured
    String compartmentId();

    /**
     * Tenant Id
     *
     * @return Tenant Id where the Instance was provisioned.
     */
    @Option.Configured
    String tenantId();

    /**
     * Instance Data
     *
     * @return Full information about the Instance as a {@link jakarta.json.JsonObject}
     */
    @Option.Configured
    JsonObject jsonObject();
}
