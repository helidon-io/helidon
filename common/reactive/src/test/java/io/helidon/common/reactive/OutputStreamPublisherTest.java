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
import java.io.PrintWriter;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link OutputStreamPublisher} test.
 */
public class OutputStreamPublisherTest {

    @Test
    public void testBasic() {
        OutputStreamPublisher publisher = new OutputStreamPublisher();
        TestSubscriber<ByteBuffer> subscriber = new TestSubscriber<>();
        publisher.subscribe(subscriber);
        subscriber.requestMax();
        PrintWriter printer = new PrintWriter(publisher);
        printer.print("foo");
        printer.close();
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        // Filter out any OutputStreamPublisher#FLUSH_BUFFER
        long size = subscriber.getItems().stream().filter(b -> b.capacity() > 0).count();
        assertThat(size, is(equalTo(1L)));
        ByteBuffer bb = subscriber.getItems().get(0);
        assertThat(new String(bb.array()), is(equalTo("foo")));
    }

    @Test
    public void testSignalCloseCompleteWithException() {
        OutputStreamPublisher publisher = new OutputStreamPublisher();
        publisher.signalCloseComplete(new IllegalStateException("foo!"));
        try {
            publisher.close();
            fail("an exception should have been thrown");
        } catch (IOException ex) {
            assertThat(ex.getCause(), is(not(nullValue())));
            assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
        }
    }

    @Test
    public void testCloseOnNoDataWritten() throws IOException {
        OutputStreamPublisher publisher = new OutputStreamPublisher();
        // this should return immediately
        publisher.close();
    }
}
