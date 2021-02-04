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
 */

package io.helidon.webserver.transport.netty.epoll;

import java.util.Optional;

import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.Transport;
import io.helidon.webserver.WebServer;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;

/**
 * A {@link Transport} implementation based upon Netty's <a
 * href="https://netty.io/wiki/native-transports.html#using-the-linux-native-transport"
 * target="_parent">epoll-based native transport</a>.
 */
public final class EPollTransport implements Transport {

    /**
     * Returns {@code true} when {@link Epoll#isAvailable()} returns
     * {@code true} and {@code false} otherwise.
     *
     * @return {@code true} when {@link Epoll#isAvailable()} returns
     * {@code true}; {@code false} otherwise
     */
    @Override
    public boolean isAvailableFor(WebServer webServer) {
        return Epoll.isAvailable();
    }

    /**
     * Returns an artifact corresponding to the supplied artifact
     * coordinates, if one is available.
     *
     * <p>Specifically, this method will return a non-{@linkplain
     * Optional#isEmpty() empty <code>Optional</code>} only if one of the
     * following conditions is true:</p>
     *
     * <ul>
     *
     * <li>{@code artifactType} is a subtype of {@link EventLoopGroup}
     * and {@code artifactName} is exactly {@linkplain
     * String#equals(Object) equal} to either {@code bossGroup} or
     * {@code workerGroup}</li>
     *
     * <li>{@code artifactType} is a subtype of {@link ChannelFactory}
     * and {@code artifactName} is exactly {@linkplain
     * String#equals(Object) equal} to {@code serverChannelFactory}</li>
     *
     * </ul>
     *
     * @param artifactType a {@link Class} indicating the kind of
     * artifact to be returned; must not be {@code null}; may usefully
     * only be a subtype of either {@link EventLoopGroup} or {@link
     * ServerChannelFactory}
     *
     * @param artifactName a {@link String} indicating which of
     * possibly several artifacts of the same kind to be returned;
     * must not be {@code null}
     *
     * @param config the {@link ServerConfiguration} in effect; must
     * not be {@code null}
     *
     * @return an {@link Optional}, which may be {@linkplain
     * Optional#isEmpty() empty} but which will never be {@code null}
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see Epoll
     *
     * @see EpollEventLoopGroup
     *
     * @see EpollServerSocketChannel
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> createTransportArtifact(Class<T> artifactType,
                                                   String artifactName,
                                                   ServerConfiguration config) {
        if (EventLoopGroup.class.isAssignableFrom(artifactType)) {
            switch (artifactName) {
            case "bossGroup":
                return Optional.of((T) new EpollEventLoopGroup(config.sockets().size()));
            case "workerGroup":
                return Optional.of((T) new EpollEventLoopGroup(Math.max(0, config.workersCount())));
            default:
                return Optional.empty();
            }
        } else if (ChannelFactory.class.isAssignableFrom(artifactType)) {
            switch (artifactName) {
            case "serverChannelFactory":
                ChannelFactory<? extends ServerChannel> cf = EpollServerSocketChannel::new;
                return Optional.of((T) cf);
            default:
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

}
