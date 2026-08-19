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
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Verifies that an application-visible failure graph does not disclose secret canaries.
 */
public final class SensitiveFailureAssertions {
    private static final int MAX_THROWABLES = 256;

    private SensitiveFailureAssertions() {
    }

    /**
     * Scans messages, rendered traces, causes, suppressed failures, and SQL next-exception links.
     *
     * @param failure application-visible failure
     * @param secrets secret canaries
     */
    public static void assertNoSecrets(Throwable failure, String... secrets) {
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (visited.size() > MAX_THROWABLES) {
                throw new AssertionError("The failure graph exceeded the sensitive-data inspection budget.");
            }

            assertSafe(current.getMessage(), secrets);
            assertSafe(current.getLocalizedMessage(), secrets);
            StringWriter rendered = new StringWriter();
            current.printStackTrace(new PrintWriter(rendered));
            assertSafe(rendered.toString(), secrets);

            Throwable cause = current.getCause();
            if (cause != null) {
                pending.addLast(cause);
            }
            for (Throwable suppressed : current.getSuppressed()) {
                pending.addLast(suppressed);
            }
            if (current instanceof SQLException sqlException) {
                SQLException next = sqlException.getNextException();
                if (next != null) {
                    pending.addLast(next);
                }
            }
        }
    }

    private static void assertSafe(String text, String[] secrets) {
        if (text == null) {
            return;
        }
        for (String secret : secrets) {
            if (text.contains(secret)) {
                throw new AssertionError("An application-visible failure disclosed a secret canary.");
            }
        }
    }
}
