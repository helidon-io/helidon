/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.common;

/**
 * Extended interface of the base media support builder methods.
 *
 * @param <T> Type of the class which this builder support is added to.
 */
public interface MediaSupportBuilder<T> extends BaseMediaSupportBuilder<T> {

    /**
     * Sets the new {@link MediaSupport} and overrides the existing one.
     * This method overrides all previously registered readers and writers.
     *
     * @param mediaSupport media support
     * @return updated instance of the builder
     */
    T mediaSupport(MediaSupport mediaSupport);

}
