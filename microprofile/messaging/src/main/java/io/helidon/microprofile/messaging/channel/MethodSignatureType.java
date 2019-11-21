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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;

/**
 * Supported method signatures as described in the MicroProfile Reactive Messaging Specification.
 */
public enum MethodSignatureType {
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>Processor&lt;Message&lt;I>, Message&lt;O>> method();</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING, MANUAL
     */
    PROCESSOR_PROCESSOR_MSG_2_VOID(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.MANUAL
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>Processor&lt;I, O> method();</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING
     */
    PROCESSOR_PROCESSOR_PAYL_2_VOID(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: Assembly time -
     * <pre>ProcessorBuilder&lt;Message&lt;I>, Message&lt;O>> method();</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING, MANUAL
     */
    PROCESSOR_PROCESSOR_BUILDER_MSG_2_VOID(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.MANUAL
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: Assembly time -
     * <pre>ProcessorBuilder&lt;I, O> method();</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING
     */
    PROCESSOR_PROCESSOR_BUILDER_PAYL_2_VOID(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>Publisher&lt;Message&lt;O>> method(Message&lt;I> msg);</pre>
     * <pre>Publisher&lt;O> method(I payload);</pre>
     * <pre></pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING
     */
    PROCESSOR_PUBLISHER_2_PUBLISHER(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>PublisherBuilder&lt;O> method(PublisherBuilder&lt;I> pub);</pre>
     * <pre></pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING
     */
    PROCESSOR_PUBLISHER_BUILDER_2_PUBLISHER_BUILDER(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>Publisher&lt;Message&lt;O>> method(Message&lt;I>msg);</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING
     */
    PROCESSOR_PUBLISHER_MSG_2_MSG(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>Publisher&lt;O> method(I payload);</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING
     */
    PROCESSOR_PUBLISHER_PAYL_2_PAYL(true, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>PublisherBuilder&lt;Message&lt;O>> method(Message&lt;I>msg);</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING
     */
    PROCESSOR_PUBLISHER_BUILDER_MSG_2_MSG(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>PublisherBuilder&lt;O> method(&lt;I> msg);</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING
     */
    PROCESSOR_PUBLISHER_BUILDER_PAYL_2_PAYL(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>Message&lt;O> method(Message&lt;I> msg)</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING
     */
    PROCESSOR_MSG_2_MSG(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>O method(I payload)</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING
     */
    PROCESSOR_PAYL_2_PAYL(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>CompletionStage&lt;Message&lt;O>> method(Message&lt;I> msg)</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING
     */
    PROCESSOR_COMPL_STAGE_MSG_2_MSG(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),
    /**
     * Processor method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>CompletionStage&lt;O> method(I payload)</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING
     */
    PROCESSOR_COMPL_STAGE_PAYL_2_PAYL(false, Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING
    ),


    /**
     * Subscriber method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>Subscriber&lt;Message&lt;I>> method()</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING, POST_PROCESSING
     */
    INCOMING_SUBSCRIBER_MSG_2_VOID(true, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),

    /**
     * Subscriber method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>Subscriber&lt;I> method()</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING, POST_PROCESSING
     */
    INCOMING_SUBSCRIBER_PAYL_2_VOID(true, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),
    /**
     * Subscriber method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>SubscriberBuilder&lt;Message&lt;I>> method()</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING, POST_PROCESSING
     */
    INCOMING_SUBSCRIBER_BUILDER_MSG_2_VOID(true, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),
    /**
     * Subscriber method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>SubscriberBuilder&lt;I> method()</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING, POST_PROCESSING
     */
    INCOMING_SUBSCRIBER_BUILDER_PAYL_2_VOID(true, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),
    /**
     * Subscriber method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>void method(I payload)</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING, POST_PROCESSING
     */
    INCOMING_VOID_2_PAYL(false, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),
    /**
     * Subscriber method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>CompletionStage&lt;?> method(Message&lt;I>msg)</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, MANUAL, PRE_PROCESSING, POST_PROCESSING
     */
    INCOMING_COMPLETION_STAGE_2_MSG(false, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.MANUAL,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),
    /**
     * Subscriber method signature type.
     * <p>
     * Invoke at: every incoming
     * <pre>CompletionStage&lt;?> method(I payload)</pre>
     * <li/>Default acknowledgment strategy: PRE_PROCESSING
     * <li/>Supported acknowledgment strategies: NONE, PRE_PROCESSING, POST_PROCESSING
     */
    INCOMING_COMPLETION_STAGE_2_PAYL(false, Acknowledgment.Strategy.POST_PROCESSING,
            Acknowledgment.Strategy.NONE,
            Acknowledgment.Strategy.PRE_PROCESSING,
            Acknowledgment.Strategy.POST_PROCESSING
    ),

    /**
     * Publisher method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>Publisher&lt;Message&lt;U>> method()</pre>
     * <pre>Publisher&lt;U> method()</pre>
     */
    OUTGOING_PUBLISHER_2_VOID(true, null),

    /**
     * Publisher method signature type.
     * <p>
     * Invoke at: assembly time
     * <pre>PublisherBuilder&lt;Message&lt;U>> method()</pre>
     * <pre>PublisherBuilder&lt;U> method()</pre>
     */
    OUTGOING_PUBLISHER_BUILDER_2_VOID(true, null),

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
    OUTGOING_MSG_2_VOID(false, null),

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
    OUTGOING_COMPLETION_STAGE_2_VOID(false, null);

    private boolean invokeAtAssembly;
    private Acknowledgment.Strategy defaultAckType;
    private Set<Acknowledgment.Strategy> supportedAckStrategies;

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
    public boolean isInvokeAtAssembly() {
        return invokeAtAssembly;
    }

    /**
     * Return set of supported acknowledgment strategies.
     *
     * @return Set of {@link org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy}
     */
    public Set<Acknowledgment.Strategy> getSupportedAckStrategies() {
        return supportedAckStrategies;
    }

    /**
     * Default {@link org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy}
     * if nothing was set by {@link org.eclipse.microprofile.reactive.messaging.Acknowledgment}.
     *
     * @return Default {@link org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy}
     */
    public Acknowledgment.Strategy getDefaultAckType() {
        return defaultAckType;
    }
}
