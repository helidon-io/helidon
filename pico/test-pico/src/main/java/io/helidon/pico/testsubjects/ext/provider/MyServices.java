/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.testsubjects.ext.provider;

import java.util.Objects;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.InjectionPointProvider;
import io.helidon.pico.ServiceInfo;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

public class MyServices {

    @Singleton
    public static class MyConcreteClassContractPerRequestProvider implements Provider<MyConcreteClassContract> {
        private volatile int counter;

        @Override
        public MyConcreteClassContract get() {
            int num = counter++;
            return new MyConcreteClassContract(getClass().getSimpleName() + ":instance_" + num);
        }
    }

    @Singleton
    @Weight(Weighted.DEFAULT_WEIGHT + 1)
    public static class MyConcreteClassContractPerRequestIPProvider implements InjectionPointProvider<MyConcreteClassContract> {
        private volatile int counter;

        private boolean postConstructed;
        private MyConcreteClassContract injected;

        @PostConstruct
        public void postConstruct() {
            assert (Objects.nonNull(injected));
            postConstructed = true;
        }

        @Inject
        void setMyConcreteClassContract(MyConcreteClassContract injected) {
            assert (Objects.isNull(this.injected));
            this.injected = Objects.requireNonNull(injected);
        }

        @Override
        public MyConcreteClassContract get(InjectionPointInfo ipInfoCtx, ServiceInfo criteria, boolean expected) {
            assert (Objects.nonNull(injected));
            assert (postConstructed);
            int num = counter++;
            String id = getClass().getSimpleName() + ":instance_" + num + ", "
                    + ipInfoCtx + ", " + criteria + ", " + expected + ", " + injected;
            return new MyConcreteClassContract(id);
        }
    }

}
