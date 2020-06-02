/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.tyrus;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

import javax.websocket.CloseReason;

import io.helidon.common.http.DataChunk;

import org.glassfish.tyrus.spi.Connection;

import static javax.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE;
import static javax.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;

/**
 * Class TyrusReaderSubscriber.
 */
public class TyrusReaderSubscriber implements Flow.Subscriber<DataChunk> {
    private static final Logger LOGGER = Logger.getLogger(TyrusSupport.class.getName());

    private static final int MAX_RETRIES = 5;
    private static final CloseReason CONNECTION_CLOSED = new CloseReason(NORMAL_CLOSURE, "Connection closed");

    private final Connection connection;
    private final ExecutorService executorService;
    private Flow.Subscription subscription;

    TyrusReaderSubscriber(Connection connection) {
        this(connection, null);
    }

    TyrusReaderSubscriber(Connection connection, ExecutorService executorService) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }
        this.connection = connection;
        this.executorService = executorService;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1L);
    }

    @Override
    public void onNext(DataChunk item) {
        if (executorService == null) {
            for (ByteBuffer byteBuffer : item.data()) {
                submitBuffer(byteBuffer);
            }
        } else {
            executorService.submit(() -> {
                for (ByteBuffer byteBuffer : item.data()) {
                    submitBuffer(byteBuffer);
                }
            });
        }
    }

    /**
     * Submits data buffer to Tyrus. Retries a few times to make sure the entire buffer
     * is consumed or logs an error.
     *
     * @param data Data buffer.
     */
    private void submitBuffer(ByteBuffer data) {
        int retries = MAX_RETRIES;
        while (data.remaining() > 0 && retries-- > 0) {
            connection.getReadHandler().handle(data);
        }
        if (retries == 0) {
            LOGGER.warning("Tyrus did not consume all data buffer after " + MAX_RETRIES + " retries");
        }
        subscription.request(1L);
    }

    @Override
    public void onError(Throwable throwable) {
        connection.close(new CloseReason(UNEXPECTED_CONDITION, throwable.getMessage()));
    }

    @Override
    public void onComplete() {
        connection.close(CONNECTION_CLOSED);
    }
}
