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

package io.helidon.integrations.oci.spi;

import java.util.Optional;

import io.helidon.service.registry.Service;

import com.oracle.bmc.Region;

/**
 * An OCI region discovery mechanism to provide {@link com.oracle.bmc.Region} as a service in Helidon
 * service registry.
 * <p>
 * This service is implemented by:
 * <ul>
 *     <li>Config based - just put {@code oci.region} to OCI configuration
 *     (or environment variables/system properties)</li>
 *     <li>Authentication provider based - if the authentication provider implements a
 *     {@link com.oracle.bmc.auth.RegionProvider}, the region will be used</li>
 *     <li>Region from {@link com.oracle.bmc.Region#getRegionFromImds()}</li>
 * </ul>
 * The first one that provides an instance will be used as the value.
 * To customize, create your own service with a default or higher weight.
 */
@Service.Contract
public interface OciRegion {
    /**
     * The region, if it can be provided by this service.
     *
     * @return OCI region
     */
    Optional<Region> region();
}
