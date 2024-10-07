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

package io.helidon.service.tests.inject;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.Service;

interface ParameterizedTypes {
    @Service.Contract
    interface GenericContract<T> {
        T get();
    }

    @Injection.Singleton
    class GenericProducerString implements GenericContract<String> {
        @Override
        public String get() {
            return "Hello";
        }
    }

    @Injection.Singleton
    class GenericProducerInt implements GenericContract<Integer> {
        @Override
        public Integer get() {
            return 42;
        }
    }

    @Injection.Singleton
    class GenericContractReceiver {
        private final GenericContract<String> genericContractString;
        private final GenericContract<Integer> genericContractInteger;

        @Injection.Inject
        GenericContractReceiver(GenericContract<String> genericContractString,
                                GenericContract<Integer> genericContractInteger) {
            this.genericContractString = genericContractString;
            this.genericContractInteger = genericContractInteger;
        }

        String getString() {
            return genericContractString.get() + "-" + genericContractInteger.get();
        }
    }
}
