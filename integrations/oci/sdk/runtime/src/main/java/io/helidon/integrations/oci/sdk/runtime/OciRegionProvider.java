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
import io.helidon.inject.ContextualServiceQuery;
import io.helidon.inject.InjectionPointProvider;
import io.helidon.inject.Services;
import io.helidon.inject.service.Injection;

import com.oracle.bmc.Region;

/**
 * Can optionally be used to return a {@link Region} appropriate for the {@link io.helidon.inject.service.Ip} context.
 */
@Injection.Singleton
@Weight(Services.INJECT_WEIGHT)
class OciRegionProvider implements InjectionPointProvider<Region> {

    OciRegionProvider() {
    }

    @Override
    public Region get() {
        return first(ContextualServiceQuery.EMPTY)
                .orElseThrow();
    }

    @Override
    public Optional<Region> first(ContextualServiceQuery query) {
        String requestedNamedProfile = query.injectionPoint()
                .map(OciAuthenticationDetailsProvider::toNamedProfile)
                .orElse(null);
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
