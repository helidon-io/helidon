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

package io.helidon.webserver;

import java.util.Objects;

import io.helidon.builder.api.Prototype;
import io.helidon.webserver.spi.TransportBinding;

final class TcpTransportConfigSupport {
    private TcpTransportConfigSupport() {
    }

    static final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Whether this binding factory can bind with the current listener endpoint configuration.
         *
         * @param config configured TCP transport factory
         * @param context listener binding planning view
         * @return whether this binding factory can bind for the listener
         */
        @Prototype.PrototypeMethod
        static boolean canBind(TcpTransportConfig config, BindingPlanContext context) {
            Objects.requireNonNull(context, "context");
            return config.enabled();
        }

        /**
         * Create a TCP transport binding.
         *
         * @param config configured TCP transport factory
         * @param context logical listener context
         * @return created TCP transport binding
         */
        @Prototype.PrototypeMethod
        static TransportBinding create(TcpTransportConfig config, TransportBindingContext context) {
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(context, "context");
            return new TcpTransportBinding(context, config);
        }
    }
}
