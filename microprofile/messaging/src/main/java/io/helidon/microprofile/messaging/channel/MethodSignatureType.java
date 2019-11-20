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

package io.helidon.microprofile.messaging.channel;

/**
 * Supported method signatures as described in the MicroProfile Reactive Messaging Specification.
 */
public enum MethodSignatureType {
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>Processor&lt;Message&lt;I>, Message&lt;O>> method();</pre>
     * <pre>Processor&lt;I, O> method();</pre>
     */
    PROCESSOR_VOID_2_PROCESSOR(true),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: Assembly time -
     * <pre>ProcessorBuilder&lt;Message&lt;I>, Message&lt;O>> method();</pre>
     * <pre></pre>
     */
    PROCESSOR_VOID_2_PROCESSOR_BUILDER(true),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>Publisher&lt;Message&lt;O>> method(Message&lt;I> msg);</pre>
     * <pre>Publisher&lt;O> method(I payload);</pre>
     */
    PROCESSOR_PUBLISHER_2_PUBLISHER(true),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>PublisherBuilder&lt;O> method(PublisherBuilder&lt;I> pub);</pre>
     */
    PROCESSOR_PUBLISHER_BUILDER_2_PUBLISHER_BUILDER(true),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>Publisher&lt;Message&lt;O>> method(Message&lt;I>msg);</pre>
     * <pre>Publisher&lt;O> method(I payload);</pre>
     */
    PROCESSOR_MSG_2_PUBLISHER(false),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>Message&lt;O> method(Message&lt;I> msg)</pre>
     * <pre>O method(I payload)</pre>
     */
    PROCESSOR_MSG_2_MSG(false),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>CompletionStage&lt;Message&lt;O>> method(Message&lt;I> msg)</pre>
     * <pre>CompletionStage&lt;O> method(I payload)</pre>
     */
    PROCESSOR_MSG_2_COMPL_STAGE(false),


    /**
     * Subscriber method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>Subscriber&lt;Message&lt;I>> method()</pre>
     * <pre>Subscriber&lt;I> method()</pre>
     */
    INCOMING_VOID_2_SUBSCRIBER(true),
    /**
     * Subscriber method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>SubscriberBuilder&lt;Message&lt;I>> method()</pre>
     * <pre>SubscriberBuilder&lt;I> method()</pre>
     */
    INCOMING_VOID_2_SUBSCRIBER_BUILDER(true),
    /**
     * Subscriber method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>void method(I payload)</pre>
     */
    INCOMING_MSG_2_VOID(false),
    /**
     * Subscriber method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>CompletionStage&lt;?> method(Message&lt;I>msg)</pre>
     * <pre>CompletionStage&lt;?> method(I payload)</pre>
     */
    INCOMING_MSG_2_COMPLETION_STAGE(false),

    /**
     * Publisher method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>Publisher&lt;Message&lt;U>> method()</pre>
     * <pre>Publisher&lt;U> method()</pre>
     */
    OUTGOING_VOID_2_PUBLISHER(true),

    /**
     * Publisher method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>PublisherBuilder&lt;Message&lt;U>> method()</pre>
     * <pre>PublisherBuilder&lt;U> method()</pre>
     */
    OUTGOING_VOID_2_PUBLISHER_BUILDER(true),

    /**
     * Publisher method signature type.
     * <p>
     * Invoke at: Each request made by subscriber
     * <pre>Message&lt;U> method()</pre>
     * <pre>U method()</pre>
     * <p>
     * Produces an infinite stream of Message associated with the
     * channel channel. The result is a CompletionStage. The method should not be
     * called by the reactive messaging implementation until the CompletionStage
     * returned previously is completed.
     */
    OUTGOING_VOID_2_MSG(false),

    /**
     * Publisher method signature type.
     * <p>
     * Invoke at: Each request made by subscriber
     * <pre>CompletionStage&lt;Message&lt;U>> method()</pre>
     * <pre>CompletionStage&lt;U> method()</pre>
     * <p>
     * Produces an infinite stream of Message associated with the
     * channel channel. The result is a CompletionStage. The method should not be
     * called by the reactive messaging implementation until the CompletionStage
     * returned previously is completed.
     */
    OUTGOING_VOID_2_COMPLETION_STAGE(false);

    private boolean invokeAtAssembly;

    MethodSignatureType(boolean invokeAtAssembly) {
        this.invokeAtAssembly = invokeAtAssembly;
    }

    /**
     * Method signatures which should be invoked at assembly(those registering publishers/processors/subscribers) are marked with true,
     * to distinguish them from those which should be invoked for every item in the stream.
     *
     * @return {@code true} if should be invoked at assembly
     */
    public boolean isInvokeAtAssembly() {
        return invokeAtAssembly;
    }
}
