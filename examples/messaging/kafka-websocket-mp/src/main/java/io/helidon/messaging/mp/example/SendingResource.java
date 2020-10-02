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

package io.helidon.messaging.mp.example;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Expose send method for publishing to messaging.
 */
@Path("rest/messages")
@RequestScoped
public class SendingResource {
    private final MsgProcessingBean msgBean;

    /**
     * Constructor injection of field values.
     *
     * @param msgBean Messaging example bean
     */
    @Inject
    public SendingResource(MsgProcessingBean msgBean) {
        this.msgBean = msgBean;
    }


    /**
     * Send message through Messaging to Kafka.
     *
     * @param msg message to process
     */
    @Path("/send/{msg}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public void getSend(@PathParam("msg") String msg) {
        msgBean.process(msg);
    }
}
