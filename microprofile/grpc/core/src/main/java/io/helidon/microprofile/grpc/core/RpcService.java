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

package io.helidon.microprofile.grpc.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;

/**
 * An annotation used to mark a class as representing a gRPC service.
 */
@Qualifier
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RpcService {
    /**
     * Obtain the service name.
     *
     * @return  the service name
     */
    String name() default "";

    /**
     * Obtain the service version.
     *
     * @return  the service version
     */
    int version() default 0;

    /**
     * An {@link AnnotationLiteral} for the {@link RpcService} annotation.
     */
    class Literal
            extends AnnotationLiteral<RpcService> implements RpcService {

        /**
         * The singleton instance of {@link Literal}.
         */
        public static final Literal INSTANCE = new Literal();

        private static final long serialVersionUID = 1L;

        @Override
        public String name() {
            return "";
        }

        @Override
        public int version() {
            return 0;
        }
    }
}
