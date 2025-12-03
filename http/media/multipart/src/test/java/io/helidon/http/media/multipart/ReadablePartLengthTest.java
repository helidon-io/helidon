/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.http.media.multipart;

import java.io.IOException;
import java.io.InputStream;

import io.helidon.common.buffers.DataReader;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class ReadablePartLengthTest {
    private static final byte[] BUFFER = new byte[1];
    static {
        // -31
        BUFFER[0] = (byte) 'á';
    }
    @Test
    public void testReadInt() throws IOException {
        MediaContext mediaContext = MediaContext.create();
        WritableHeaders<?> headers = WritableHeaders.create();
        DataReader dataReader = new DataReader(() -> BUFFER);
        int index = 0;
        int partLength = 1;

        ReadablePartLength part = new ReadablePartLength(mediaContext, headers, dataReader, index, partLength);
        InputStream is = part.inputStream();
        int value = is.read();

        // the contract of the InputStream.read() method claims the result is between 0 (inclusive) and 255
        assertThat(value, greaterThan(0));
    }
}
