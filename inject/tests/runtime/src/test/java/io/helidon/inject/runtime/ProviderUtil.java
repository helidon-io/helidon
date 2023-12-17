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

package io.helidon.inject.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.helidon.inject.ServiceProvider;

public class ProviderUtil {
    /**
     * Provides a {@link io.helidon.inject.ServiceProvider#description()}, falling back to {@link #toString()} on the passed
     * provider argument.
     *
     * @param provider the provider
     * @return the description
     */
    static String toDescription(Object provider) {
        if (provider instanceof Optional) {
            provider = ((Optional<?>) provider).orElse(null);
        }

        if (provider instanceof ServiceProvider) {
            return ((ServiceProvider<?>) provider).description();
        }
        return String.valueOf(provider);
    }

    /**
     * Provides a {@link ServiceProvider#description()}, falling back to {@link #toString()} on the passed
     * provider argument.
     *
     * @param coll the collection of providers
     * @return the description
     */
    static List<String> toDescriptions(Collection<?> coll) {
        return coll.stream()
                .map(ProviderUtil::toDescription)
                .toList();
    }
}
