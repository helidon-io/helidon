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

import java.util.Optional;

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

    RegionProviderSdk() {
        this.region = LazyValue.create(() -> Optional.ofNullable(regionFromImds()));
    }

    /**
     * There is a 30 second timeout configured, so this has a relatively low weight.
     * We want a different way to get the region if available.
     */
    static Region regionFromImds() {
        Region.registerFromInstanceMetadataService();
        return Region.getRegionFromImds();
    }

    @Override
    public Optional<Region> region() {
        return region.get();
    }
}
