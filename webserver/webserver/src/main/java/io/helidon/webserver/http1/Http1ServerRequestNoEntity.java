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

package io.helidon.webserver.http1;

import java.io.InputStream;
import java.util.function.UnaryOperator;

import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http.HttpSecurity;

class Http1ServerRequestNoEntity extends Http1ServerRequest {
    Http1ServerRequestNoEntity(ConnectionContext ctx,
                               HttpSecurity security, HttpPrologue prologue,
                               Headers headers,
                               int requestId) {
        super(ctx, security, prologue, headers, requestId);
    }

    @Override
    public void reset() {
    }

    @Override
    public ReadableEntity content() {
        return ReadableEntityBase.empty();
    }

    @Override
    public boolean continueSent() {
        // we do not have a request entity, so we did not sent expect continue, and we do not need to drain it
        return false;
    }

    @Override
    public void streamFilter(UnaryOperator<InputStream> filterFunction) {
    }

    @Override
    public String toString() {
        return super.toString() + " without entity";
    }
}
