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

package io.helidon.rsocket.server;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

import javax.websocket.Session;

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


/**
 * Helidon Duplex connection for RSocket.
 */
public class HelidonDuplexConnection implements DuplexConnection {

    private final Session session;
    private final Sinks.Empty<Void> onCloseSink;
    private final Sinks.Many<ByteBuf> receiverSink;

    /**
     * Constructor for HelidonDuplexConnection.
     *
     * @param session
     */
    public HelidonDuplexConnection(Session session) {
      this.onCloseSink = Sinks.empty();
      this.session = session;
      this.receiverSink = Sinks.<ByteBuf>unsafe().many().unicast().onBackpressureBuffer();
    }

    /**
     * Send Frame.
     *
     * @param i
     * @param byteBuf data
     */
    @Override
    public void sendFrame(int i, ByteBuf byteBuf) {
      try {
        final ByteBuf bb = Unpooled.copiedBuffer(byteBuf);
        session.getAsyncRemote().sendBinary(bb.nioBuffer());
      } catch (Throwable t) {
        dispose();
      } finally {
        byteBuf.release();
      }
    }

    /**
     * Close on Exception.
     *
     * @param e {@link RSocketErrorException}
     */
    @Override
    public void sendErrorAndClose(RSocketErrorException e) {
      final ByteBuf bb = ErrorFrameCodec.encode(UnpooledByteBufAllocator.DEFAULT, 0, e);
      session.getAsyncRemote().sendBinary(bb.nioBuffer(), sendResult -> dispose());
    }

    /**
     * Receive function.
     *
     * @return Flux with ByteBuf
     */
    @Override
    public Flux<ByteBuf> receive() {
      return receiverSink.asFlux()
              .doOnSubscribe(subscription ->
                  session.addMessageHandler(
                      ByteBuffer.class,
                      message -> receiverSink.emitNext(Unpooled.wrappedBuffer(message), EmitFailureHandler.FAIL_FAST)
                  )
              );
    }

    /**
     * Allocate ByteBuffer.
     *
     * @return {@link ByteBufAllocator}
     */
    @Override
    public ByteBufAllocator alloc() {
      return UnpooledByteBufAllocator.DEFAULT;
    }

    /**
     * Return remote address. Not required.
     *
     * @return {@link SocketAddress}
     */
    @Override
    public SocketAddress remoteAddress() {
      return null;
    }

    /**
     * Called on Close.
     *
     * @return Mono with Void.
     */
    @Override
    public Mono<Void> onClose() {
      return onCloseSink.asMono();
    }

    /**
     * Dispose connection.
     */
    @Override
    public void dispose() {
        try {
            session.close();
        } catch (Throwable e) {
            //left intentionally
        }
        receiverSink.tryEmitComplete();
        onCloseSink.tryEmitEmpty();
    }
  }
