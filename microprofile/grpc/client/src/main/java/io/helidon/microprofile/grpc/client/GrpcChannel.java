/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
 * A qualifier annotation that can be used to specify the name of a configured gRPC
 * channel to inject, or the name of the host to connect to, as described in
 * {@link io.helidon.grpc.client.GrpcChannelsProvider#channel(String)} documentation.
 * <p>
 * For example:
 *
 * <pre>
 *     &#064;Inject
 *     &#064;GrpcChannel(name = "foo")
 *     private Channel channel;
 * </pre>
 *
 * This annotation can also be specified at the injection point for a client proxy,
 * in combination with the {@link GrpcServiceProxy @GrpcServiceProxy} annotation:
 *
 * <pre>
 *     &#064;Inject
 *     &#064;GrpcChannel(name = "foo")
 *     &#064;GrpcServiceProxy
 *     private FooServiceClient client;
 * </pre>
 *
 * Alternatively, if the client proxy should always use the same channel, it can
 * be specified on the client interface instead:
 *
 * <pre>
 *     &#064;RpcService(name = "FooService")
 *     &#064;GrpcChannel(name = "foo")
 *     public interface FooServiceClient {
 *         ...
 *     };
 * </pre>
 *
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface GrpcChannel {
    /**
     * The name of the configured channel or gRPC server host.
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
