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
package io.helidon.webserver.http1;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.helidon.webserver.http1.Http1ServerResponse.BlockingOutputStream;
import static io.helidon.webserver.http1.Http1ServerResponse.ClosingBufferedOutputStream;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.MatcherAssert.assertThat;

class ClosingBufferedOutputStreamTest {

    private final List<String> delegateCalls = new ArrayList<>();

    @Test
    void closingBeforeCloseTest() {
        BlockingOutputStream bos = mockBlockingOutputStream();
        ClosingBufferedOutputStream cbos = new ClosingBufferedOutputStream(bos, 1);
        cbos.commit();
        cbos.close();
        assertThat(delegateCalls, contains("closing", "close"));
    }

    private BlockingOutputStream mockBlockingOutputStream() {
        BlockingOutputStream bos = Mockito.mock(BlockingOutputStream.class);
        Mockito.doAnswer(i -> {
            delegateCalls.add("closing");
            return null;
        }).when(bos).closing();
        Mockito.doAnswer(i -> {
            delegateCalls.add("close");
            return null;
        }).when(bos).close();
        return bos;
    }
}
