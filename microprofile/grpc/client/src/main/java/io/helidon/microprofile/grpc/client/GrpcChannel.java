/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.grpc.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.Nonbinding;
import javax.inject.Qualifier;

/**
 * An qualifier annotation to specify the name of a gRPC channel to inject.
 * <p>
 * For example:
 * <pre>
 *     &#064;javax.inject.Inject
 *     &#064;io.helidon.microprofile.grpc.core.Channel(name="foo")
 *     private io.grpc.Channel channel;
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface GrpcChannel {
    /**
     * Obtain the name of the channel.
     *
     * @return  name of the channel
     */
    @Nonbinding String name();

    /**
     * An {@link javax.enterprise.util.AnnotationLiteral} for the
     * {@link GrpcChannel} annotation.
     */
    class Literal extends AnnotationLiteral<GrpcChannel> implements GrpcChannel {

        @Override
        public String name() {
            return "";
        }

        /**
         * The singleton instance of {@link GrpcChannel.Literal}.
         */
        public static final Literal INSTANCE = new Literal();

        private static final long serialVersionUID = 1L;
    }
}
