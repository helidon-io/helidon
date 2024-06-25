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

import java.net.URI;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciRegion;
import io.helidon.service.registry.Service;

import com.oracle.bmc.Region;

@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT - 100)
class RegionProviderSdk implements OciRegion {
    private final LazyValue<Optional<Region>> region;

    RegionProviderSdk(Supplier<OciConfig> config) {
        this.region = LazyValue.create(() -> Optional.ofNullable(regionFromImds(config.get())));
    }

    /**
     * There is a 30 second timeout configured, so this has a relatively low weight.
     * We want a different way to get the region if available.
     */
    static Region regionFromImds(OciConfig ociConfig) {
        if (HelidonOci.imdsAvailable(ociConfig)) {
            Optional<URI> uri = ociConfig.imdsBaseUri();
            return uri.map(URI::toString)
                    .map(Region::getRegionFromImds)
                    .orElseGet(() -> {
                        Region.registerFromInstanceMetadataService();
                        return Region.getRegionFromImds();
                    });

        }
        return null;
    }

    @Override
    public Optional<Region> region() {
        return region.get();
    }
}
