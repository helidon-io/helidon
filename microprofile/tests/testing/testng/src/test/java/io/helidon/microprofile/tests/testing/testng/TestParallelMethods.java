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

import org.testng.TestListenerAdapter;
import org.testng.annotations.Test;
import org.testng.xml.XmlSuite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestParallelMethods {

    @Test
    void testParallelMethods() {
        TestListenerAdapter tla = new TestNGRunner()
                .name("TestParallelMethods")
                .parameter("TestParallelMethods", "true")
                .testClasses(TestParallelMethodsExtra.class)
                .parallel(XmlSuite.ParallelMode.METHODS)
                .run();
        assertThat(tla.getPassedTests().size(), is(2));
    }
}
