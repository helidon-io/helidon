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

package io.helidon.inject.api;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Comparator appropriate for {@link DependencyInfo}.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public class DependencyInfoComparator implements java.util.Comparator<DependencyInfo>, Serializable {
    private static final Comparator<DependencyInfo> INSTANCE = new DependencyInfoComparator();

    private DependencyInfoComparator() {
    }

    /**
     * Dependency info comparator.
     *
     * @return instance of the comparator
     */
    public static Comparator<DependencyInfo> instance() {
        return INSTANCE;
    }

    @Override
    public int compare(DependencyInfo o1,
                       DependencyInfo o2) {
        InjectionPointInfo ipi1 = o1.injectionPointDependencies().iterator().next();
        InjectionPointInfo ipi2 = o2.injectionPointDependencies().iterator().next();

        java.util.Comparator<InjectionPointInfo> idComp = java.util.Comparator.comparing(InjectionPointInfo::baseIdentity);
        java.util.Comparator<InjectionPointInfo> posComp =
                java.util.Comparator.comparing(DependencyInfoComparator::elementOffsetOf);

        return idComp.thenComparing(posComp).compare(ipi1, ipi2);
    }

    private static int elementOffsetOf(InjectionPointInfo ipi) {
        return ipi.elementOffset().orElse(0);
    }
}
