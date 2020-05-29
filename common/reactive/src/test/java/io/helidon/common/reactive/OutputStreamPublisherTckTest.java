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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

/**
 * {@link OutputStreamPublisher} reactive streams tck test.
 */
public class OutputStreamPublisherTckTest extends FlowPublisherVerification<ByteBuffer> {

    private static ExecutorService executor;

    public OutputStreamPublisherTckTest() {
        super(new TestEnvironment(150));
    }

    @Override
    public Flow.Publisher<ByteBuffer> createFlowPublisher(long l) {
        OutputStreamPublisher osp = new OutputStreamPublisher();
        executor.execute(() -> {
            for (long n = 0; n < l; n++) {
                try {
                    osp.write(("token" + n).getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                }
            }
            try {
                osp.close();
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        });

        return Multi.from(osp);
    }

    @Override
    public Flow.Publisher<ByteBuffer> createFailedFlowPublisher() {
        OutputStreamPublisher osp = new OutputStreamPublisher();
        osp.signalCloseComplete(new Exception("test"));
        return Multi.from(osp);
    }

    @BeforeClass
    public static void beforeClass() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void afterClass() {
        executor.shutdown();
    }
}
