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

import io.helidon.builder.api.Prototype;

class MediaContextBuilderDecorator implements Prototype.BuilderDecorator<MediaContextConfig.BuilderBase<?, ?>> {
    @Override
    public void decorate(MediaContextConfig.BuilderBase<?, ?> target) {
        if (target.registerDefaults()) {
            target.addMediaSupport(StringSupport.create())
                    .addMediaSupport(PathSupport.create())
                    .addMediaSupport(FormParamsSupport.create());
        }
    }
}
