/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.microprofile.messaging.AbstractCDITest;
import io.helidon.microprofile.messaging.CountableTestBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.DeploymentException;

import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class InnerChannelTest extends AbstractCDITest {

    @Override
    public void setUp() {
        //Starting container manually
    }

    static Stream<CdiTestCase> testCaseSource() {
        return Stream.of(
                CdiTestCase.from(InternalChannelsBean.class),
                CdiTestCase.from(InnerProcessorBean.class),
                CdiTestCase.from(MultipleProcessorBean.class),
                CdiTestCase.from(MultipleTypeProcessorChainBean.class),
                CdiTestCase.from(PrimitiveProcessorBean.class)
        );
    }

    @ParameterizedTest
    @MethodSource("testCaseSource")
    void innerChannelBeanTest(CdiTestCase testCase) {
        cdiContainer = startCdiContainer(Collections.emptyMap(), testCase.getClazzes());
        testCase.getCountableBeanClasses().forEach(c -> {
            CountableTestBean countableTestBean = CDI.current().select(c).get();
            // Wait till all messages are delivered
            assertAllReceived(countableTestBean);
        });
    }

    @Test
    void notConnectedIncomingChannelTest() {
        assertThrows(DeploymentException.class, () ->
                cdiContainer = startCdiContainer(Collections.emptyMap(), NotConnectedIncommingChannelBean.class));
    }

    @Test
    void notConnectedOutgoingChannelTest() {
        assertThrows(DeploymentException.class, () ->
                cdiContainer = startCdiContainer(Collections.emptyMap(), NotConnectedOutgoingChannelBean.class));
    }
}
