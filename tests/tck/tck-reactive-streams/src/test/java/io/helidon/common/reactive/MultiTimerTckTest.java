/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.common.reactive;

import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test
public class MultiTimerTckTest extends FlowPublisherVerification<Long> {

    private static ScheduledExecutorService executor;

    public MultiTimerTckTest() {
        super(new TestEnvironment(200));
    }

    @BeforeClass
    public static void beforeClass() {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterClass
    public static void afterClass() {
        executor.shutdown();
    }

    @Override
    public Flow.Publisher<Long> createFlowPublisher(long l) {
        return Multi.timer(1, TimeUnit.MILLISECONDS, executor);
    }

    @Override
    public Flow.Publisher<Long> createFailedFlowPublisher() {
        return null;
    }

    @Override
    public long maxElementsFromPublisher() {
        return 1;
    }
}
