/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

public class MultiFromInputStreamTest {

    private static ExecutorService executorService;

    @BeforeAll
    static void beforeAll() {
        executorService = Executors.newFixedThreadPool(4);
    }

    @AfterAll
    static void afterAll() {
        executorService.shutdown();
    }

    @Test
    public void testInputStream() {
        byte[] initialArray = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 6, 4, 5, 78, 55, 44};

        InputStream is = new ByteArrayInputStream(initialArray);

        List<Byte> result = IoMulti.createInputStream(is)
                .flatMapIterable((ByteBuffer b) -> {
                    List<Byte> list = new LinkedList<>();
                    while (b.remaining() > 0) {
                        list.add(b.get());
                    }
                    return list;
                })
                .collectList()
                .await(100, TimeUnit.MILLISECONDS);
        assertThat(result, equalTo(toList(initialArray)));
    }

    @RepeatedTest(value = 20, name = "buffer size {currentRepetition}")
    void longStringTrustedStream(RepetitionInfo repetitionInfo) {
        var bufferSize = repetitionInfo.getCurrentRepetition();
        longString(is -> IoMulti.builderInputStream(is)
                .byteBufferSize(bufferSize)
                .build());
    }

    @RepeatedTest(value = 20, name = "buffer size {currentRepetition}")
    void longStringNotTrustedStream(RepetitionInfo repetitionInfo) {
        var bufferSize = repetitionInfo.getCurrentRepetition();
        longString(is -> IoMulti.builderInputStream(is)
                .executor(executorService)
                .byteBufferSize(bufferSize)
                .build());
    }

    private void longString(Function<InputStream, Multi<ByteBuffer>> pubCreator) {
        final var STRING_LENGTH = 200_000;
        final var token = "Lorem ipsum ".toCharArray();
        final var sb = new StringBuilder();

        byte token_index = 0;
        for (int i = 0; i < STRING_LENGTH; i++) {
            sb.append(token[token_index++]);
            if (token_index == token.length) {
                token_index = 0;
            }
        }

        final var expected = sb.toString();

        InputStream is = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8));

        String result = pubCreator.apply(is)
                .map(StandardCharsets.UTF_8::decode)
                .map(CharBuffer::toString)
                .map(CharSequence.class::cast)
                .collectStream(Collectors.joining())
                .await(100, TimeUnit.SECONDS);

        assertThat(result, equalTo(expected));
    }

    private static List<Byte> toList(byte[] array) {
        List<Byte> result = new ArrayList<>(array.length);
        for (byte b : array) {
            result.add(b);
        }
        return result;
    }
}
