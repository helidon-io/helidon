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

package io.helidon.inject.tests.inject.provider;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.Lookup;

public class MyServices {

    @Injection.Service
    public static class MyConcreteClassContractPerRequestProvider implements Supplier<MyConcreteClassContract> {
        public static volatile AtomicInteger COUNTER = new AtomicInteger();

        @Override
        public MyConcreteClassContract get() {
            int num = COUNTER.getAndIncrement();
            return new MyConcreteClassContract(getClass().getSimpleName() + ":instance_" + num);
        }
    }

    @Injection.Singleton
    @Weight(Weighted.DEFAULT_WEIGHT + 1)
    public static class MyConcreteClassContractPerRequestIPProvider implements InjectionPointProvider<MyConcreteClassContract> {
        private volatile int counter;

        private boolean postConstructed;
        private MyConcreteClassContract injected;

        @Injection.PostConstruct
        public void postConstruct() {
            assert (injected != null);
            postConstructed = true;
        }

        @Override
        public Optional<MyConcreteClassContract> first(Lookup query) {
            assert (injected != null);
            assert (postConstructed);
            int num = counter++;
            String id = getClass().getSimpleName() + ":instance_" + num + ", " + injected;
            return Optional.of(new MyConcreteClassContract(id));
        }

        @Injection.Inject
        void setMyConcreteClassContract(MyConcreteClassContract injected) {
            assert (this.injected == null);
            this.injected = Objects.requireNonNull(injected);
        }

    }

}
