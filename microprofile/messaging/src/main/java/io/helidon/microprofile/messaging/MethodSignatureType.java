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

package io.helidon.microprofile.messaging;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;

/**
 * Supported method signatures as described in the MicroProfile Reactive Messaging Specification.
 */
enum MethodSignatureType {
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: assembly time
     * <br>
     * <pre>Processor&lt;Message&lt;I&gt;, Message&lt;O&gt;&gt; method();</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING, MANUAL</li>
     * </ul>
     */
    PROCESSOR_PROCESSOR_MSG_2_VOID(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.MANUAL
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: assembly time
     * <br>
     * <pre>Processor&lt;I, O&gt; method();</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_PROCESSOR_PAYL_2_VOID(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: Assembly time -
     * <pre>ProcessorBuilder&lt;Message&lt;I&gt;, Message&lt;O&gt;&gt; method();</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING, MANUAL</li>
     * </ul>
     */
    PROCESSOR_PROCESSOR_BUILDER_MSG_2_VOID(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.MANUAL
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: Assembly time -
     * <pre>ProcessorBuilder&lt;I, O&gt; method();</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_PROCESSOR_BUILDER_PAYL_2_VOID(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: assembly time
     * <pre>Publisher&lt;Message&lt;O&gt;&gt; method(Publisher&lt;Message&lt;I&gt;&gt; pub);</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_PUBLISHER_MSG_2_PUBLISHER_MSG(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: assembly time
     * <pre>Publisher&lt;O&gt; method(Publisher&lt;I&gt; pub);</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_PUBLISHER_PAYL_2_PUBLISHER_PAYL(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: assembly time
     * <pre>PublisherBuilder&lt;Message&lt;O&gt;&gt; method(PublisherBuilder&lt;Message&lt;I&gt;&gt; pub);</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_PUBLISHER_BUILDER_MSG_2_PUBLISHER_BUILDER_MSG(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: assembly time
     * <pre>PublisherBuilder&lt;O&gt; method(PublisherBuilder&lt;I&gt; pub);</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_PUBLISHER_BUILDER_PAYL_2_PUBLISHER_BUILDER_PAYL(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: every incoming
     * <pre>Publisher&lt;Message&lt;O&gt;&gt; method(Message&lt;I&gt; msg);</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_PUBLISHER_MSG_2_MSG(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: every incoming
     * <pre>Publisher&lt;O&gt; method(I payload);</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_PUBLISHER_PAYL_2_PAYL(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: every incoming
     * <pre>PublisherBuilder&lt;Message&lt;O&gt;&gt; method(Message&lt;I&gt; msg);</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_PUBLISHER_BUILDER_MSG_2_MSG(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: every incoming
     * <pre>PublisherBuilder&lt;O&gt; method(I payload);</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_PUBLISHER_BUILDER_PAYL_2_PAYL(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: every incoming
     * <pre>Message&lt;O&gt; method(Message&lt;I&gt; msg)</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_MSG_2_MSG(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: every incoming
     * <pre>O method(I payload)</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: POST_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING, POST_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_PAYL_2_PAYL(false, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: every incoming
     * <pre>CompletionStage&lt;Message&lt;O&gt;&gt; method(Message&lt;I&gt; msg)</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_COMPL_STAGE_MSG_2_MSG(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <br>
     * Invoke at: every incoming
     * <pre>CompletionStage&lt;O&gt; method(I payload)</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING</li>
     * </ul>
     */
    PROCESSOR_COMPL_STAGE_PAYL_2_PAYL(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),


    /**
     * Subscriber method signature type.
     * <br>
     * Invoke at: assembly time
     * <pre>Subscriber&lt;Message&lt;I&gt;&gt; method()</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING, POST_PROCESSING</li>
     * </ul>
     */
    INCOMING_SUBSCRIBER_MSG_2_VOID(true, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),

    /**
     * Subscriber method signature type.
     * <br>
     * Invoke at: assembly time
     * <pre>Subscriber&lt;I&gt; method()</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING, POST_PROCESSING</li>
     * </ul>
     */
    INCOMING_SUBSCRIBER_PAYL_2_VOID(true, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),
    /**
     * Subscriber method signature type.
     * <br>
     * Invoke at: assembly time
     * <pre>SubscriberBuilder&lt;Message&lt;I&gt;&gt; method()</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING, POST_PROCESSING</li>
     * </ul>
     */
    INCOMING_SUBSCRIBER_BUILDER_MSG_2_VOID(true, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),
    /**
     * Subscriber method signature type.
     * <br>
     * Invoke at: assembly time
     * <pre>SubscriberBuilder&lt;I&gt; method()</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING, POST_PROCESSING</li>
     * </ul>
     */
    INCOMING_SUBSCRIBER_BUILDER_PAYL_2_VOID(true, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),
    /**
     * Subscriber method signature type.
     * <br>
     * Invoke at: every incoming
     * <pre>void method(I payload)</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING, POST_PROCESSING</li>
     * </ul>
     */
    INCOMING_VOID_2_PAYL(false, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),
    /**
     * Subscriber method signature type.
     * <br>
     * Invoke at: every incoming
     * <pre>CompletionStage&lt;?&gt; method(Message&lt;I&gt; msg)</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING, POST_PROCESSING</li>
     * </ul>
     */
    INCOMING_COMPLETION_STAGE_2_MSG(false, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),
    /**
     * Subscriber method signature type.
     * <br>
     * Invoke at: every incoming
     * <pre>CompletionStage&lt;?&gt; method(I payload)</pre>
     * <ul>
     *  <li>Default acknowledgment strategy: PRE_PROCESSING</li>
     *  <li>Supported acknowledgment strategies: NONE, PRE_PROCESSING, POST_PROCESSING</li>
     * </ul>
     */
    INCOMING_COMPLETION_STAGE_2_PAYL(false, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),

    /**
     * Publisher method signature type.
     * <br>
     * Invoke at: assembly time
     * <pre>Publisher&lt;Message&lt;U&lt;&lt; method()</pre>
     */
    OUTGOING_PUBLISHER_MSG_2_VOID(true, null),

    /**
     * Publisher method signature type.
     * <br>
     * Invoke at: assembly time
     * <pre>Publisher&lt;U&lt; method()</pre>
     */
    OUTGOING_PUBLISHER_PAYL_2_VOID(true, null),

    /**
     * Publisher method signature type.
     * <br>
     * Invoke at: assembly time
     * <pre>PublisherBuilder&lt;Message&lt;U&lt;&lt; method()</pre>
     */
    OUTGOING_PUBLISHER_BUILDER_MSG_2_VOID(true, null),

    /**
     * Publisher method signature type.
     * <br>
     * Invoke at: assembly time
     * <pre>PublisherBuilder&lt;U&lt; method()</pre>
     */
    OUTGOING_PUBLISHER_BUILDER_PAYL_2_VOID(true, null),

    /**
     * Publisher method signature type.
     * <br>
     * Invoke at: Each request made by subscriber
     * <pre>Message&lt;U&lt; method()</pre>
     * <br>
     * Produces an infinite stream of Message associated with the
     * channel channel. The result is a CompletionStage. The method should not be
     * called by the reactive messaging implementation until the CompletionStage
     * returned previously is completed.
     */
    OUTGOING_MSG_2_VOID(false, null),

    /**
     * Publisher method signature type.
     * <br>
     * Invoke at: Each request made by subscriber
     * <pre>U method()</pre>
     * <br>
     * Produces an infinite stream of Message associated with the
     * channel channel. The result is a CompletionStage. The method should not be
     * called by the reactive messaging implementation until the CompletionStage
     * returned previously is completed.
     */
    OUTGOING_PAYL_2_VOID(false, null),

    /**
     * Publisher method signature type.
     * <br>
     * Invoke at: Each request made by subscriber
     * <pre>CompletionStage&lt;Message&lt;U&lt;&lt; method()</pre>
     * <br>
     * Produces an infinite stream of Message associated with the
     * channel channel. The result is a CompletionStage. The method should not be
     * called by the reactive messaging implementation until the CompletionStage
     * returned previously is completed.
     */
    OUTGOING_COMPLETION_STAGE_MSG_2_VOID(false, null),

    /**
     * Publisher method signature type.
     * <br>
     * Invoke at: Each request made by subscriber
     * <pre>CompletionStage&lt;U&lt; method()</pre>
     * <br>
     * Produces an infinite stream of Message associated with the
     * channel channel. The result is a CompletionStage. The method should not be
     * called by the reactive messaging implementation until the CompletionStage
     * returned previously is completed.
     */
    OUTGOING_COMPLETION_STAGE_PAYL_2_VOID(false, null);

    private final boolean invokeAtAssembly;
    private final Acknowledgment.Strategy defaultAckType;
    private final Set<Acknowledgment.Strategy> supportedAckStrategies;

    MethodSignatureType(boolean invokeAtAssembly,
                        Acknowledgment.Strategy defaultAckType,
                        Acknowledgment.Strategy... supportedAckTypes) {
        this.invokeAtAssembly = invokeAtAssembly;
        this.defaultAckType = defaultAckType;
        this.supportedAckStrategies = new HashSet<>(Arrays.asList(supportedAckTypes));
    }

    /**
     * Method signatures which should be invoked at assembly(those registering publishers/processors/subscribers) are marked with true,
     * to distinguish them from those which should be invoked for every item in the stream.
     *
     * @return {@code true} if should be invoked at assembly
     */
    boolean isInvokeAtAssembly() {
        return invokeAtAssembly;
    }

    /**
     * Return set of supported acknowledgment strategies.
     *
     * @return Set of {@link org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy}
     */
    Set<Acknowledgment.Strategy> getSupportedAckStrategies() {
        return supportedAckStrategies;
    }

    /**
     * Default {@link org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy}
     * if nothing was set by {@link org.eclipse.microprofile.reactive.messaging.Acknowledgment}.
     *
     * @return Default {@link org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy}
     */
    Acknowledgment.Strategy getDefaultAckType() {
        return defaultAckType;
    }
}
