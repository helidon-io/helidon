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

package io.helidon.http.http2;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class WindowSizeTest {
    @Test
    void observesUpdateBeforeBlockingWaitStarts() {
        ConnectionFlowControl connection = ConnectionFlowControl.clientBuilder((_, _) -> { }).build();
        var outbound = (WindowSizeImpl.Outbound) connection.outbound();
        outbound.decrementWindowSize(outbound.getRemainingWindowSize());

        boolean waitedForUpdate = outbound.blockTillUpdate(() -> outbound.incrementWindowSize(1));

        assertThat("positive window must be rechecked before waiting", waitedForUpdate, is(false));
    }
}
