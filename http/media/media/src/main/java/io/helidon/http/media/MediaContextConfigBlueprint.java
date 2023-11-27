/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.http.media;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.http.media.spi.MediaSupportProvider;

@Prototype.Blueprint(decorator = MediaContextBuilderDecorator.class)
@Prototype.Configured
interface MediaContextConfigBlueprint extends Prototype.Factory<MediaContext> {
    /**
     * Media supports to use.
     * This instance has priority over provider(s) discovered by service loader.
     * The providers are used in order of calling this method, where the first support added is the
     * first one to be queried for readers and writers.
     *
     * @return media supports
     */
    @Option.Singular
    @Option.Configured
    @Option.Provider(MediaSupportProvider.class)
    List<MediaSupport> mediaSupports();

    /**
     * Existing context to be used as a fallback for this context.
     *
     * @return media context to use if supports configured on this request cannot provide a good result
     */
    @Option.Configured
    Optional<MediaContext> fallback();

    /**
     * Should we register defaults of Helidon, such as String media support.
     *
     * @return whether to register default media supports
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean registerDefaults();
}
