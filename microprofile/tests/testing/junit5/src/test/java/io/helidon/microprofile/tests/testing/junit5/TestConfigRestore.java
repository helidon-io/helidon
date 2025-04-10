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

package io.helidon.microprofile.tests.testing.junit5;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

import io.helidon.microprofile.testing.AddConfigBlock;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@SuppressWarnings("ALL")
class TestConfigRestore {

    @Test
    void testConfigRestore() {
        Events events = EngineTestKit.engine("junit-jupiter")
                .configurationParameter("TestConfigRestore", "true")
                .selectors(
                        selectClass(TestCase1.class),
                        selectClass(TestCase2.class),
                        selectClass(TestCase3.class))
                .execute()
                .allEvents();

        List<Throwable> errors = events.stream()
                .flatMap(e -> e.getPayload(TestExecutionResult.class).stream())
                .flatMap(r -> r.getThrowable().stream())
                .toList();

        if (!errors.isEmpty()) {
            throw new MultiException(errors);
        }
    }

    @EnabledIfParameter(key = "TestConfigRestore", value = "true")
    static class TestCase1 {

        @Test
        void testExisting() {
            Config config = ConfigProvider.getConfig();
            assertThat(config.getValue("foo", String.class), is("systemProperty"));
        }
    }

    @EnabledIfParameter(key = "TestConfigRestore", value = "true")
    @HelidonTest
    @AddConfigBlock(value = """
        foo=configBlock
        config_ordinal=1000
        """)
    static class TestCase2 {

        @Inject
        @ConfigProperty(name = "foo")
        String value;

        @Test
        void testSynthetic() {
            assertThat(value, is("configBlock"));
        }
    }

    @EnabledIfParameter(key = "TestConfigRestore", value = "true")
    static class TestCase3 {

        @Test
        void testExisting() {
            Config config = ConfigProvider.getConfig();
            assertThat(config.getValue("foo", String.class), is("systemProperty"));
        }
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
