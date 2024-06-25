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
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciRegion;
import io.helidon.service.registry.Service;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.RegionProvider;

/**
 * Region provider that uses an available OCI authentication method, if it yields an
 * authentication details provider that implements a region provider.
 */
@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT - 20)
class RegionProviderAuthenticationMethod implements OciRegion {
    private final LazyValue<Optional<Region>> region;

    RegionProviderAuthenticationMethod(Supplier<Optional<AbstractAuthenticationDetailsProvider>> atnProvider) {

        this.region = LazyValue.create(() -> {
            var provider = atnProvider.get();
            if (provider.isEmpty()) {
                return Optional.empty();
            }
            if (provider.get() instanceof RegionProvider regionProvider) {
                return Optional.of(regionProvider.getRegion());
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<Region> region() {
        return region.get();
    }
}
