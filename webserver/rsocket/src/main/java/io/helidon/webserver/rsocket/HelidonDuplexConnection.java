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
package io.helidon.webserver.rsocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.rsocket.DuplexConnection;
import io.rsocket.RSocketErrorException;
import io.rsocket.frame.ErrorFrameCodec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import reactor.core.publisher.Sinks.Many;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import javax.websocket.Session;

public class HelidonDuplexConnection implements DuplexConnection {

    final Session session;
    final Sinks.Empty<Void> onCloseSink;

    HelidonDuplexConnection(Session session) {
      this.onCloseSink = Sinks.empty();
      this.session = session;
    }

    @Override
    public void sendFrame(int i, ByteBuf byteBuf) {
      try {
        final ByteBuf bb = Unpooled.copiedBuffer(byteBuf);
        session.getAsyncRemote().sendBinary(bb.nioBuffer());
      } finally {
        byteBuf.release();
      }
    }

    @Override
    public void sendErrorAndClose(RSocketErrorException e) {
      final ByteBuf bb = ErrorFrameCodec.encode(UnpooledByteBufAllocator.DEFAULT, 0, e);
      session.getAsyncRemote().sendBinary(bb.nioBuffer());
    }

    @Override
    public Flux<ByteBuf> receive() {
      final Many<ByteBuf> sink = Sinks.<ByteBuf>unsafe().many().multicast().directBestEffort();
      session.addMessageHandler(ByteBuffer.class,
          message -> sink.emitNext(Unpooled.wrappedBuffer(message), EmitFailureHandler.FAIL_FAST));
      return sink.asFlux();
    }

    @Override
    public ByteBufAllocator alloc() {
      return UnpooledByteBufAllocator.DEFAULT;
    }

    @Override
    public SocketAddress remoteAddress() {
      return null;
    }

    @Override
    public Mono<Void> onClose() {
      return onCloseSink.asMono();
    }

    @Override
    public void dispose() {

    }
  }
