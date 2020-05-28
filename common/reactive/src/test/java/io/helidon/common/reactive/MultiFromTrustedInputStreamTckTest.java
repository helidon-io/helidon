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
package io.helidon.common.reactive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;
import org.testng.annotations.Test;

@Test
public class MultiFromTrustedInputStreamTckTest extends FlowPublisherVerification<ByteBuffer> {
    static final int BUFFER_SIZE = 4;

    public MultiFromTrustedInputStreamTckTest() {
        super(new TestEnvironment(50));
    }

    @Override
    public Flow.Publisher<ByteBuffer> createFlowPublisher(long l) {
        AtomicLong remaining = new AtomicLong(l * BUFFER_SIZE);
        final byte[] theByte = {0};
        InputStream is = new InputStream() {
            @Override
            public int read() {
                if (0 == remaining.getAndDecrement()) {
                    return -1;
                }
                return theByte[0]++;
            }
        };
        return getPublisher(is);
    }

    protected Multi<ByteBuffer> getPublisher(InputStream is) {
        return IoMulti.builder(is)
                .byteBufferSize(BUFFER_SIZE)
                .build();
    }

    @Override
    public Flow.Publisher<ByteBuffer> createFailedFlowPublisher() {
        InputStream is = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("BOOM!!!");
            }

            @Override
            public int available() throws IOException {
                throw new IOException("BOOM!!!");
            }
        };
        return getPublisher(is);
    }
}
