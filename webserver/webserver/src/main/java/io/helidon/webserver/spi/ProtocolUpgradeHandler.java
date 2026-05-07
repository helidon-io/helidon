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

package io.helidon.webserver.spi;

import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

/**
 * Handler that can enforce route policy for a protocol upgrade request without invoking
 * the ordinary HTTP endpoint handler.
 * <p>
 * Implementations must use the same contract as ordinary HTTP handlers: call
 * {@link ServerRequest#next()} to allow processing to continue, or send a
 * response using {@link ServerResponse} to reject the upgrade.
 *
 * @deprecated internal SPI for Helidon 3.x protocol upgrade handling
 */
@Deprecated(since = "3.0.0")
public interface ProtocolUpgradeHandler extends Handler {
}
