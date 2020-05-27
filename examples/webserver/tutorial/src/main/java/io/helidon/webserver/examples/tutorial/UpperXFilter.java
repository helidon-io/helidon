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

package io.helidon.webserver.examples.tutorial;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Function;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;


/**
 * All 'x' must be upper case.
 * <p>
 * This is a naive implementation.
 */
public final class UpperXFilter implements Function<Publisher<DataChunk>, Publisher<DataChunk>> {

    private static final Charset CHARSET = StandardCharsets.US_ASCII;
    private static final byte LOWER_X = "x".getBytes(CHARSET)[0];
    private static final byte UPPER_X = "X".getBytes(CHARSET)[0];

    @Override
    public Publisher<DataChunk> apply(Publisher<DataChunk> publisher) {
        return Multi.from(publisher).map(responseChunk -> {
            if (responseChunk == null) {
                return null;
            }
            try {
                ByteBuffer[] originalData = responseChunk.data();
                ByteBuffer[] processedData = new ByteBuffer[originalData.length];
                for (int i=0 ; i < originalData.length ; i++) {
                    // Naive but works for demo
                    byte[] buff = new byte[originalData[i].remaining()];
                    originalData[i].get(buff);
                    for (int j = 0; j < buff.length; j++) {
                        if (buff[j] == LOWER_X) {
                            buff[j] = UPPER_X;
                        }
                    }
                    processedData[i] = ByteBuffer.wrap(buff);
                }
                return DataChunk.create(responseChunk.flush(), processedData);
            } finally {
                responseChunk.release();
            }
        });
    }
}
