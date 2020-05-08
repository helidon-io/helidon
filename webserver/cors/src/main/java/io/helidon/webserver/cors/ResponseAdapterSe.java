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
 *
 */
package io.helidon.webserver.cors;

import io.helidon.common.http.Http;
import io.helidon.webserver.ServerResponse;

/**
 * SE implementation of {@link CorsSupportBase.ResponseAdapter}.
 */
class ResponseAdapterSe implements CorsSupportBase.ResponseAdapter<ServerResponse> {

    private final ServerResponse serverResponse;

    ResponseAdapterSe(ServerResponse serverResponse) {
        this.serverResponse = serverResponse;
    }

    @Override
    public CorsSupportBase.ResponseAdapter<ServerResponse> header(String key, String value) {
        serverResponse.headers().add(key, value);
        return this;
    }

    @Override
    public CorsSupportBase.ResponseAdapter<ServerResponse> header(String key, Object value) {
        serverResponse.headers().add(key, value.toString());
        return this;
    }

    @Override
    public ServerResponse forbidden(String message) {
        serverResponse.status(Http.ResponseStatus.create(Http.Status.FORBIDDEN_403.code(), message));
        return serverResponse;
    }

    @Override
    public ServerResponse ok() {
        serverResponse.status(Http.Status.OK_200.code());
        return serverResponse;
    }

    @Override
    public int status() {
        return serverResponse.status().code();
    }
}
