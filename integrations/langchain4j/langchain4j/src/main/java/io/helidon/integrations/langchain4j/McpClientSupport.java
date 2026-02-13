/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j;

import io.helidon.builder.api.Prototype;

final class McpClientSupport {

    private McpClientSupport() {
    }

    static final class McpClientDecorator implements Prototype.BuilderDecorator<McpClientConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(McpClientConfig.BuilderBase<?, ?> target) {
            /*
            Backward compatibility for deprecated methods.
             */
            if (target.uri().isPresent() && target.sseUri().isEmpty()) {
                target.sseUri(target.uri().get());
            }
            if (target.uri().isEmpty() && target.sseUri().isPresent()) {
                target.uri(target.sseUri().get());
            }
        }
    }
}
