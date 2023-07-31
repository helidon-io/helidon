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

package io.helidon.inject.api;

import java.util.Optional;

import io.helidon.builder.api.Prototype;

/**
 * Internal bootstrap is what we store when {@link InjectionServices#globalBootstrap(Bootstrap)} is used.
 */
@Prototype.Blueprint(decorator = InternalBootstrapBlueprint.BuilderInterceptor.class)
interface InternalBootstrapBlueprint {

    /**
     * The user established bootstrap.
     *
     * @return user established bootstrap
     */
    Optional<Bootstrap> bootStrap();

    /**
     * Only populated when {@link InjectionServices#TAG_DEBUG} is set.
     *
     * @return the calling context
     */
    Optional<CallingContext> callingContext();

    class BuilderInterceptor implements Prototype.BuilderDecorator<InternalBootstrap.BuilderBase<?, ?>> {
        @Override
        public InternalBootstrap.BuilderBase<?, ?> decorate(InternalBootstrap.BuilderBase<?, ?> target) {
            if (target.bootStrap().isEmpty()) {
                target.bootStrap(Bootstrap.create());
            }
            return target;
        }
    }
}
