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
package io.helidon.common.reactive;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class MultiFromNotTrustedInputStreamTckTest extends MultiFromTrustedInputStreamTckTest {
    private static final Logger LOGGER = Logger.getLogger(MultiFromNotTrustedInputStreamTckTest.class.getName());

    static final int BUFFER_SIZE = 4;

    ExecutorService executorService = null;

    @Override
    @BeforeMethod
    public void setUp() throws Exception {
        super.setUp();
        executorService = Executors.newFixedThreadPool(4);
    }

    @AfterMethod
    public void tearDown() {
        executorService.shutdown();
        try {
            executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Executor service shutdown interrupted!");
        } finally {
            executorService = null;
        }
    }

    protected Multi<ByteBuffer> getPublisher(InputStream is) {
        return IoMulti.multiFromStreamBuilder(is)
                .executor(executorService)
                .byteBufferSize(BUFFER_SIZE)
                .build();
    }

    @Test
    public void testCumulativeDeferredBadRequest() {
        Flow.Publisher<ByteBuffer> pub = createFlowPublisher(3);
        TestSubscriber<ByteBuffer> sub = new TestSubscriber<>() {
            @Override
            public void onSubscribe(final Flow.Subscription s) {
                super.onSubscribe(s);
                s.request(10);
                s.request(-1L);
            }
        };
        pub.subscribe(sub);
        sub.awaitDone(100, TimeUnit.MILLISECONDS);
        sub.assertError(IllegalArgumentException.class);
    }
}
