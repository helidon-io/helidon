/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;

/**
 * A file writer that subscribes to chunks of data.
 *
 * @deprecated use {@link io.helidon.common.reactive.IoMulti#writeToFile(java.nio.file.Path)} instead
 */
@Deprecated(forRemoval = true)
public final class FileSubscriber implements Flow.Subscriber<DataChunk> {
    private static final Logger LOGGER = Logger.getLogger(FileSubscriber.class.getName());

    private final CompletableFuture<Path> resultFuture = new CompletableFuture<>();
    private final Path filePath;
    private final Path tempPath;
    private final FileChannel channel;

    private Flow.Subscription subscription;

    private FileSubscriber(Path filePath, Path tempPath, FileChannel channel) {
        this.filePath = filePath;
        this.tempPath = tempPath;
        this.channel = channel;
    }

    /**
     * Subscribes this instance to the obtained publisher.
     *
     * @param publisher publisher
     * @return completion stage of the saving process
     */
    public CompletionStage<Path> subscribeTo(Flow.Publisher<DataChunk> publisher) {
        publisher.subscribe(this);
        return resultFuture;
    }

    /**
     * Create a subscriber that consumes {@link DataChunk DataChunks} and writes them to a file.
     * A temporary file is created first to download the whole content and it is then moved to the final
     * destination.
     *
     * @param filePath path of the final file
     * @return subscriber to consume {@link DataChunk}
     */
    public static FileSubscriber create(Path filePath) {
        // make sure we can write the path
        if (Files.exists(filePath)) {
            throw new WebClientException("Path " + filePath.toAbsolutePath() + " already exists, cannot download into it");
        }

        try {
            Path tempPath = Files.createTempFile("helidon-large", ".tmp");
            FileChannel channel = FileChannel.open(tempPath, StandardOpenOption.WRITE);
            return new FileSubscriber(filePath, tempPath, channel);
        } catch (IOException e) {
            throw new WebClientException("Failed to open temporary file", e);
        }
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(DataChunk item) {
        try {
            channel.write(item.data());

            subscription.request(1);
        } catch (IOException e) {
            throw new WebClientException("Failed to write data to temporary file: " + tempPath.toAbsolutePath(), e);
        } finally {
            item.release();
        }
    }

    @Override
    public void onError(Throwable throwable) {
        try {
            channel.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Received an onError", e);
        }

        resultFuture.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        try {
            channel.close();
            Files.move(tempPath, filePath);
            resultFuture.complete(filePath);
        } catch (IOException e) {
            throw new WebClientException("Failed to move file from temp to final. Temp: " + tempPath
                    .toAbsolutePath() + ", final: " + filePath.toAbsolutePath(), e);
        }
    }
}
