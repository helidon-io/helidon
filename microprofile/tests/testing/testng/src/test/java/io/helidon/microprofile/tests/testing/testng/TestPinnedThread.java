/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.Arrays;

import io.helidon.common.testing.virtualthreads.PinningAssertionError;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.annotations.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestPinnedThread {

    private static final String EXPECTED_PINNING_METHOD_NAME = "lambda$testPinned$0";

    @Test
    void testPinnedThread() {
        TestListenerAdapter tla = new TestNGRunner()
                .name("TestPinnedThread")
                .parameter("TestPinnedThread", "true")
                .testClasses(TestPinnedThreadExtra.class)
                .printErrors(false)
                .run();
        Throwable ex = tla.getFailedTests().stream()
                .findFirst()
                .map(ITestResult::getThrowable)
                .orElse(null);
        assertThat(ex, is(not(nullValue())));
        assertThat(ex, is(instanceOf(PinningAssertionError.class)));
        assertThat(ex.getMessage(), startsWith("Pinned virtual threads were detected:"));
        assertThat("Method with pinning is missing from stack strace.", Arrays.asList(ex.getStackTrace()),
                hasItem(new StackTraceElementMatcher(EXPECTED_PINNING_METHOD_NAME)));
    }

    private static class StackTraceElementMatcher extends BaseMatcher<StackTraceElement> {

        private final String methodName;

        StackTraceElementMatcher(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public boolean matches(Object o) {
            return methodName.equals(((StackTraceElement) o).getMethodName());
        }

        @Override
        public void describeMismatch(Object o, Description description) {
            description.appendText("method ").appendValue(methodName)
                    .appendText(" does not match stack trace element ")
                    .appendValue(o);
        }

        @Override
        public void describeTo(Description description) {
        }
    }
}
