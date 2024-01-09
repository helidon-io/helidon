/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject;

import java.util.Comparator;

import io.helidon.inject.service.RegistryInstance;

class RegistryInstanceComparator implements Comparator<RegistryInstance<?>> {
    private static final RegistryInstanceComparator INSTANCE = new RegistryInstanceComparator();

    private RegistryInstanceComparator() {
    }

    /**
     * Returns a service provider comparator.
     *
     * @return the service provider comparator
     */
    static RegistryInstanceComparator instance() {
        return INSTANCE;
    }

    @Override
    public int compare(RegistryInstance<?> p1,
                       RegistryInstance<?> p2) {
        if (p1 == p2) {
            return 0;
        }

        // unqualified instances always first (even if lower weight)
        if (p1.qualifiers().isEmpty() && !p2.qualifiers().isEmpty()) {
            return -1;
        }

        if (p2.qualifiers().isEmpty() && !p1.qualifiers().isEmpty()) {
            return 1;
        }

        // weights
        int comp = Double.compare(p2.weight(), p1.weight());
        if (comp != 0) {
            return comp;
        }

        // last by name
        return p1.serviceType().compareTo(p2.serviceType());
    }

}
