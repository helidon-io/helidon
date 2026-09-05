/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.messaging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.common.Api;
import io.helidon.messaging.spi.ConnectorDeliveryReservation;
import io.helidon.service.registry.Service;

/**
 * Declarative messaging annotations.
 */
@Api.Preview
public final class Messaging {
    private Messaging() {
    }

    /**
     * Marks a method that receives messages from a channel.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    @Service.EntryPoint
    public @interface ReceiveFrom {
        /**
         * Source channel name.
         *
         * @return source channel name
         */
        String value();
    }

    /**
     * Target channel for the value returned by a one-to-one message processor method.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    public @interface SendTo {
        /**
         * Target channel name.
         *
         * @return target channel name
         */
        String value();
    }

    /**
     * Declares default delivery failure handling for the incoming channel named by {@link ReceiveFrom}.
     * The policy is channel- and delivery-scoped, so it covers sibling handlers and downstream outputs participating
     * in the same source delivery rather than only the annotated method invocation. An explicitly configured
     * {@code failure} key for the incoming channel overrides the corresponding annotation member.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    public @interface OnFailure {
        /**
         * Delay before retrying a failed delivery, in {@link java.time.Duration} format. This populates
         * {@link RetryConfig#delay()}.
         *
         * @return retry delay
         */
        String retryDelay() default "PT1S";

        /**
         * Maximum total delivery attempts, including the initial attempt. Zero means unlimited attempts and is only
         * valid with {@link FailureDisposition#FAIL}. This populates {@link RetryConfig#maxAttempts()}. An unlimited
         * pre-dispatch mapping failure reported through
         * {@link ConnectorDeliveryReservation#startFailed(MessageBatch, RuntimeException)} is treated as exhausted
         * after its initial attempt because the runtime cannot repeat transport mapping.
         *
         * @return maximum delivery attempts, or zero for unlimited attempts
         */
        int maxAttempts() default 0;

        /**
         * Terminal disposition after delivery attempts are exhausted.
         *
         * @return terminal disposition
         */
        FailureDisposition onExhausted() default FailureDisposition.FAIL;

        /**
         * Logical channel used for dead-letter delivery. This is required for
         * {@link FailureDisposition#DEAD_LETTER} and is not valid for other dispositions. A non-empty value populates
         * {@link DeadLetterConfig#channel()}.
         *
         * @return dead-letter channel, or an empty string when none is declared
         */
        String deadLetterChannel() default "";
    }

    /**
     * Message header parameter.
     * <p>
     * Header names are exact and case-sensitive. Supported parameter types are:
     * <ul>
     *     <li>{@code String}: required last value, which must be a {@link MessageHeaderValue.TextValue}</li>
     *     <li>{@code Optional<String>}: optional last value, which must be a {@code TextValue} when present</li>
     *     <li>{@link MessageHeaderValue}: required last value of any kind</li>
     *     <li>{@code Optional<MessageHeaderValue>}: optional last value of any kind</li>
     *     <li>{@code List<MessageHeaderValue>}: immutable list of all matching values in message-entry order, empty when
     *     absent</li>
     * </ul>
     * A missing required value or a non-text value selected for a text parameter fails delivery. An explicit
     * {@link MessageHeaderValue.NullValue} is a present header value, including in {@code Optional<MessageHeaderValue>}. No automatic
     * header-value conversion is performed. Local metadata is not visible through this annotation. A handler may
     * declare at most one header parameter for each exact name.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.PARAMETER)
    public @interface HeaderParam {
        /**
         * Header name.
         *
         * @return header name
         */
        String value();
    }

    /**
     * Message payload parameter.
     * <p>
     * This explicit marker selects the payload view even when the parameter type implements {@link Message}. Header
     * parameters on the same method are read from the outer delivery message.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.PARAMETER)
    public @interface Entity {
    }

}
