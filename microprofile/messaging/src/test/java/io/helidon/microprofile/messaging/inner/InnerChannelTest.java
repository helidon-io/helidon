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

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import javax.enterprise.inject.spi.CDI;

import io.helidon.microprofile.messaging.AbstractCDITest;
import io.helidon.microprofile.messaging.CompletableTestBean;
import io.helidon.microprofile.messaging.CountableTestBean;
import io.helidon.microprofile.messaging.inner.ack.ManualAckBean;
import io.helidon.microprofile.messaging.inner.ack.ChainWithPayloadAckBean;


import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;


import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class InnerChannelTest extends AbstractCDITest {

    @Override
    public void setUp() {
        //Starting container manually
    }

    static Stream<CdiTestCase> testCaseSource() {
        return Stream.of(
                //Positive tests
                PublisherBuilderTransformerV2Bean.class,
                PublisherBuilderTransformerV1Bean.class,
                PublisherFromPublisherV2Bean.class,
                PublisherFromPublisherV1Bean.class,
                ProcessorBean.class,
                ProcessorBuilderBean.class,
                PullForEachBean.class,
                CompletionStageV1Bean.class,
                PublisherPayloadV6Bean.class,
                PublisherPayloadV5Bean.class,
                PublisherPayloadV4Bean.class,
                PublisherPayloadV3Bean.class,
                PublisherPayloadV1Bean.class,
                PublisherSubscriberBuilderV2Bean.class,
                PublisherSubscriberBuilderV1Bean.class,
                PublisherSubscriberV2Bean.class,
                PublisherSubscriberV1Bean.class,
                InternalChannelsBean.class,
                InnerProcessorBean.class,
                MultipleProcessorBean.class,
                MultipleTypeProcessorChainV1Bean.class,
                MultipleTypeProcessorChainV2Bean.class,
                ByRequestProcessorV5Bean.class,
                ByRequestProcessorV4Bean.class,
                ByRequestProcessorV3Bean.class,
                ByRequestProcessorV2Bean.class,
                ByRequestProcessorV1Bean.class,
                PublisherProcessorV4Bean.class,
                PublisherProcessorV3Bean.class,
                PublisherProcessorV2Bean.class,
                PublisherProcessorV1Bean.class,
                //Ack tests
//                ChainWithPayloadAckBean.class,
//                ManualAckBean.class,

                //Negative tests
                NotConnectedIncommingChannelBean.class,
                NotConnectedOutgoingChannelBean.class,
                BadSignaturePublisherPayloadBean.class
        ).map(CdiTestCase::from);
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
                CompletableTestBean completableTestBean = CDI.current().select(c).get();
                try {
                    completableTestBean.getTestCompletion().toCompletableFuture().get(1, TimeUnit.SECONDS);
                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    fail(e);
                }
            });
        }
    }
}
