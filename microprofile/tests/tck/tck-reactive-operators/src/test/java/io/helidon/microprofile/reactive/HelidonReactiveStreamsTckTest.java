/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreamsFactory;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.operators.tck.ReactiveStreamsTck;
import org.eclipse.microprofile.reactive.streams.operators.tck.api.ReactiveStreamsApiVerification;
import org.eclipse.microprofile.reactive.streams.operators.tck.spi.CoupledStageVerification;
import org.eclipse.microprofile.reactive.streams.operators.tck.spi.CustomCoupledStageVerification;
import org.eclipse.microprofile.reactive.streams.operators.tck.spi.ReactiveStreamsSpiVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Factory;

public class HelidonReactiveStreamsTckTest extends ReactiveStreamsTck<HelidonReactiveStreamsEngine> {

    public HelidonReactiveStreamsTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    protected HelidonReactiveStreamsEngine createEngine() {
        return new HelidonReactiveStreamsEngine();
    }

    private ExecutorService executor;

    @BeforeSuite(alwaysRun = true)
    public void before() {
        executor = Executors.newSingleThreadExecutor();
        HelidonReactiveStreamsEngine.setCoupledExecutor(executor);
    }

    @AfterSuite(alwaysRun = true)
    public void after() {
        HelidonReactiveStreamsEngine.setCoupledExecutor(null);
        executor.shutdown();
    }

}
