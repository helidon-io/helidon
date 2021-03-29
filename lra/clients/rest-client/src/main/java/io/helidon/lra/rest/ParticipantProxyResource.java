/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.lra.rest;


import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;

@ApplicationScoped
@Path(ParticipantProxyResource.LRA_PROXY_PATH)
public class ParticipantProxyResource {
    static final String LRA_PROXY_PATH = "lraproxy";
    @Inject
    private ProxyService proxyService;

    @Path("{lraId}/{pId}/complete")
    @PUT
    public Response complete(@PathParam("lraId")String lraId,
                         @PathParam("pId")String participantId,
                         String participantData) throws URISyntaxException, UnsupportedEncodingException {
        return proxyService.notifyParticipant(toURI(lraId, true), participantId, participantData, false);
    }

    @Path("{lraId}/{pId}/compensate")
    @PUT
    public Response compensate(@PathParam("lraId")String lraId,
                               @PathParam("pId")String participantId,
                               String participantData) throws URISyntaxException, UnsupportedEncodingException {
        return proxyService.notifyParticipant(toURI(lraId, true), participantId, participantData, true);
    }

    @Path("{lraId}/{pId}")
    @DELETE
    public void forget(@PathParam("lraId")String lraId,
                       @PathParam("pId")String participantId) throws URISyntaxException, UnsupportedEncodingException {
        proxyService.notifyForget(toURI(lraId, true), participantId);
    }

    @Path("{lraId}/{pId}")
    @GET
    public String status(@PathParam("lraId")String lraId,
                       @PathParam("pId")String participantId) throws UnsupportedEncodingException, Exception {
        try {
            return proxyService.getStatus(toURI(lraId, true), participantId).name();
        } catch (URISyntaxException e) {
            throw new Exception("Caller provided an invalid LRA: " + lraId, e);
        }
    }

    private URI toURI(String url, boolean decode) throws URISyntaxException, UnsupportedEncodingException {
        if (url == null) {
            return null;
        }

        if (decode) {
            url = URLDecoder.decode(url, "UTF-8");
        }

        return new URI(url);
    }
}
