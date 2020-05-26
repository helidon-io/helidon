/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
import java.util.concurrent.Flow;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

class MultiLoggingPublisher<T> implements Multi<T> {

    private static final Logger LOGGER = Logger.getLogger(MultiLoggingPublisher.class.getName());

    private final String caller;
    private final String streamDescription;
    private final Multi<T> source;

    MultiLoggingPublisher(final Multi<T> source) {
        this.source = source;
        if (this.source instanceof OperatorWithDescription) {
            streamDescription = ((OperatorWithDescription) this.source).getDescription();
        } else {
            streamDescription = this.getClass().getSimpleName();
        }
        caller = StackWalker.getInstance().walk(frmStream -> frmStream
                .limit(3)
                .skip(2)
                .map(String::valueOf)
                .map(this::parseStackFrame)
                .findFirst()).get();
    }

    private String parseStackFrame(String stackFrameAsString){
        if (stackFrameAsString.contains("/")){
           return stackFrameAsString.split("/")[1];
        }
        return stackFrameAsString;
    }

    private static void loggr(String caller, String msg) {
        LogRecord record = new LogRecord(Level.INFO, msg);
        record.setSourceClassName(caller);
        LOGGER.log(record);
    }

    private void logCancel() {
        loggr(caller, streamDescription + ".cancel()");
    }

    private void logOnComplete() {
        loggr(caller, streamDescription + ".onComplete()");
    }

    private void logRequest(long n) {
        loggr(caller, "request(" + (n == Long.MAX_VALUE ? "Long.MAX_VALUE" : n) + ")");
    }

    private void logOnError(Throwable throwable) {
        loggr(caller, streamDescription + ".onError(" + throwable + ")");
    }

    private void logOnSubscribe(Flow.Subscription subscription) {
        loggr(caller, streamDescription + ".onSubscribe(...)");
    }

    private void logOnNext(T item) {
        loggr(caller, streamDescription + ".onNext(" + item + ")");
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
