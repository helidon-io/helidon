/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.io.IOException;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.util.concurrent.Flow;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ConfigHelper}.
 */
public class ConfigHelperTest {

    @Test
    public void testCreateReaderFromReader() throws IOException {
        StringReader readable = new StringReader("test-value");

        assertThat(ConfigHelper.createReader(readable), is(readable));
    }

    @Test
    public void testCreateReaderFromCharBuffer() throws IOException {
        assertThat(readerToString(ConfigHelper.createReader(CharBuffer.wrap("test-value"))), is("test-value"));
    }

    @Test
    public void testSubscriber() {
        //mocks
        Function<Long, Boolean> onNextFunction = mock(Function.class);
        Flow.Subscription subscription = mock(Flow.Subscription.class);

        //create Subscriber
        Flow.Subscriber<Long> subscriber = ConfigHelper.subscriber(onNextFunction);

        //onSubscribe
        subscriber.onSubscribe(subscription);
        //    request(Long.MAX_VALUE) has been invoked
        verify(subscription).request(Long.MAX_VALUE);

        //MOCK onNext
        when(onNextFunction.apply(1L)).thenReturn(true);
        when(onNextFunction.apply(2L)).thenReturn(true);
        when(onNextFunction.apply(3L)).thenReturn(false);
        // 2x onNext -> true
        subscriber.onNext(1L);
        subscriber.onNext(2L);
        //    function invoked 2x, cancel never
        verify(onNextFunction, times(2)).apply(any());
        verify(subscription, never()).cancel();
        // 1x onNext -> false
        subscriber.onNext(3L);
        //    function invoked 2+1x, cancel 1x
        verify(onNextFunction, times(2 + 1)).apply(any());
        verify(subscription, times(1)).cancel();
    }

    /**
     * Reads specified {@code readable} into String.
     *
     * @param readable readable
     * @return String
     * @throws IOException in case of error during reading from readable
     */
    public static String readerToString(Readable readable) throws IOException {
        StringBuilder builder = new StringBuilder();
        CharBuffer chars = CharBuffer.allocate(100);
        int charsRead;
        do {
            charsRead = readable.read(chars);
            if (charsRead > 0) {
                builder.append(chars.flip().toString());
            }
        } while (charsRead > 0);
        return builder.toString();
    }

}
