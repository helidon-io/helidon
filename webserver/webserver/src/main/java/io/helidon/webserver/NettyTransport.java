/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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
package io.helidon.webserver;

import java.util.Optional;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * Base {@link Transport} for Netty.
 */
public abstract class NettyTransport implements Transport {

	@Override
	public boolean isAvailableFor(WebServer webserver) {
			return webserver instanceof NettyWebServer;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Optional<T> createTransportArtifact(Class<T> artifactType,
																								 String artifactName,
																								 ServerConfiguration config) {
			if (EventLoopGroup.class.isAssignableFrom(artifactType)) {
					switch (artifactName) {
					case "bossGroup":
							return Optional.of((T) eventLoopGroup(config.sockets().size()));
					case "workerGroup":
							return Optional.of((T) eventLoopGroup(Math.max(0, config.workersCount())));
					default:
					}
			} else if (ChannelFactory.class.isAssignableFrom(artifactType)) {
					switch (artifactName) {
					case "serverChannelFactory":
							return Optional.of((T) channelFactory());
					default:
					}
			}
			return Optional.empty();
	}

	/**
	 * Create a new instance for the given number of threads.
	 *
	 * @param nThreads the number of threads.
	 * @return event loop group instance.
	 */
	protected abstract EventLoopGroup eventLoopGroup(int nThreads);

	/**
	 * @return channel factory instance.
	 */
	protected abstract ChannelFactory<? extends ServerChannel> channelFactory();

	/**
	 * Transport that leverages NIO Selector.
	 */
	static class NioTransport extends NettyTransport {

		@Override
		protected EventLoopGroup eventLoopGroup(int nThreads) {
			return new NioEventLoopGroup(nThreads);
		}

		@Override
		protected ChannelFactory<? extends ServerChannel> channelFactory() {
			return NioServerSocketChannel::new;
		}
	}

	/**
	 * Transport that leverages Netty Epoll which should reap better performance than NIO.
	 */
	static class EpollTransport extends NettyTransport {

		@Override
		public boolean isAvailableFor(WebServer webserver) {
			return super.isAvailableFor(webserver) && Epoll.isAvailable();
		}

		@Override
		protected EventLoopGroup eventLoopGroup(int nThreads) {
			return new EpollEventLoopGroup(nThreads);
		}

		@Override
		protected ChannelFactory<? extends ServerChannel> channelFactory() {
			return EpollServerSocketChannel::new;
		}
	}
}
