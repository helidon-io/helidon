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
 */
package io.helidon.examples.media.multipart;

import io.helidon.media.multipart.common.ReadableBodyPart;
import io.helidon.media.multipart.common.ReadableMultiPart;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Exposes an endpoint that handles multipart requests.
 */
public final class FileUploadService implements Service {

    @Override
    public void update(Routing.Rules rules) {
        rules.post("/raw", this::raw)
             .post("/buffered", this::buffered)
             .post("/stream", this::stream);
    }

    /**
     *
     * Prints the raw request payload to the console output.
     *
     * @param req server request
     * @param res server response
     */
    private void raw(ServerRequest req, ServerResponse res) {
        req.content().as(String.class).thenAccept(str -> {
            System.out.println(str);
            res.send();
        });
    }

    /**
     *
     * Reads the request payload as a buffered multi-part entity and print the content of each part to the console output.
     *
     * @param req server request
     * @param res server response
     */
    private void buffered(ServerRequest req, ServerResponse res) {
        req.content().as(ReadableMultiPart.class).thenAccept(multiPart -> {
            for (ReadableBodyPart part : multiPart.bodyParts()) {
                System.out.println("Headers: " + part.headers().toMap());
                System.out.println("Content: " + part.as(String.class));
            }
            res.send();
        });
    }

    /**
     *
     * Reads the request payload as a stream of body part entities and print the content of each part to the console output.
     *
     * @param req server request
     * @param res server response
     */
    private void stream(ServerRequest req, ServerResponse res) {
        req.content().asStream(ReadableBodyPart.class).subscribe((part) -> {
            System.out.println("Headers: " + part.headers().toMap());
            System.out.println("Content: " + part.as(String.class));
        }, (error) -> {
            res.send(error);
        }, () -> {
            res.send();
        });
    }
}
