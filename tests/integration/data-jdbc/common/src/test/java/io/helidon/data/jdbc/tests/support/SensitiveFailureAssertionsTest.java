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
package io.helidon.data.jdbc.tests.support;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SensitiveFailureAssertionsTest {

    /**
     * Proves a canary is found through cause, suppressed, and JDBC next-exception relationships.
     */
    @Test
    void traversesCausesSuppressedFailuresAndSqlNextExceptions() {
        SQLException sqlFailure = new SQLException("safe SQL failure");
        sqlFailure.setNextException(new SQLException("private-next-canary"));
        IllegalStateException failure = new IllegalStateException("safe outer failure", sqlFailure);
        failure.addSuppressed(new IllegalArgumentException("safe suppressed failure"));

        assertThrows(AssertionError.class,
                     () -> SensitiveFailureAssertions.assertNoSecrets(failure, "private-next-canary"));
    }

    /**
     * Proves shared throwable nodes are visited by identity without duplicate traversal or false budget exhaustion.
     */
    @Test
    void identityProtectsSharedFailureGraphs() {
        IllegalStateException shared = new IllegalStateException("safe shared failure");
        IllegalArgumentException failure = new IllegalArgumentException("safe outer failure", shared);
        failure.addSuppressed(shared);

        SensitiveFailureAssertions.assertNoSecrets(failure, "private-absent-canary");
    }

    /**
     * Proves a malformed cyclic cause graph terminates while still inspecting every reachable throwable.
     */
    @Test
    void cycleProtectsCauseGraphs() {
        CyclicFailure first = new CyclicFailure("safe first failure");
        CyclicFailure second = new CyclicFailure("safe second failure");
        first.cause(second);
        second.cause(first);

        SensitiveFailureAssertions.assertNoSecrets(first, "private-absent-canary");
    }

    private static final class CyclicFailure extends RuntimeException {
        private Throwable cause;

        private CyclicFailure(String message) {
            super(message, null);
        }

        @Override
        public synchronized Throwable getCause() {
            return cause;
        }

        private void cause(Throwable cause) {
            this.cause = cause;
        }
    }
}
