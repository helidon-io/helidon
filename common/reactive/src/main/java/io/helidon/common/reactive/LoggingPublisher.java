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

package io.helidon.common.reactive;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

class LoggingPublisher<T> implements Flow.Publisher<T> {

    private static final Logger LOGGER = Logger.getLogger(LoggingPublisher.class.getName());

    private static final AtomicLong LOG_ID = new AtomicLong();

    private final String caller;
    private final String loggerName;
    private final String methodName;
    private final Flow.Publisher<T> source;
    private final Level level;

    LoggingPublisher(final Flow.Publisher<T> source, Level level, String loggerName) {
        Objects.requireNonNull(loggerName);
        this.source = source;
        this.level = level;
        this.loggerName = loggerName;
        caller = resolveCaller(source);
        methodName = "log()";
    }

    LoggingPublisher(final Flow.Publisher<T> source, Level level, boolean trace) {
        this.source = source;
        this.level = level;
        if (trace) {
            Optional<StackWalker.StackFrame> frm = findCaller();
            if (frm.isPresent()) {
                caller = frm.map(StackWalker.StackFrame::getClassName).orElse(Multi.class.getName());
                String fileName = frm.map(f -> f.getFileName() + ":" + f.getLineNumber()).orElse("");
                methodName = frm.map(StackWalker.StackFrame::getMethodName).orElse("log") + "(" + fileName + ")";
                loggerName = caller + "." + methodName;
                return;
            }
        }
        caller = resolveCaller(source);
        methodName = "log()";
        loggerName = Multi.class.getSimpleName() + ".log(" + LOG_ID.incrementAndGet() + ")";
    }

    private String resolveCaller(Flow.Publisher<T> source) {
        if (source instanceof NamedOperator) {
            return ((NamedOperator) source).operatorName();
        } else {
            return source.getClass().getSimpleName();
        }
    }

    private Optional<StackWalker.StackFrame> findCaller() {
        return StackWalker.getInstance().walk(frmStream -> frmStream
                .limit(4)
                .skip(3)
                .findFirst());
    }

    void log(Supplier<String> msgSupplier) {
        if (!LOGGER.isLoggable(level)) {
            return;
        }
        LogRecord record = new LogRecord(level, msgSupplier.get());
        record.setSourceClassName(caller);
        record.setSourceMethodName(methodName);
        record.setLoggerName(loggerName);
        LOGGER.log(record);
    }

    void logCancel() {
        log(() -> " ⇗ cancel()");
    }

    void logOnComplete() {
        log(() -> " ⇘ onComplete()");
    }

    void logRequest(long n) {
        log(() -> " ⇗ request(" + (n == Long.MAX_VALUE ? "Long.MAX_VALUE" : n) + ")");
    }

    void logOnError(Throwable throwable) {
        log(() -> " ⇘ onError(" + throwable + ")");
    }

    void logOnSubscribe(Flow.Subscription subscription) {
        log(() -> " ⇘ onSubscribe(...)");
    }

    void logOnNext(T item) {
        log(() -> " ⇘ onNext(" + item + ")");
    }

    @Override
    public void subscribe(final Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        source.subscribe(new MultiTappedPublisher.MultiTappedSubscriber<>(subscriber,
                this::logOnSubscribe,
                this::logOnNext,
                this::logOnError,
                this::logOnComplete,
                this::logRequest,
                this::logCancel));
    }
}
