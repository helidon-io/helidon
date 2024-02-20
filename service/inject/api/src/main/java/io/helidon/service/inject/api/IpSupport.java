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

package io.helidon.service.inject.api;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.ElementKind;
import io.helidon.service.registry.Dependency;

final class IpSupport {
    private IpSupport() {
    }

    final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Return the dependency if it is an instance of {@link io.helidon.service.inject.api.Ip},
         * or create an Ip that is equivalent to the dependency.
         *
         * @param dependency dependency to convert to injection point
         * @return injection point
         */
        @Prototype.FactoryMethod
        static Ip create(Dependency dependency) {
            if (dependency instanceof Ip ip) {
                return ip;
            }
            return Ip.builder()
                    .from(dependency)
                    .elementKind(ElementKind.CONSTRUCTOR)
                    .build();
        }
    }
}
