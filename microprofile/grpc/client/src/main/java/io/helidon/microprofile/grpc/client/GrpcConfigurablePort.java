/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.grpc.client;

/**
 * Interface implemented by all gRPC client proxies. The method {@link #channelPort} can be
 * called at runtime to override the client URI port from config. Typically used for testing.
 */
public interface GrpcConfigurablePort {

    /**
     * Name of single setter method on this interface.
     */
    String CHANNEL_PORT = "channelPort";

    /**
     * Overrides client URI port.
     *
     * @param value the new port value
     */
    void channelPort(int value);
}
