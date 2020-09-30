/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.messaging.inner;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.CDI;

import io.helidon.microprofile.messaging.AbstractCDITest;
import io.helidon.microprofile.messaging.AssertableTestBean;
import io.helidon.microprofile.messaging.AsyncTestBean;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.util.ClassFilter;
import org.junit.platform.commons.util.ReflectionUtils;

public class ChannelTest extends AbstractCDITest {

    @Override
    public void setUp() {
        //Starting container manually
    }

    static Stream<CdiTestCase> testCaseSource() {
        return ReflectionUtils
                .findAllClassesInPackage(
                        ChannelTest.class.getPackage().getName(),
                        ClassFilter.of(c -> Objects.nonNull(c.getAnnotation(ApplicationScoped.class))))
                .stream()
                .map(CdiTestCase::from);
    }

    @ParameterizedTest
    @MethodSource("testCaseSource")
    @Disabled("Fails due to backpressure issues in InternalProcessor, reenable once fixed")
    void innerChannelBeanTest(CdiTestCase testCase) {
        Optional<? extends Class<? extends Throwable>> expectedThrowable = testCase.getExpectedThrowable();
        if (expectedThrowable.isPresent()) {
            assertThrows(expectedThrowable.get(), () ->
                    cdiContainer = startCdiContainer(Collections.emptyMap(), testCase.getClazzes()));
        } else {
            cdiContainer = startCdiContainer(Collections.emptyMap(), testCase.getClazzes());
            testCase.getAsyncBeanClasses()
                    .map(c -> CDI.current().select(c).get())
                    .forEach(AsyncTestBean::tearDown);
            // Wait till all messages are delivered
            testCase.getCountableBeanClasses()
                    .map(c -> CDI.current().select(c).get())
                    .forEach(this::assertAllReceived);
            testCase.getCompletableBeanClasses()
                    .map(c -> CDI.current().select(c).get())
                    .forEach(AssertableTestBean::assertValid);
        }
    }
}
