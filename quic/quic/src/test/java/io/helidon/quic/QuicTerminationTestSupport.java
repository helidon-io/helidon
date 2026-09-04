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

package io.helidon.quic;

/**
 * Test support for constructing immutable QUIC termination observations.
 */
public final class QuicTerminationTestSupport {
    private QuicTerminationTestSupport() {
    }

    /**
     * Creates a local observation from a close command.
     *
     * @param command close command
     * @return local termination observation
     */
    public static QuicTermination local(QuicCloseCommand command) {
        return QuicTermination.local(command, command.keySpace().orElse(null));
    }
}
