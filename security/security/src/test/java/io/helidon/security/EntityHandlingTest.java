/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;

import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.security.providers.ProviderForTesting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test of handling message entities.
 */
public class EntityHandlingTest {
    private static final String REQUEST_BYTES = "request bytes";

    private static Security security;
    private static int counter = 1;
    private SecurityContext context;
    private String reactiveTestResult;
    private Throwable reactiveTestException;

    @BeforeAll
    public static void classInit() {
        ProviderForTesting firstProvider = new ProviderForTesting("DENY");
        security = Security.builder()
                .addProvider(firstProvider, "FirstInstance")
                .authenticationProvider(firstProvider)
                .build();
    }

    @BeforeEach
    public void instanceInit() {
        this.context = security.contextBuilder(String.valueOf(counter++)).build();
    }

    @Test
    public void reactiveTest() throws Throwable {
        CountDownLatch cdl = new CountDownLatch(1);

        context.env(context.env()
                               .derive()
                               .method("POST")
                               .path("/post"));

        SubmissionPublisher<ByteBuffer> publisher = new SubmissionPublisher<>(Runnable::run, 10);
        SecurityRequest request = context.securityRequestBuilder()
                .requestMessage(message(publisher))
                .buildRequest();

        AuthenticationClientImpl authClient = (AuthenticationClientImpl) context.atnClientBuilder().build();

        Optional<Entity> requestMessage = request.requestEntity();

        requestMessage.ifPresentOrElse(message -> message.filter(byteBufferPublisher -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            SubmissionPublisher<ByteBuffer> bPublisher = new SubmissionPublisher<>();

            byteBufferPublisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(2);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    try {
                        baos.write(item.array());
                        bPublisher.submit(item);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    reactiveTestException = throwable;
                }

                @Override
                public void onComplete() {
                    reactiveTestResult = new String(baos.toByteArray());
                    cdl.countDown();
                }
            });
            return bPublisher;
        }), () -> fail("Request message should have been present"));

        request.responseEntity().ifPresent(message -> fail("Response message should not be present"));

        publisher.submit(ByteBuffer.wrap(REQUEST_BYTES.getBytes()));
        publisher.close();

        cdl.await();

        if (null != reactiveTestException) {
            throw reactiveTestException;
        }

        assertThat(reactiveTestResult, is(REQUEST_BYTES));
    }

    private Entity message(SubmissionPublisher<ByteBuffer> publisher) {
        return filterFunction -> filterFunction.apply(publisher);
    }
}
