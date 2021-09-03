/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.microprofile.reactive;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreamsFactory;
import org.eclipse.microprofile.reactive.streams.operators.tck.ReactiveStreamsTck;
import org.reactivestreams.tck.TestEnvironment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test
public class HelidonReactiveStreamsEngineTckTest extends ReactiveStreamsTck<HelidonReactiveStreamsEngine> {

    public HelidonReactiveStreamsEngineTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    protected HelidonReactiveStreamsEngine createEngine() {
        return HelidonReactiveStreamsEngine.INSTANCE;
    }

    @Override
    protected ReactiveStreamsFactory createFactory() {
        return HelidonReactivePublisherFactory.INSTANCE;
    }

    @Override
    protected boolean isEnabled(Object test) {
        return true;
    }

    private ExecutorService executor;

    @BeforeClass(alwaysRun = true)
    public void before() {
        executor = Executors.newSingleThreadExecutor();
        HelidonReactiveStreamsEngine.setCoupledExecutor(executor);
    }

    @AfterClass(alwaysRun = true)
    public void after() {
        HelidonReactiveStreamsEngine.setCoupledExecutor(null);
        executor.shutdown();
    }

    @Test
    public void hasExecutor() {
        org.testng.Assert.assertNotNull(executor);
    }
}
