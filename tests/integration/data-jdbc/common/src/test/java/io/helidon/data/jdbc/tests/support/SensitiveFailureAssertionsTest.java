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

import java.io.PrintWriter;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SensitiveFailureAssertionsTest {
    private static final int SHARED_REFERENCE_COUNT = 257;

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
     * Proves identity traversal terminates cyclic cause graphs and visits a high-fan-out shared node only once.
     */
    @Test
    void identityProtectsCyclicAndSharedFailureGraphs() {
        CyclicFailure first = new CyclicFailure("safe first failure");
        CyclicFailure shared = new CyclicFailure("safe shared failure");
        first.cause(shared);
        shared.cause(first);
        for (int i = 0; i < SHARED_REFERENCE_COUNT; i++) {
            first.addSuppressed(shared);
        }

        SensitiveFailureAssertions.assertNoSecrets(first, "private-absent-canary");

        assertThat(first.causeReads(), is(1));
        assertThat(shared.causeReads(), is(1));
    }

    private static final class CyclicFailure extends IllegalStateException {
        private Throwable cause;
        private int causeReads;

        private CyclicFailure(String message) {
            super(message, null);
        }

        @Override
        public Throwable getCause() {
            causeReads++;
            if (causeReads > 1) {
                throw new AssertionError("Failure node was traversed more than once.");
            }
            return cause;
        }

        @Override
        public void printStackTrace(PrintWriter writer) {
            writer.print(getMessage());
        }

        private void cause(Throwable cause) {
            this.cause = cause;
        }

        private int causeReads() {
            return causeReads;
        }
    }
}
