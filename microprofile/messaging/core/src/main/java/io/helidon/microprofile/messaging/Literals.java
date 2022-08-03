/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.io.Serial;
import java.util.concurrent.Flow;

import io.helidon.common.reactive.Multi;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.reactivestreams.Publisher;



class Literals {

    private Literals(){
    }

    static <T> TypeLiteral<Flow.Publisher<T>> flowPublisherType() {
        return new TypeLiteral<>() {
        };
    }

    static <T> TypeLiteral<Multi<T>> multiType() {
        return new TypeLiteral<>() {
        };
    }

    static <T> TypeLiteral<PublisherBuilder<T>> publisherBuilderType() {
        return new TypeLiteral<>() {
            @Serial
            private static final long serialVersionUID = -3906621393273806089L;
        };
    }

    static <T> TypeLiteral<Publisher<T>> publisherType() {
        return new TypeLiteral<>() {
            @Serial
            private static final long serialVersionUID = 3144888301100811722L;
        };
    }

    static <T> TypeLiteral<Emitter<T>> emitterType() {
        return new TypeLiteral<>() {
            @Serial
            private static final long serialVersionUID = 3401873958631233858L;
        };
    }

    static Channel channel(String name) {
        return new ChannelLiteral(name);
    }

    static ChannelInternal internalChannel(String name) {
        return new ChannelInternalLiteral(name);
    }

    static OnOverflow defaultOnOverflow() {
        return DefaultOnOverflowLiteral.create();
    }

    private static class ChannelLiteral extends AnnotationLiteral<Channel> implements Channel {
        @Serial
        private static final long serialVersionUID = -4589239119732652918L;

        private final String channelName;

        public static ChannelLiteral of(String name) {
            return new ChannelLiteral(name);
        }

        ChannelLiteral(String channelName) {
            this.channelName = channelName;
        }


        @Override
        public String value() {
            return channelName;
        }
    }

    private static class ChannelInternalLiteral extends AnnotationLiteral<ChannelInternal> implements ChannelInternal {

        @Serial
        private static final long serialVersionUID = -6822848404758628126L;
        private final String channelName;

        public static ChannelInternalLiteral of(String channelName) {
            return new ChannelInternalLiteral(channelName);
        }

        ChannelInternalLiteral(String channelName) {
            this.channelName = channelName;
        }


        @Override
        public String value() {
            return channelName;
        }
    }

    static class DefaultOnOverflowLiteral extends AnnotationLiteral<OnOverflow> implements OnOverflow {

        public static DefaultOnOverflowLiteral create() {
            return new DefaultOnOverflowLiteral();
        }

        @Override
        public Strategy value() {
            return Strategy.BUFFER;
        }

        @Override
        public long bufferSize() {
            return 0;
        }
    }
}
