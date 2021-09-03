/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.integration.jersey;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.SubmissionPublisher;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link SubscriberInputStream}.
 */
public class SubscriberInputStreamTest {
    @Test
    public void testSingle() throws IOException {
        String value = "the long value I want to push through";
        SubmissionPublisher<ByteBuffer> publisher = new SubmissionPublisher<>();

        SubscriberInputStream is = new SubscriberInputStream();
        publisher.subscribe(is);

        // asynchronously publish
        new Thread(() -> {
            publisher.submit(ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8)));
            publisher.close();
        }).start();

        byte[] buffer = new byte[16];
        int len;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }

        String result = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        assertThat(result, is(value));
    }

    @Test
    public void testMultiple() throws IOException {
        String oneValue = "someBytes";
        SubmissionPublisher<ByteBuffer> publisher = new SubmissionPublisher<>();

        SubscriberInputStream is = new SubscriberInputStream();
        publisher.subscribe(is);

        // asynchronously publish
        new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                publisher.submit(ByteBuffer.wrap(oneValue.getBytes(StandardCharsets.UTF_8)));
            }
            publisher.close();
        }).start();

        byte[] buffer = new byte[16];
        int len;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }

        String result = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        StringBuilder expectedResult = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            expectedResult.append(oneValue);
        }
        assertThat(result, is(expectedResult.toString()));
    }
}
