/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.compatibility.v4;

import java.util.List;

import io.helidon.service.registry.Service;

/**
 * Constructor injection of differently qualified implementations of the same contract.
 */
@Service.Singleton
public class LegacyNamedService {
    private final LegacyFactory.Product first;
    private final LegacyFactory.Product second;

    @Service.Inject
    LegacyNamedService(@Service.Named("legacy-first") LegacyFactory.Product first,
                       @Service.Named("legacy-second") LegacyFactory.Product second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Values of the injected products, in injection-point order.
     *
     * @return first and second product values
     */
    public List<String> values() {
        return List.of(first.value(), second.value());
    }
}
