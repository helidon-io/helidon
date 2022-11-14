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

package io.helidon.pico.testsubjects.ext.tbox;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.pico.RunLevel;
import io.helidon.pico.spi.ext.Resetable;
import io.helidon.pico.testsubjects.ext.stacking.Intercepted;
import io.helidon.pico.testsubjects.ext.stacking.InterceptedImpl;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@RunLevel(1)
@Singleton
@Named("testing")
public class TestingSingleton extends InterceptedImpl implements Resetable {
    static AtomicInteger postConstructCount = new AtomicInteger();
    static AtomicInteger preDestroyCount = new AtomicInteger();

    @Inject Blank blank;

    @Inject
    public TestingSingleton(Optional<Intercepted> inner) {
        super(inner);
    }

    @Override
    @PostConstruct
    public void voidMethodWithNoArgs() {
        postConstructCount.incrementAndGet();
    }

    @PreDestroy
    public void preDestroy() {
        preDestroyCount.incrementAndGet();
    }

    public static int getPostConstructCount() {
        return postConstructCount.get();
    }

    public static int getPreDestroyCount() {
        return preDestroyCount.get();
    }

    @Override
    public void clear() {
        postConstructCount.set(0);
        preDestroyCount.set(0);
    }

}
