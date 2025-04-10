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

package io.helidon.microprofile.tests.testing.testng;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("ALL")
public class TestConfigRestore {

    @Test(priority = Integer.MAX_VALUE)
    void testConfigRestore() {
        TestListenerAdapter tla = new TestNGRunner()
                .name("TestConfigRestore")
                .parameter("TestConfigRestore", "true")
                .testClasses(
                        TestConfigRestoreCase1.class,
                        TestConfigRestoreCase2.class,
                        TestConfigRestoreCase3.class)
                .printErrors(false)
                .run();

        List<Throwable> errors = tla.getFailedTests().stream()
                .map(ITestResult::getThrowable)
                .toList();

        if (!errors.isEmpty()) {
            throw new MultiException(errors);
        }

        assertThat(tla.getPassedTests().size(), is(3));
    }

    static class MultiException extends AssertionError {
        final List<Throwable> throwables;

        MultiException(List<Throwable> ths) {
            super(ths.getFirst().getMessage(), ths.getFirst());
            throwables = ths;
        }

        @Override
        public String getMessage() {
            StringBuffer sb = new StringBuffer(String.format(
                    "A MultiException has %d exceptions. They are:%n",
                    throwables.size()));
            int i = 1;
            for (Throwable th : throwables) {
                String msg = th.getMessage() != null ? ": " + th.getMessage() : "";
                sb.append(String.format(
                        "%d.%s.%s%n",
                        i++, th.getClass().getName(), msg));
            }
            return sb.toString();
        }

        @Override
        public void printStackTrace(PrintStream s) {
            if (throwables.size() <= 0) {
                super.printStackTrace(s);
            } else {
                int i = 1;
                for (Throwable th : throwables) {
                    s.println(String.format(
                            "MultiException stack %d of %d",
                            i++, throwables.size()));
                    th.printStackTrace(s);
                }
            }
        }

        @Override
        public void printStackTrace(PrintWriter s) {
            if (throwables.size() <= 0) {
                super.printStackTrace(s);
            } else {
                int i = 1;
                for (Throwable th : throwables) {
                    s.println(String.format(
                            "MultiException stack %d of %d",
                            i++, throwables.size()));
                    th.printStackTrace(s);
                }
            }
        }

        @Override
        public String toString() {
            return getMessage();
        }
    }
}
