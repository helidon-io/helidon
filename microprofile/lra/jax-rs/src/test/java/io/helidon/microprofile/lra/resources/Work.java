/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.helidon.microprofile.lra.resources;

import java.net.URI;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.core.Response;

public enum Work {

    NOOP(uri -> Response.ok().build(), 200),
    BOOM(uri -> {
        throw new RuntimeException("BOOM!!! lraId: " + uri.toASCIIString());
    }, 500),
    INTERNAL_SERVER_ERROR(uri -> {
        return Response.serverError().
                entity(new RuntimeException("BOOM!!! lraId: " + uri.toASCIIString()))
                .build();
    }),
    DELAY(uri -> {
        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Logger.getLogger(Work.class.getName())
                    .log(Level.SEVERE, "Delayed work of lraId: " + uri.toASCIIString() + " interrupted!", e);
        }
        return Response.ok().build();
    }, 200),
    ;

    public static final String HEADER_KEY = "test-work";
    private final Integer[] expectedStatuses;

    private final Function<URI, Response> worker;

    Work(Function<URI, Response> worker, Integer... expectedStatuses) {
        this.worker = worker;
        this.expectedStatuses = expectedStatuses;
    }

    public Response doWork(URI lraId) {
        return worker.apply(lraId);
    }
    
    public Integer[] expectedResponseStatuses(){
        return expectedStatuses;
    }
}
