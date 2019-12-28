/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.internal.ConfigFileTypeDetector;

/**
 * Common Configuration utilities.
 */
public final class ConfigHelper {
    private static final int DEFAULT_BUFFER_CAPACITY = 1024;
    private static final Logger LOGGER = Logger.getLogger(ConfigFileTypeDetector.class.getName());

    private ConfigHelper() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * Creates a {@link Reader} from the given {@link Readable} object.
     * <p>
     * Equivalent to {@code createReader(readable, 1024)}. See
     * {@link #createReader(Readable,int)}.
     *
     * @param readable a readable
     * @return a reader
     * @throws IOException when {@link Readable#read(CharBuffer)} encounters an error
     */
    public static Reader createReader(Readable readable) throws IOException {
        return createReader(readable, DEFAULT_BUFFER_CAPACITY);
    }

    /**
     * Creates a {@link Reader} from the given {@link Readable} object using the
     * specified buffer size.
     *
     * @param readable       a readable
     * @param bufferCapacity a new buffer capacity, in chars
     * @return a reader
     * @throws IOException when {@link Readable#read(CharBuffer)} encounters an error
     */
    static Reader createReader(Readable readable, int bufferCapacity) throws IOException {
        if (readable instanceof Reader) {
            return (Reader) readable;
        }
        CharBuffer cb = CharBuffer.allocate(bufferCapacity);
        StringBuilder sb = new StringBuilder();
        while (readable.read(cb) != -1) {
            cb.flip();
            sb.append(cb.toString());
        }

        return new StringReader(sb.toString());
    }

    /**
     * Creates a {@link ConfigHelper#subscriber(Function) Flow.Subscriber} that
     * will delegate {@link Flow.Subscriber#onNext(Object)} to the specified
     * {@code onNextFunction} function.
     * <p>
     * The new subscriber's
     * {@link Flow.Subscriber#onSubscribe(Flow.Subscription)} method
     * automatically invokes {@link Flow.Subscription#request(long)} to request
     * all events that are available in the subscription.
     * <p>
     * The caller-provided {@code onNextFunction} should return {@code false} in
     * order to {@link Flow.Subscription#cancel() cancel} current subscription.
     *
     * @param onNextFunction function to be invoked during {@code onNext}
     * processing
     * @param <T> the type of the items provided by the subscription
     * @return {@code Subscriber} that delegates its {@code onNext} to the
     * caller-provided function
     */
    public static <T> Flow.Subscriber<T> subscriber(Function<T, Boolean> onNextFunction) {
        return new OnNextFunctionSubscriber<>(onNextFunction);
    }

    /**
     * Creates a {@link Flow.Publisher} which wraps the provided one and also
     * supports "active" and "suspended" states.
     * <p>
     * The new {@code Publisher} starts in the "suspended" state.
     * Upon the first subscriber request the {@code Publisher} transitions into the "active" state
     * and invokes the caller-supplied {@code onFirstSubscriptionRequest} {@code Runnable}.
     * When the last subscriber cancels the returned {@code Publisher} transitions into the "suspended" state and
     * invokes the caller-provided {@code onLastSubscriptionCancel} {@code Runnable}.
     *
     * @param delegatePublisher          publisher to be wrapped
     * @param onFirstSubscriptionRequest hook invoked when the first subscriber requests events from the publisher
     * @param onLastSubscriptionCancel   hook invoked when last remaining subscriber cancels its subscription
     * @param <T>                        the type of the items provided by the publisher
     * @return new instance of suspendable {@link Flow.Publisher}
     */
    public static <T> Flow.Publisher<T> suspendablePublisher(Flow.Publisher<T> delegatePublisher,
                                                             Runnable onFirstSubscriptionRequest,
                                                             Runnable onLastSubscriptionCancel) {
        return new SuspendablePublisher<T>(delegatePublisher) {
            @Override
            protected void onFirstSubscriptionRequest() {
                onFirstSubscriptionRequest.run();
            }

            @Override
            protected void onLastSubscriptionCancel() {
                onLastSubscriptionCancel.run();
            }
        };
    }

    // lazy loading of default and installed file type detectors
    private static class FileTypeDetectors {
        static final List<FileTypeDetector> INSTALLED_DETECTORS =
            loadInstalledDetectors();

        // loads all installed file type detectors
        private static List<FileTypeDetector> loadInstalledDetectors() {
            return AccessController
                    .doPrivileged(new PrivilegedAction<List<FileTypeDetector>>() {
                        @Override
                        public List<FileTypeDetector> run() {
                            List<FileTypeDetector> list = new LinkedList<>();
                            ServiceLoader<FileTypeDetector> loader = ServiceLoader
                                    .load(FileTypeDetector.class);
                            for (FileTypeDetector detector : loader) {
                                list.add(detector);
                            }
                            return list;
                        }
                    });
        }
    }

    /**
     * Infers the content type contained in the provided {@code Path}.
     *
     * @param path path to check
     * @return string with content type
     */
    public static String detectContentType(Path path) {
        String result = null;

        try {
            for (FileTypeDetector detector : FileTypeDetectors.INSTALLED_DETECTORS) {
                result = detector.probeContentType(path);
                if (result != null) {
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINEST,
                       e,
                       () -> "Failed to find content type for " + path);
        }

        return result;
    }

    /**
     * Implementation of {@link ConfigHelper#subscriber(Function)}.
     *
     * @param <T> the subscribed item type
     * @see ConfigHelper#subscriber(Function)
     */
    private static class OnNextFunctionSubscriber<T> implements Flow.Subscriber<T> {
        private final Function<T, Boolean> onNextFunction;
        private final Logger logger;
        private Flow.Subscription subscription;

        private OnNextFunctionSubscriber(Function<T, Boolean> onNextFunction) {
            this.onNextFunction = onNextFunction;
            this.logger = Logger.getLogger(OnNextFunctionSubscriber.class.getName() + "."
                                                   + Integer.toHexString(System.identityHashCode(onNextFunction)));
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            logger.finest(() -> "onSubscribe: " + subscription);

            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            boolean cancel = !onNextFunction.apply(item);

            logger.finest(() -> "onNext: " + item + " => " + (cancel ? "CANCEL" : "FOLLOW"));

            if (cancel) {
                subscription.cancel();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            logger.log(Level.WARNING,
                       throwable,
                       () -> "Config Changes support failed. " + throwable.getLocalizedMessage());
        }

        @Override
        public void onComplete() {
            logger.config("Config Changes support finished. There will no other Config reload.");
        }

    }

}
