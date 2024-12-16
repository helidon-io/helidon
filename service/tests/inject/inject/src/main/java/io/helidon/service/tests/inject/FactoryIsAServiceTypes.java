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

import java.util.function.Supplier;

import io.helidon.service.inject.api.Injection;

final class FactoryIsAServiceTypes {
    private FactoryIsAServiceTypes() {
    }

    interface FactoryContract {
        String name();
    }

    interface TargetType {
        String name();
    }

    @Injection.Singleton
    static class MyFactory implements FactoryContract, Supplier<TargetType> {
        @Override
        public String name() {
            return "Factory";
        }

        @Override
        public TargetType get() {
            return new TargetTypeImpl("Created");
        }
    }

    @Injection.Singleton
    static class InjectionReceiver {
        private final FactoryContract factoryContract;
        private final TargetType targetType;

        @Injection.Inject
        InjectionReceiver(FactoryContract factoryContract,
                          TargetType targetType) {
            this.factoryContract = factoryContract;
            this.targetType = targetType;
        }

        FactoryContract factoryContract() {
            return factoryContract;
        }

        TargetType targetType() {
            return targetType;
        }
    }

    static class TargetTypeImpl implements TargetType {
        private final String name;

        TargetTypeImpl(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }
}
