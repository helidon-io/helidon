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

package io.helidon.builder.test.testsubjects;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;

@RuntimeType.PrototypedBy(RuntimeTypeExampleInterfaceConfig.class)
public interface RuntimeTypeExampleInterface extends RuntimeType.Api<RuntimeTypeExampleInterfaceConfig> {

    static RuntimeTypeExampleInterface create(RuntimeTypeExampleInterfaceConfig prototype) {
        return new AnImplementation(prototype);
    }

    static RuntimeTypeExampleInterfaceConfig.Builder builder() {
        return RuntimeTypeExampleInterfaceConfig.builder();
    }

    static RuntimeTypeExampleInterface create(Consumer<RuntimeTypeExampleInterfaceConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    class AnImplementation implements RuntimeTypeExampleInterface {
        private final RuntimeTypeExampleInterfaceConfig prototype;

        private AnImplementation(RuntimeTypeExampleInterfaceConfig prototype) {
            this.prototype = prototype;
        }

        @Override
        public RuntimeTypeExampleInterfaceConfig prototype() {
            return prototype;
        }
    }

}
