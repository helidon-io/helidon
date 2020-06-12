/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * {@link MultiFromOutputStream} reactive streams tck test.
 */
public class MultiFromOutputStreamTckTest extends FlowPublisherVerification<ByteBuffer> {

    private static TidyTestExecutor executor;
    private static final TestEnvironment env = new TestEnvironment(150);

    public MultiFromOutputStreamTckTest() {
        super(env);
    }

    @Override
    public Flow.Publisher<ByteBuffer> createFlowPublisher(long l) {
        CountDownLatch countDownLatch = new CountDownLatch((int) l);
        MultiFromOutputStream osp = IoMulti.createOutputStream();
        executor.submit(() -> {
            for (long n = 0; n < l; n++) {
                final long fn = n;
                try {
                    osp.write(("token" + fn).getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    // expected by some tests
                }
                countDownLatch.countDown();
            }
            try {
                countDownLatch.await();
                osp.close();
            } catch (IOException | InterruptedException e) {
                // expected by some tests
            }
        });

        return osp;
    }

    @Override
    public Flow.Publisher<ByteBuffer> createFailedFlowPublisher() {
        MultiFromOutputStream osp = IoMulti.createOutputStream();
        osp.fail(new Exception("test"));
        return osp;
    }

    @Override
    public long maxElementsFromPublisher() {
        return Integer.MAX_VALUE - 1;
    }

    @Test
    public void stochastic_spec103_mustSignalOnMethodsSequentially() throws Throwable {
        for (int i = 0; i < 100; i++) {
            super.stochastic_spec103_mustSignalOnMethodsSequentially();
        }
    }

    @BeforeClass
    public void beforeClass() {
        executor = new TidyTestExecutor();
    }

    @AfterClass
    public void afterClass() {
        executor.shutdownNow();
    }

    @AfterMethod
    public void tearDown() throws InterruptedException {
        executor.awaitAllFinished();
    }
}
