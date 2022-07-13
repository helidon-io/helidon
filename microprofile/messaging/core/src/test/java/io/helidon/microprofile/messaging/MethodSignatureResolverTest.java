/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.reactive.Multi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

class MethodSignatureResolverTest {

    @Incoming("in-channel-name")
    @ExpectedSignatureType(MethodSignatureType.INCOMING_COMPLETION_STAGE_2_MSG)
    CompletionStage<Void> incoming_completion_stage_2_msg(Message<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @ExpectedSignatureType(MethodSignatureType.INCOMING_COMPLETION_STAGE_2_PAYL)
    CompletionStage<Void> incoming_completion_stage_2_payl(String payload) {
        return null;
    }

    @Incoming("in-channel-name")
    @ExpectedSignatureType(MethodSignatureType.INCOMING_SUBSCRIBER_BUILDER_MSG_2_VOID)
    SubscriberBuilder<Message<String>, Void> incoming_subscriber_builder_msg_2_void() {
        return null;
    }

    @Incoming("in-channel-name")
    @ExpectedSignatureType(MethodSignatureType.INCOMING_SUBSCRIBER_BUILDER_MSG_2_VOID)
    SubscriberBuilder<ExtendedMessage<String>, Void> incoming_subscriber_builder_msg_2_void1() {
        return null;
    }

    @Incoming("in-channel-name")
    @ExpectedSignatureType(MethodSignatureType.INCOMING_SUBSCRIBER_BUILDER_PAYL_2_VOID)
    SubscriberBuilder<String, Void> incoming_subscriber_builder_payl_2_void() {
        return null;
    }

    @Incoming("in-channel-name")
    @ExpectedSignatureType(MethodSignatureType.INCOMING_SUBSCRIBER_MSG_2_VOID)
    Subscriber<Message<String>> incoming_subscriber_msg_2_void() {
        return null;
    }

    @Incoming("in-channel-name")
    @ExpectedSignatureType(MethodSignatureType.INCOMING_SUBSCRIBER_MSG_2_VOID)
    Subscriber<ExtendedMessage<String>> incoming_subscriber_msg_2_void1() {
        return null;
    }

    @Incoming("in-channel-name")
    @ExpectedSignatureType(MethodSignatureType.INCOMING_SUBSCRIBER_PAYL_2_VOID)
    Subscriber<String> incoming_subscriber_payl_2_void() {
        return null;
    }

    @Incoming("in-channel-name")
    @ExpectedSignatureType(MethodSignatureType.INCOMING_VOID_2_PAYL)
    void incoming_void_2_payl(String payload) {
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_PUBLISHER_BUILDER_MSG_2_VOID)
    PublisherBuilder<Message<String>> outgoing_publisher_builder_msg_2_void() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_PUBLISHER_BUILDER_MSG_2_VOID)
    PublisherBuilder<ExtendedMessage<String>> outgoing_publisher_builder_msg_2_void1() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_PUBLISHER_BUILDER_PAYL_2_VOID)
    PublisherBuilder<String> outgoing_publisher_builder_payl_2_void() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_PUBLISHER_MSG_2_VOID)
    Publisher<Message<String>> outgoing_publisher_msg_2_void() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_PUBLISHER_MSG_2_VOID)
    Publisher<ExtendedMessage<String>> outgoing_publisher_msg_2_void1() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_FLOW_PUBLISHER_MSG_2_VOID)
    Multi<Message<String>> outgoing_multi_msg_1_void1() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_FLOW_PUBLISHER_PAYL_2_VOID)
    Multi<String> outgoing_multi_payload_1_void1() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_FLOW_PUBLISHER_PAYL_2_VOID)
    Flow.Publisher<String> outgoing_multi_payload_2_void1() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_FLOW_PUBLISHER_MSG_2_VOID)
    Multi<ExtendedMessage<String>> outgoing_multi_msg_2_void1() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_FLOW_PUBLISHER_MSG_2_VOID)
    Flow.Publisher<Message<String>> outgoing_flow_msg_1_void1() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_FLOW_PUBLISHER_MSG_2_VOID)
    Flow.Publisher<ExtendedMessage<String>> outgoing_flow_msg_2_void1() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_PUBLISHER_PAYL_2_VOID)
    Publisher<String> outgoing_publisher_payl_2_void() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_COMPLETION_STAGE_MSG_2_VOID)
    CompletionStage<Message<String>> outgoing_completion_stage_msg_2_void() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_COMPLETION_STAGE_MSG_2_VOID)
    CompletionStage<ExtendedMessage<String>> outgoing_completion_stage_msg_2_void1() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_COMPLETION_STAGE_PAYL_2_VOID)
    CompletionStage<String> outgoing_completion_stage_payl_2_void() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_MSG_2_VOID)
    Message<String> outgoing_msg_2_void() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_MSG_2_VOID)
    ExtendedMessage<String> outgoing_msg_2_void1() {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_PAYL_2_VOID)
    String outgoing_payl_2_void() {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PROCESSOR_BUILDER_MSG_2_VOID)
    ProcessorBuilder<Message<String>, Message<String>> processor_processor_builder_msg_2_void() {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PROCESSOR_BUILDER_MSG_2_VOID)
    ProcessorBuilder<ExtendedMessage<String>, ExtendedMessage<String>> processor_processor_builder_msg_2_void1() {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PROCESSOR_BUILDER_MSG_2_VOID)
    ProcessorBuilder<ExtendedMessage<String>, Message<String>> processor_processor_builder_msg_2_void2() {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PROCESSOR_BUILDER_MSG_2_VOID)
    ProcessorBuilder<Message<String>, ExtendedMessage<String>> processor_processor_builder_msg_2_void3() {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PROCESSOR_BUILDER_PAYL_2_VOID)
    ProcessorBuilder<String, Integer> processor_processor_builder_payl_2_void() {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PROCESSOR_MSG_2_VOID)
    Processor<Message<String>, Message<Integer>> processor_processor_msg_2_void() {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PROCESSOR_MSG_2_VOID)
    Processor<ExtendedMessage<String>, ExtendedMessage<Integer>> processor_processor_msg_2_void1() {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PROCESSOR_MSG_2_VOID)
    Processor<ExtendedMessage<String>, Message<Integer>> processor_processor_msg_2_void2() {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PROCESSOR_MSG_2_VOID)
    Processor<Message<String>, ExtendedMessage<Integer>> processor_processor_msg_2_void3() {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PROCESSOR_PAYL_2_VOID)
    Processor<String, Integer> processor_processor_payl_2_void() {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_MSG_2_PUBLISHER_BUILDER_MSG)
    PublisherBuilder<Message<String>> processor_publisher_builder_msg_2_publisher_builder_msg(PublisherBuilder<Message<String>> pub) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_MSG_2_PUBLISHER_BUILDER_MSG)
    PublisherBuilder<ExtendedMessage<String>> processor_publisher_builder_msg_2_publisher_builder_msg1(PublisherBuilder<ExtendedMessage<String>> pub) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_MSG_2_PUBLISHER_BUILDER_MSG)
    PublisherBuilder<ExtendedMessage<String>> processor_publisher_builder_msg_2_publisher_builder_msg2(PublisherBuilder<Message<String>> pub) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_MSG_2_PUBLISHER_BUILDER_MSG)
    PublisherBuilder<Message<String>> processor_publisher_builder_msg_2_publisher_builder_msg3(PublisherBuilder<ExtendedMessage<String>> pub) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_PAYL_2_PUBLISHER_BUILDER_PAYL)
    PublisherBuilder<String> processor_publisher_builder_payl_2_publisher_builder_payl(PublisherBuilder<String> pub) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_MSG_2_MSG)
    PublisherBuilder<Message<String>> processor_publisher_builder_msg_2_msg(Message<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_MSG_2_MSG)
    PublisherBuilder<ExtendedMessage<String>> processor_publisher_builder_msg_2_msg2(ExtendedMessage<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_MSG_2_MSG)
    PublisherBuilder<ExtendedMessage<String>> processor_publisher_builder_msg_2_msg3(Message<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_MSG_2_MSG)
    PublisherBuilder<Message<String>> processor_publisher_builder_msg_2_msg4(ExtendedMessage<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_PAYL_2_PAYL)
    PublisherBuilder<String> processor_publisher_builder_payl_2_payl(String payload) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_PUBLISHER_MSG)
    Publisher<Message<String>> processor_publisher_msg_2_publisher_msg(Publisher<Message<String>> pub) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_PUBLISHER_MSG)
    Publisher<ExtendedMessage<String>> processor_publisher_msg_2_publisher_msg1(Publisher<ExtendedMessage<String>> pub) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_PUBLISHER_MSG)
    Publisher<Message<String>> processor_publisher_msg_2_publisher_msg2(Publisher<ExtendedMessage<String>> pub) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_PUBLISHER_MSG)
    Publisher<ExtendedMessage<String>> processor_publisher_msg_2_publisher_msg3(Publisher<Message<String>> pub) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_PAYL_2_PUBLISHER_PAYL)
    Publisher<String> processor_publisher_payl_2_publisher_payl(Publisher<String> pub) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_MSG)
    Publisher<Message<String>> processor_publisher_msg_2_msgxxx(Message<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_FLOW_PUBLISHER_MSG_2_MSG)
    Flow.Publisher<Message<String>> processor_flow_publisher_msg_2_msgxxx(Message<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_FLOW_PUBLISHER_MSG_2_MSG)
    Multi<Message<String>> processor_multi_publisher_msg_2_msgxxx(Message<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_MSG)
    Publisher<ExtendedMessage<String>> processor_publisher_msg_2_msg(ExtendedMessage<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_MSG)
    Publisher<Message<String>> processor_publisher_msg_2_msg2(ExtendedMessage<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_MSG)
    Publisher<ExtendedMessage<String>> processor_publisher_msg_2_msg3(Message<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PUBLISHER_PAYL_2_PAYL)
    Publisher<String> processor_publisher_payl_2_payl(String payload) {
        return null;
    }


    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_FLOW_PUBLISHER_PAYL_2_PAYL)
    Flow.Publisher<String> processor_flow_publisher_payl_2_payl(String payload) {
        return null;
    }


    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_FLOW_PUBLISHER_PAYL_2_PAYL)
    Multi<String> processor_multi_publisher_payl_2_payl(String payload) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_COMPL_STAGE_MSG_2_MSG)
    CompletionStage<Message<String>> processor_compl_stage_msg_2_msg(Message<String> msg) {
        return null;
    }


    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_COMPL_STAGE_MSG_2_MSG)
    CompletionStage<ExtendedMessage<String>> processor_compl_stage_msg_2_msg(ExtendedMessage<String> msg) {
        return null;
    }


    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_COMPL_STAGE_MSG_2_MSG)
    CompletionStage<Message<String>> processor_compl_stage_msg_2_msg1(ExtendedMessage<String> msg) {
        return null;
    }


    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_COMPL_STAGE_MSG_2_MSG)
    CompletionStage<ExtendedMessage<String>> processor_compl_stage_msg_2_msg2(Message<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_COMPL_STAGE_PAYL_2_PAYL)
    CompletionStage<String> processor_compl_stage_payl_2_payl(String payload) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_MSG_2_MSG)
    Message<String> processor_msg_2_msg(Message<String> msg) {
        return null;
    }

    @Incoming("in-channel-name")
    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.PROCESSOR_PAYL_2_PAYL)
    String processor_payl_2_payl(String payload) {
        return null;
    }

    @Outgoing("out-channel-name")
    @ExpectedSignatureType(MethodSignatureType.OUTGOING_PUBLISHER_PAYL_2_VOID)
    public Publisher<String> extendedPublisher() {
        return null;
    }

    private static Stream<MethodTestCase> locateTestMethods() {
        return Arrays.stream(MethodSignatureResolverTest.class.getDeclaredMethods())
                .filter(m -> Objects.nonNull(m.getAnnotation(ExpectedSignatureType.class)))
                .sorted(Comparator.comparing(Method::getName))
                .map(MethodTestCase::new);
    }

    @ParameterizedTest
    @MethodSource("locateTestMethods")
    void signatureResolving(MethodTestCase testCase) {
        Optional<MethodSignatureType> signatureType = MethodSignatureResolver.create(testCase.m).resolve();
        assertThat("Resolved signature type is empty", signatureType.isPresent());
        assertThat(signatureType.get(), is(testCase.expectedType));
    }

    @Test
    void testSignatureResolvingCoverage() {
        Set<MethodSignatureType> testedTypes = locateTestMethods().map(m -> m.expectedType).collect(Collectors.toSet());
        Set<String> unTestedTypes = Arrays.stream(MethodSignatureType.values())
                .filter(o -> !testedTypes.contains(o))
                .map(MethodSignatureType::name)
                .collect(Collectors.toSet());
        if (!unTestedTypes.isEmpty()) {
            fail("No test found for signature types: \n" + String.join("\n", unTestedTypes));
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ExpectedSignatureType {
        MethodSignatureType value();
    }

    private static class MethodTestCase {
        private final MethodSignatureType expectedType;
        private final Method m;

        MethodTestCase(Method m) {
            this.m = m;
            this.expectedType = m.getAnnotation(ExpectedSignatureType.class).value();
        }

        @Override
        public String toString() {
            return expectedType.name();
        }
    }
}