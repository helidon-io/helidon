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

import javax.websocket.CloseReason;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import org.glassfish.tyrus.spi.Connection;

import static javax.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE;
import static javax.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;

/**
 * Class TyrusReaderSubscriber.
 */
public class TyrusReaderSubscriber implements Flow.Subscriber<DataChunk> {
    private static final Logger LOGGER = Logger.getLogger(TyrusSupport.class.getName());

    private static final int MAX_RETRIES = 3;
    private static final CloseReason CONNECTION_CLOSED = new CloseReason(NORMAL_CLOSURE, "Connection closed");

    private final Connection connection;

    TyrusReaderSubscriber(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }
        this.connection = connection;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(DataChunk item) {
        // Send data to Tyrus
        ByteBuffer data = item.data();
        connection.getReadHandler().handle(data);

        // Retry a few times if Tyrus did not consume all data
        int retries = MAX_RETRIES;
        while (data.remaining() > 0 && retries-- > 0) {
            LOGGER.warning("Tyrus did not consume all data buffer");
            connection.getReadHandler().handle(data);
        }

        // Report error if data is still unconsumed
        if (retries == 0) {
            throw new RuntimeException("Tyrus unable to consume data buffer");
        }
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
