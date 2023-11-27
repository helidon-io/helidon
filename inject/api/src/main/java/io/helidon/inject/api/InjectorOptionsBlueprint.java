/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Provides optional, contextual tunings to the {@link Injector}.
 *
 * @see Injector
 */
@Prototype.Blueprint(decorator = InjectorOptionsBlueprint.BuilderDecorator.class)
interface InjectorOptionsBlueprint {
    /**
     * The strategy the injector should apply. The default is {@link Injector.Strategy#ANY}.
     *
     * @return the injector strategy to use
     */
    @Option.Default("ANY")
    Injector.Strategy strategy();

    /**
     * Optionally, customized activator options to use for the {@link Activator}.
     *
     * @return activator options, or leave blank to use defaults
     */
    ActivationRequest activationRequest();


    /**
     * This will ensure that the activation request is populated.
     */
    class BuilderDecorator implements Prototype.BuilderDecorator<InjectorOptions.BuilderBase<?, ?>> {
        BuilderDecorator() {
        }

        @Override
        public void decorate(InjectorOptions.BuilderBase<?, ?> target) {
            if (target.activationRequest().isEmpty()) {
                target.activationRequest(InjectionServices.createActivationRequestDefault());
            }
        }
    }

}
