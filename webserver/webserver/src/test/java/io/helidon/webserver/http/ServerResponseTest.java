/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ServerResponseTest {
    @ParameterizedTest
    @CsvSource({
            "0, 6, abcdef",
            "2, 3, cde",
            "4, 2, ef",
            "0, 0, ''",
            "2, 0, ''",
            "6, 0, ''"
    })
    void defaultSendUsesByteCount(int position, int length, String expected) {
        ServerResponse response = mock(ServerResponse.class, CALLS_REAL_METHODS);
        byte[] bytes = "abcdef".getBytes(StandardCharsets.UTF_8);

        response.send(bytes, position, length);

        verify(response).send(expected.getBytes(StandardCharsets.UTF_8));
    }
}
