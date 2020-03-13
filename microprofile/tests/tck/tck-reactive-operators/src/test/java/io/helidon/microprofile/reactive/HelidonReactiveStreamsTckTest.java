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
import org.testng.annotations.Factory;

public class HelidonReactiveStreamsTckTest extends ReactiveStreamsTck<HelidonReactiveStreamEngine> {

    private static final TestEnvironment testEnvironment = new TestEnvironment(200);
    private HelidonReactiveStreamEngine engine;
    private ReactiveStreamsFactory rs;
    private ScheduledExecutorService executorService;

    public HelidonReactiveStreamsTckTest() {
        super(testEnvironment);
    }

    @Override
    protected HelidonReactiveStreamEngine createEngine() {
        return new HelidonReactiveStreamEngine();
    }

    @Factory
    @Override
    public Object[] allTests() {
        engine = createEngine();
        rs = createFactory();
        executorService = Executors.newScheduledThreadPool(4);

        ReactiveStreamsApiVerification apiVerification = new ReactiveStreamsApiVerification(rs);
        ReactiveStreamsSpiVerification spiVerification = new CustomReactiveStreamsSpiVerification(testEnvironment, rs, engine, executorService);

        List<Object> allTests = new ArrayList<>();
        allTests.addAll(apiVerification.allTests());
        allTests.addAll(spiVerification.allTests());
        return allTests.stream().filter(this::isEnabled).toArray();
    }

    @AfterSuite(alwaysRun = true)
    public void shutdownEngine() {
        if (engine != null) {
            shutdownEngine(engine);
        }
        executorService.shutdown();
    }

    static class CustomReactiveStreamsSpiVerification extends ReactiveStreamsSpiVerification {

        public CustomReactiveStreamsSpiVerification(TestEnvironment testEnvironment, ReactiveStreamsFactory rs,
                                                    ReactiveStreamsEngine engine, ScheduledExecutorService executorService) {
            super(testEnvironment, rs, engine, executorService);
        }

        @Override
        public List<Object> allTests() {
            VerificationDeps deps = new VerificationDeps();
            CoupledStageVerification.ProcessorVerification processorVerification =
                    new CustomCoupledStageVerification(deps).getProcessorVerification();
            return super.allTests().stream()
                    .map(o -> o instanceof CoupledStageVerification.ProcessorVerification ? processorVerification : o)
                    .collect(Collectors.toList());
        }
    }
}
