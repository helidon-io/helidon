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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Flow;

/**
 * Class ServerFileReader. Reads a file using NIO and produces data chunks for a
 * {@code Subscriber} to process.
 */
public class ServerFileReader implements Flow.Publisher<DataChunk> {
    private static final Logger LOGGER = Logger.getLogger(ServerFileReader.class.getName());

    static final int BUFFER_SIZE = 4096;

    private final Path path;

    ServerFileReader(Path path) {
        this.path = path;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super DataChunk> s) {
        FileChannel channel;
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        try {
            channel = FileChannel.open(path, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        s.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                try {
                    while (n > 0) {
                        int bytes = channel.read(buffer);
                        if (bytes < 0) {
                            s.onComplete();
                            channel.close();
                            return;
                        }
                        if (bytes > 0) {
                            LOGGER.info(buffer.toString());
                            buffer.flip();
                            s.onNext(DataChunk.create(buffer));
                            n--;
                        }
                        buffer.rewind();
                    }
                } catch (IOException e) {
                    s.onError(e);
                }
            }

            @Override
            public void cancel() {
                try {
                    channel.close();
                } catch (IOException e) {
                    LOGGER.info(e.getMessage());
                }
            }
        });
    }
}
