/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.lang.reflect.Method;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;

/**
 * Messaging method metadata.
 */
public interface MessagingMethod {

    /**
     * Method name.
     *
     * @return the name
     */
    String getName();

    /**
     * Java method reference.
     *
     * @return the method
     */
    Method getMethod();

    /**
     * Incoming channel name, eg. channel specified with
     * {@link org.eclipse.microprofile.reactive.messaging.Incoming @Incoming} annotation.
     *
     * @return name of the incoming channel
     */
    String getIncomingChannelName();

    /**
     * Outgoing channel name, eg. channel specified with
     * {@link org.eclipse.microprofile.reactive.messaging.Outgoing @Outgoin} annotation.
     *
     * @return name of the outgoing channel
     */
    String getOutgoingChannelName();

    /**
     * Resolved messaging method signature type.
     *
     * @return signature type
     */
    MethodSignatureType getType();

    /**
     * Resolved acknowledgement strategy, eg. resolved from
     * {@link org.eclipse.microprofile.reactive.messaging.Acknowledgment @Acknowledgment} annotation
     * or default for given signature type.
     *
     * @return acknowledgement strategy
     */
    Acknowledgment.Strategy getAckStrategy();

    /**
     * Reference to the bean instance enclosing messaging method.
     *
     * @return bean instance reference
     */
    Object getBeanInstance();
}
