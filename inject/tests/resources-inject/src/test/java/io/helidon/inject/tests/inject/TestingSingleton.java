/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.inject;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.inject.Resettable;
import io.helidon.inject.service.Injection;
import io.helidon.inject.tests.inject.stacking.CommonContract;
import io.helidon.inject.tests.inject.tbox.Awl;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Injection.RunLevel(Injection.RunLevel.STARTUP)
@Singleton
@Named("testing")
public class TestingSingleton implements Resettable, CommonContract {
    final static AtomicInteger postConstructCount = new AtomicInteger();
    final static AtomicInteger preDestroyCount = new AtomicInteger();

    CommonContract inner;
    @Inject Provider<Awl> awlProvider;

    @Inject
    TestingSingleton(Optional<CommonContract> inner) {
        this.inner = inner.orElseThrow();
    }

    @PostConstruct
    public void voidMethodWithNoArgs() {
        postConstructCount.incrementAndGet();
    }

    @PreDestroy
    public void preDestroy() {
        preDestroyCount.incrementAndGet();
    }

    public static int postConstructCount() {
        return postConstructCount.get();
    }

    public static int preDestroyCount() {
        return preDestroyCount.get();
    }

    @Override
    public void reset(boolean deep) {
        postConstructCount.set(0);
        preDestroyCount.set(0);
    }

    @Override
    public CommonContract getInner() {
        return inner;
    }

    @Override
    public String sayHello(String arg) {
        return "TS: " + getInner().sayHello(arg);
    }
}
