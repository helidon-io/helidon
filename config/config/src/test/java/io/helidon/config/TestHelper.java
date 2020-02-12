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
 */
package io.helidon.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.config.spi.ConfigParser;

/**
 * Testing utilities.
 */
public final class TestHelper {
    private TestHelper() {
    }

    /**
     * Create an input stream on the provided string.
     *
     * @param string data
     * @return input stream on UTF-8 bytes of the data
     */
    public static InputStream toInputStream(String string) {
        return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads specified {@code input stream} into String.
     *
     * @param inputStream inputStream
     * @return String
     * @throws java.io.IOException in case of error during reading from readable
     */
    public static String inputStreamToString(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    public static ConfigParser.Content parsableContent(String data, String mediaType, Object stamp) {
        return ConfigParser.Content.builder()
                .data(toInputStream(data))
                .mediaType(mediaType)
                .stamp(stamp)
                .build();
    }
}
