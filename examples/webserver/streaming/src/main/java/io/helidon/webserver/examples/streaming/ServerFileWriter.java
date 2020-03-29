/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.examples.streaming;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.webserver.ServerResponse;

/**
 * Class ServerFileWriter. Process data chunks from a {@code Producer} and
 * writes them to a temporary file using NIO. For simplicity, this {@code
 * Subscriber} requests an unbounded number of chunks on its subscription.
 */
public class ServerFileWriter implements Flow.Subscriber<DataChunk> {
    private static final Logger LOGGER = Logger.getLogger(ServerFileWriter.class.getName());

    private final FileChannel channel;

    private final ServerResponse response;

    ServerFileWriter(ServerResponse response) {
        this.response = response;
        try {
            Path tempFilePath = Files.createTempFile("large-file", ".tmp");
            channel = FileChannel.open(tempFilePath, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(DataChunk chunk) {
        try {
            channel.write(chunk.data());
            LOGGER.info(chunk.data().toString() + " " + Thread.currentThread());
            chunk.release();
        } catch (IOException e) {
            LOGGER.info(e.getMessage());
        }
    }

    @Override
    public void onError(Throwable throwable) {
        throwable.printStackTrace();
    }

    @Override
    public void onComplete() {
        try {
            channel.close();
            response.send("DONE");
        } catch (IOException e) {
            LOGGER.info(e.getMessage());
        }
    }
}
