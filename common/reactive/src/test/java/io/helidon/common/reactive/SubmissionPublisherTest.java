/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive;

import java.util.concurrent.ForkJoinPool;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SubmissionPublisherTest {

    // TODO, do a test with requestMax.
    // TODO use latches intead of thread.sleep

    @Test
    public void testMultipleSubscribers() throws InterruptedException{
        SubmissionPublisher<String> publisher = new SubmissionPublisher<>();
        TestSubscriber s1 = new TestSubscriber();
        publisher.subscribe(s1);
        s1.request1();
        TestSubscriber s2 = new TestSubscriber();
        publisher.subscribe(s2);
        s2.request1();
        publisher.submit("hello");
        publisher.close();
        Thread.sleep(1000);
        assertThat(s1.getItems().size(), is(1));
        assertThat(s2.getItems().size(), is(1));
        assertThat(publisher.getNumberOfSubscribers(), is(2));
    }

    @Test
    public void testNoReplayElements() throws InterruptedException{
        SubmissionPublisher<String> publisher = new SubmissionPublisher<>();
        TestSubscriber s1 = new TestSubscriber();
        publisher.subscribe(s1);
        s1.request1();
        publisher.submit("hello");
        Thread.sleep(1000);
        assertThat(s1.getItems().size(), is(1));
        TestSubscriber s2 = new TestSubscriber();
        publisher.subscribe(s2);
        s2.request1();
        Thread.sleep(1000);
        assertThat(s2.getItems().size(), is(0));
        assertThat(publisher.getNumberOfSubscribers(), is(2));
    }

    @Test
    public void testNoReplayElementsWithParallePublisher() throws InterruptedException{
        SubmissionPublisher<String> publisher = new SubmissionPublisher<>(ForkJoinPool.commonPool(), 256);
        TestSubscriber s1 = new TestSubscriber();
        publisher.subscribe(s1);
        s1.request1();
        publisher.submit("hello");
        Thread.sleep(1000);
        assertThat(s1.getItems().size(), is(1));
        TestSubscriber s2 = new TestSubscriber();
        publisher.subscribe(s2);
        s2.request1();
        Thread.sleep(1000);
        assertThat(s2.getItems().size(), is(0));
        assertThat(publisher.getNumberOfSubscribers(), is(2));
    }
}
