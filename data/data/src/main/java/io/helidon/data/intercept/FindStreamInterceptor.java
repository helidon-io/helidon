/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.intercept;

import java.util.stream.Stream;

/**
 * An interceptor that executes a {@link io.micronaut.data.annotation.Query} and returns a {@link java.util.stream.Stream} of results.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> The declaring type
 */
public interface FindStreamInterceptor<T> extends DataInterceptor<T, Stream<T>> {
}
