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
package io.helidon.data.jdbc.tests.chaos.support;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import io.helidon.data.DataException;
import io.helidon.transaction.TxException;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Centralized assertions for public failures produced by JDBC chaos scenarios.
 */
public final class ChaosFailureAssertions {
    private static final int MAX_THROWABLES = 256;

    private ChaosFailureAssertions() {
    }

    /**
     * Asserts that a failure is a sanitized data failure and omits all supplied canaries.
     *
     * @param failure application-visible failure
     * @param canaries secret values that must not be exposed
     */
    public static void assertSanitizedDataFailure(Throwable failure, String... canaries) {
        assertThat(failure, instanceOf(DataException.class));
        assertNoCanaries(failure, canaries);
    }

    /**
     * Asserts that a failure and its complete application-visible graph omit all supplied canaries.
     *
     * @param failure application-visible failure
     * @param canaries secret values that must not be exposed
     */
    public static void assertSanitizedFailure(Throwable failure, String... canaries) {
        assertNoCanaries(failure, canaries);
    }

    /**
     * Asserts that a transaction failure has a sanitized data failure in its causal graph.
     *
     * @param failure application-visible transaction failure
     * @param canaries secret values that must not be exposed
     */
    public static void assertSanitizedTransactionDataFailure(Throwable failure, String... canaries) {
        assertThat(failure, instanceOf(TxException.class));
        assertNoCanaries(failure, canaries);
        if (!contains(failure, DataException.class)) {
            throw new AssertionError("Expected a sanitized data failure in the transaction failure graph.");
        }
    }

    private static void assertNoCanaries(Throwable failure, String[] canaries) {
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (visited.size() > MAX_THROWABLES) {
                throw new AssertionError("The failure graph exceeded the chaos diagnostic inspection budget.");
            }

            assertSafe(current.getMessage(), canaries);
            assertSafe(current.getLocalizedMessage(), canaries);
            StringWriter rendered = new StringWriter();
            current.printStackTrace(new PrintWriter(rendered));
            assertSafe(rendered.toString(), canaries);

            Throwable cause = current.getCause();
            if (cause != null) {
                pending.addLast(cause);
            }
            for (Throwable suppressed : current.getSuppressed()) {
                pending.addLast(suppressed);
            }
            if (current instanceof SQLException sqlException && sqlException.getNextException() != null) {
                pending.addLast(sqlException.getNextException());
            }
        }
    }

    private static boolean contains(Throwable failure, Class<? extends Throwable> expectedType) {
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (expectedType.isInstance(current)) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause != null) {
                pending.addLast(cause);
            }
            for (Throwable suppressed : current.getSuppressed()) {
                pending.addLast(suppressed);
            }
            if (current instanceof SQLException sqlException && sqlException.getNextException() != null) {
                pending.addLast(sqlException.getNextException());
            }
        }
        return false;
    }

    private static void assertSafe(String text, String[] canaries) {
        if (text == null) {
            return;
        }
        for (String canary : canaries) {
            if (text.contains(canary)) {
                throw new AssertionError("An application-visible chaos failure disclosed a secret canary.");
            }
        }
    }
}
