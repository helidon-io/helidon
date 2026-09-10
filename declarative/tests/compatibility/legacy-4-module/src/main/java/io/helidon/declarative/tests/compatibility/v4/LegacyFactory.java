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

import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

/**
 * Factory producing qualified services through a Helidon 4 descriptor.
 */
@Service.Singleton
public class LegacyFactory implements Service.ServicesFactory<LegacyFactory.Product> {
    @Override
    public List<Service.QualifiedInstance<Product>> services() {
        return List.of(Service.QualifiedInstance.create(() -> "first-product", Qualifier.createNamed("legacy-first")),
                       Service.QualifiedInstance.create(() -> "second-product", Qualifier.createNamed("legacy-second")));
    }

    /**
     * Contract satisfied only by factory-produced instances.
     */
    @Service.Contract
    public interface Product {
        /**
         * Product value.
         *
         * @return value identifying the produced instance
         */
        String value();
    }
}
