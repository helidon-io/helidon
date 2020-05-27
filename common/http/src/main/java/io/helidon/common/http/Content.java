/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.common.reactive.Single;

/**
 * Represents an HTTP entity as a {@link Flow.Publisher publisher} of {@link DataChunk chunks} with specific
 * features.
 * <h3>Default publisher contract</h3>
 * Default publisher accepts only single subscriber. Other subscribers receives
 * {@link Flow.Subscriber#onError(Throwable) onError()}.
 * <p>
 * {@link DataChunk} provided by {@link Flow.Subscriber#onNext(Object) onNext()} method <b>must</b> be consumed in this
 * method call. Buffer can be reused by network infrastructure as soon as {@code onNext()} method returns.
 * This behavior can be inconvenient yet it helps to provide excellent performance.
 *
 * <h3>Publisher Overwrite.</h3>
 * It is possible to modify contract of the original publisher by registration of a new publisher using
 * {@link #registerFilter(Function)} method. It can be used to wrap or replace previously registered (or default) publisher.
 *
 * <h3>Entity Readers</h3>
 * It is possible to register function to convert publisher to {@link io.helidon.common.reactive.Single} of a single entity using
 * {@link #registerReader(Class, Reader)} or {@link #registerReader(Predicate, Reader)} methods. It
 * is then possible to use {@link #as(Class)} method to obtain such entity.
 * @deprecated use {@code io.helidon.media.common.MessageBodyReadableContent} instead
 */
@Deprecated
public interface Content extends Flow.Publisher<DataChunk> {
    /**
     * If possible, adds the given Subscriber to this publisher. This publisher is effectively
     * either the original publisher
     * or the last publisher registered by the method {@link #registerFilter(Function)}.
     * <p>
     * Note that the original publisher allows only a single subscriber and requires the passed
     * {@link DataChunk} in the {@link Flow.Subscriber#onNext(Object)} call
     * to be consumed before the method completes as specified by the {@link Content Default Publisher Contract}.
     *
     * @param subscriber the subscriber
     * @throws NullPointerException if subscriber is null
     */
    @Override
    void subscribe(Flow.Subscriber<? super DataChunk> subscriber);

    /**
     * Registers a filter that allows a control of the original publisher.
     * <p>
     * The provided function is evaluated upon calling either of {@link #subscribe(Flow.Subscriber)}
     * or {@link #as(Class)}.
     * The first evaluation of the function transforms the original publisher to a new publisher.
     * Any subsequent evaluation receives the publisher transformed by the last previously
     * registered filter.
     * It is up to the implementor of the given function to respect the contract of both the original
     * publisher and the previously registered ones.
     *
     * @param function a function that transforms a given publisher (that is either the original
     *                 publisher or the publisher transformed by the last previously registered filter).
     * @deprecated use {@code io.helidon.media.common.MessageBodyReaderContext.registerFilter}
     */
    @Deprecated
    void registerFilter(Function<Flow.Publisher<DataChunk>, Flow.Publisher<DataChunk>> function);

    /**
     * Registers a reader for a later use with an appropriate {@link #as(Class)} method call.
     * <p>
     * The reader must transform the published byte buffers into a completion stage of the
     * requested type.
     * <p>
     * Upon calling {@link #as(Class)} a matching reader is searched in the same order as the
     * readers were registered. If no matching reader is found, or when the function throws
     * an exception, the resulting completion stage ends exceptionally.
     *
     * @param type   the requested type the completion stage is be associated with.
     * @param reader the reader as a function that transforms a publisher into completion stage.
     *               If an exception is thrown, the resulting completion stage of
     *               {@link #as(Class)} method call ends exceptionally.
     * @param <T>    the requested type
     * @deprecated use {@code io.helidon.media.common.MessageBodyReaderContext.registerReader}
     */
    @Deprecated
    <T> void registerReader(Class<T> type, Reader<T> reader);

    /**
     * Registers a reader for a later use with an appropriate {@link #as(Class)} method call.
     * <p>
     * The reader must transform the published byte buffers into a completion stage of the
     * requested type.
     * <p>
     * Upon calling {@link #as(Class)} a matching reader is searched in the same order as the
     * readers were registered. If no matching reader is found or when the predicate throws
     * an exception, or when the function throws an exception, the resulting completion stage
     * ends exceptionally.
     *
     * @param predicate the predicate that determines whether the registered reader can handle
     *                  the requested type. If an exception is thrown, the resulting completion
     *                  stage of {@link #as(Class)} method call ends exceptionally.
     * @param reader    the reader as a function that transforms a publisher into completion stage.
     *                  If an exception is thrown, the resulting completion stage of
     *                  {@link #as(Class)} method call ends exceptionally.
     * @param <T>       the requested type
     * @deprecated use {@code io.helidon.media.common.MessageBodyReaderContext.registerReader}
     */
    @Deprecated
    <T> void registerReader(Predicate<Class<?>> predicate, Reader<T> reader);

    /**
     * Consumes and converts the request content into a completion stage of the requested type.
     * <p>
     * The conversion requires an appropriate reader to be already registered
     * (see {@link #registerReader(Predicate, Reader)}). If no such reader is found, the
     * resulting completion stage ends exceptionally.
     *
     * @param <T>  the requested type
     * @param type the requested type class
     * @return a completion stage of the requested type
     */
    <T> Single<T> as(Class<T> type);
}
