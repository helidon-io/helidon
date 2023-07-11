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

package io.helidon.integrations.oci.sdk.runtime;

import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.inject.api.InjectionPointInfo;
import io.helidon.inject.api.ContextualServiceQuery;
import io.helidon.inject.api.InjectionPointProvider;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.ServiceInfoBasics;

import com.oracle.bmc.Region;
import jakarta.inject.Singleton;

import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.toNamedProfile;

/**
 * Can optionally be used to return a {@link Region} appropriate for the {@link InjectionPointInfo} context.
 */
@Singleton
@Weight(ServiceInfoBasics.DEFAULT_INJECT_WEIGHT)
class OciRegionProvider implements InjectionPointProvider<Region> {

    OciRegionProvider() {
    }

    @Override
    public Region get() {
        return first(ContextualServiceQuery.builder()
                             .serviceInfoCriteria(InjectionServices.EMPTY_CRITERIA)
                             .expected(false)
                             .build())
                .orElseThrow();
    }

    @Override
    public Optional<Region> first(ContextualServiceQuery query) {
        String requestedNamedProfile = toNamedProfile(query.injectionPointInfo().orElse(null));
        Region region = toRegionFromNamedProfile(requestedNamedProfile);
        if (region == null) {
            region = Region.getRegionFromImds();
        }
        return Optional.ofNullable(region);
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
