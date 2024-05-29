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

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;

import com.oracle.bmc.Region;

final class OciConfigSupport {
    /**
     * Primary hostname of metadata service.
     */
    // we do not use the constant, as it is marked as internal, and we only need the IP address anyway
    // see com.oracle.bmc.auth.AbstractFederationClientAuthenticationDetailsProviderBuilder.METADATA_SERVICE_BASE_URL
    static final String IMDS_HOSTNAME = "169.254.169.254";

    private OciConfigSupport() {
    }

    @Prototype.FactoryMethod
    static Region createRegion(Config config) {
        return config.asString()
                .map(Region::fromRegionCodeOrId)
                .get();
    }
}
