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

package io.helidon.integrations.jta.narayana;

import com.arjuna.ats.arjuna.utils.Process;

/**
 * Replacement for Arjuna's default {@code com.arjuna.ats.internal.arjuna.utils.SocketProcessId}
 * which is not needed since JDK 9 introduced {@code ProcessHandle}.
 * As no extra socket needs to be opened in this implementation, CRaC snapshotting is not affected.
 */
public final class ProcessId implements Process {
    @Override
    public int getpid() {
        // linux pid can be max 2^22(PID_MAX_LIMIT)
        // macos 99998
        // windows is complicated by shouldn't be more than 2^31-1
        return Math.toIntExact(ProcessHandle.current().pid());
    }
}
