/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.webclient.http2;

import org.junit.jupiter.api.Test;

import io.helidon.webclient.http2.Http2ClientStream.ReadState;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadStateTest {

    @Test
    void checkAllValidTransitions() {
        ReadState.INIT.check(ReadState.CONTINUE_100_HEADERS);
        ReadState.INIT.check(ReadState.HEADERS);

        ReadState.CONTINUE_100_HEADERS.check(ReadState.HEADERS);
        ReadState.CONTINUE_100_HEADERS.check(ReadState.DATA);
        ReadState.CONTINUE_100_HEADERS.check(ReadState.END);

        ReadState.HEADERS.check(ReadState.DATA);
        ReadState.HEADERS.check(ReadState.TRAILERS);
        ReadState.HEADERS.check(ReadState.END);

        ReadState.DATA.check(ReadState.TRAILERS);
        ReadState.DATA.check(ReadState.END);

        ReadState.TRAILERS.check(ReadState.END);
    }

    @Test
    void checkSomeInvalidTransitions() {
        assertThrows(IllegalStateException.class, () -> ReadState.CONTINUE_100_HEADERS.check(ReadState.INIT));
        assertThrows(IllegalStateException.class, () -> ReadState.HEADERS.check(ReadState.INIT));

        assertThrows(IllegalStateException.class, () -> ReadState.HEADERS.check(ReadState.CONTINUE_100_HEADERS));
        assertThrows(IllegalStateException.class, () -> ReadState.DATA.check(ReadState.CONTINUE_100_HEADERS));
        assertThrows(IllegalStateException.class, () -> ReadState.END.check(ReadState.CONTINUE_100_HEADERS));

        assertThrows(IllegalStateException.class, () -> ReadState.DATA.check(ReadState.HEADERS));
        assertThrows(IllegalStateException.class, () -> ReadState.TRAILERS.check(ReadState.HEADERS));
        assertThrows(IllegalStateException.class, () -> ReadState.END.check(ReadState.HEADERS));

        assertThrows(IllegalStateException.class, () -> ReadState.TRAILERS.check(ReadState.DATA));
        assertThrows(IllegalStateException.class, () -> ReadState.END.check(ReadState.DATA));

        assertThrows(IllegalStateException.class, () -> ReadState.END.check(ReadState.TRAILERS));
    }
}
