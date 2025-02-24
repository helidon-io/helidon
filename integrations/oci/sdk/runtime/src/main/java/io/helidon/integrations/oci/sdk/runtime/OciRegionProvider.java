/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.runtime;

import java.util.Optional;

import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

import com.oracle.bmc.Region;

/**
 * Can optionally be used to return a {@link Region} appropriate for the {@link io.helidon.service.registry.Dependency} context.
 */
@Service.Singleton
class OciRegionProvider implements Service.InjectionPointFactory<Region> {

    OciRegionProvider() {
    }

    @Override
    public Optional<Service.QualifiedInstance<Region>> first(Lookup query) {
        String requestedNamedProfile = query.dependency()
                .map(OciAuthenticationDetailsProvider::toNamedProfile)
                .orElse(null);
        Region region = toRegionFromNamedProfile(requestedNamedProfile);
        if (region == null) {
            region = Region.getRegionFromImds();
        }
        return Optional.ofNullable(region)
                .map(it -> Service.QualifiedInstance.create(it, Qualifier.createNamed(it.getRegionId())));
    }

    static Region toRegionFromNamedProfile(String requestedNamedProfile) {
        if (requestedNamedProfile == null || requestedNamedProfile.isBlank()) {
            return null;
        }

        try {
            return Region.fromRegionCodeOrId(requestedNamedProfile);
        } catch (Exception e) {
            // eat it
            return null;
        }
    }

}
