/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;

/**
 * An qualifier annotation to specify that an in-process {@link io.grpc.Channel}
 * should be injected.
 * <p>
 * For example:
 * <pre>
 *     &#064;javax.inject.Inject
 *     &#064;io.helidon.microprofile.grpc.core.InProcessChannel
 *     private io.grpc.Channel channel;
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface InProcessGrpcChannel {

    /**
     * An {@link AnnotationLiteral} for the {@link InProcessGrpcChannel} annotation.
     */
    class Literal
            extends AnnotationLiteral<InProcessGrpcChannel>
            implements InProcessGrpcChannel {

        /**
         * The singleton instance of {@link Literal}.
         */
        public static final Literal INSTANCE = new Literal();

        private static final long serialVersionUID = 1L;
    }
}
