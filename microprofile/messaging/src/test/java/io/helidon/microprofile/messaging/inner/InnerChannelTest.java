/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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
import io.helidon.microprofile.messaging.CountableTestBean;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.util.ClassFilter;
import org.junit.platform.commons.util.ReflectionUtils;

public class InnerChannelTest extends AbstractCDITest {

    @Override
    public void setUp() {
        //Starting container manually
    }

    static Stream<CdiTestCase> testCaseSource() {
        return ReflectionUtils
                .findAllClassesInPackage(
                        InnerChannelTest.class.getPackage().getName(),
                        ClassFilter.of(c -> Objects.nonNull(c.getAnnotation(ApplicationScoped.class))))
                .stream()
                .peek(System.out::println)
                .map(CdiTestCase::from);
    }

    @ParameterizedTest
    @MethodSource("testCaseSource")
    void innerChannelBeanTest(CdiTestCase testCase) {
        Optional<? extends Class<? extends Throwable>> expectedThrowable = testCase.getExpectedThrowable();
        if (expectedThrowable.isPresent()) {
            assertThrows(expectedThrowable.get(), () ->
                    cdiContainer = startCdiContainer(Collections.emptyMap(), testCase.getClazzes()));
        } else {
            cdiContainer = startCdiContainer(Collections.emptyMap(), testCase.getClazzes());
            testCase.getCountableBeanClasses().forEach(c -> {
                CountableTestBean countableTestBean = CDI.current().select(c).get();
                // Wait till all messages are delivered
                assertAllReceived(countableTestBean);
            });
            testCase.getCompletableBeanClasses().forEach(c -> {
                AssertableTestBean assertableTestBean = CDI.current().select(c).get();
                assertableTestBean.assertValid();
            });
        }
    }
}
