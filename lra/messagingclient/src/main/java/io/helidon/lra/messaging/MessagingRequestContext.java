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
package io.helidon.lra.messaging;


import javax.ws.rs.core.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MessagingRequestContext {
    Map properties = new HashMap<>();
    MultivaluedMap<String, String> multivaluedMap = new MultivaluedHashMap<String, String>();
    public UriInfo uriInfo;

//    Object lraContext = MessagingRequestContext.getProperty(LRA_HTTP_CONTEXT_HEADER);
//    ArrayList<Progress> progress = cast(requestContext.getProperty(ABORT_WITH_PROP));
//    Object suspendedLRA = requestContext.getProperty(SUSPENDED_LRA_PROP);
//    URI toClose = (URI) requestContext.getProperty(TERMINAL_LRA_PROP);
//    Response.Status.Family[] cancel0nFamily = (Response.Status.Family[]) requestContext.getProperty(CANCEL_ON_FAMILY_PROP);
//    Response.Status[] cancel0n = (Response.Status[]) requestContext.getProperty(CANCEL_ON_PROP);
    // LRA_HTTP_CONTEXT_HEADER, ABORT_WITH_PROP, SUSPENDED_LRA_PROP, TERMINAL_LRA_PROP, CANCEL_ON_FAMILY_PROP, CANCEL_ON_PROP
    Object getProperty(String var1) {
        return properties.get(var1);
    }

//                    MessagingRequestContext.setProperty(CANCEL_ON_FAMILY_PROP, cancel0nFamily);
//                MessagingRequestContext.setProperty(CANCEL_ON_PROP, cancel0n);
//                MessagingRequestContext.setProperty(SUSPENDED_LRA_PROP, incommingLRA);
//                MessagingRequestContext.setProperty(SUSPENDED_LRA_PROP, suspendedLRA);
//            MessagingRequestContext.setProperty(TERMINAL_LRA_PROP, lraId);
//                MessagingRequestContext.setProperty(SUSPENDED_LRA_PROP, incommingLRA);
//            MessagingRequestContext.setProperty(NEW_LRA_PROP, newLRA);
//    abortWith(MessagingRequestContext, String, int, String, Collection<Progress>)
//        MessagingRequestContext.setProperty(ABORT_WITH_PROP, reasons);
    void setProperty(String var1, Object var2){
        properties.put(var1, var2);
    }

    //SUSPENDED_LRA_PROP
    void removeProperty(String var1){
        properties.remove(var1);
    }

//                            resourceInfo.getResourceClass(), MessagingRequestContext.getUriInfo(), timeout);
//    URI baseUri = MessagingRequestContext.getUriInfo().getBaseUri();
//    Map<String, String> terminateURIs = NarayanaLRAClient.getTerminationUris(resourceInfo.getResourceClass(), MessagingRequestContext.getUriInfo(), timeout);
    UriInfo getUriInfo(){
        return uriInfo;
    }

//    MultivaluedMap<String, String> headers = MessagingRequestContext.getHeaders();
//                            Current.getLast(requestContext.getHeaders().get(LRA_HTTP_CONTEXT_HEADER)))) {
//        requestContext.getHeaders().remove(LRA_HTTP_CONTEXT_HEADER);
//        requestContext.getHeaders().remove(LRA_HTTP_CONTEXT_HEADER);
//        Current.getLast(requestContext.getHeaders().get(LRA_HTTP_CONTEXT_HEADER)))) {
//            requestContext.getHeaders().remove(LRA_HTTP_CONTEXT_HEADER);
    MultivaluedMap<String, String> getHeaders(){
        return multivaluedMap;
    }

//    MessagingRequestContext.abortWith(Response.status(statusCode).build());
    void abortWith(Response var1){
    }

}
