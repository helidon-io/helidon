/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.jersey;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import io.opentracing.SpanContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The JerseyExampleResource.
 */
@Path("first")
public class JerseyExampleResource {

    static final int LARGE_DATA_SIZE_BYTES = 1_560_000;

    /** Tests that use this field are expected to synchronize on the
     * {@link JerseyExampleResource#getClass()} object.
     */
    static volatile Throwable streamException;

    @Inject
    private ServerRequest request;

    @Inject
    private ServerResponse response;

    @Inject @Named(JerseySupport.REQUEST_SPAN_CONTEXT)
    private SpanContext spanContext;

    @GET
    @Path("injection")
    public Response webServerInjection() {
        return Response.ok("request=" + request.getClass().getName()
                                   + "\nresponse=" + response.getClass().getName()
                                   + "\nspanContext=" + (null == spanContext ? null : spanContext.getClass().getName()))
                .build();
    }

    @GET
    @Path("headers")
    public Response headers(@Context HttpHeaders headers, @QueryParam("header") String header) {
        return Response.ok("headers=" + headers.getRequestHeader(header).stream().collect(Collectors.joining(",")))
                       .build();
    }

    @GET
    @Path("onlyget")
    public Response onlyGet() {
        return Response.accepted("Only get method!").build();
    }

    @GET
    @Path("hello")
    public Response hello() {
        return Response.accepted("Hello!").build();
    }

    @GET
    @Path("longhello")
    public Response longHello() {

        return Response.accepted("Hello Long: " + JerseySupportTest.longData(LARGE_DATA_SIZE_BYTES) + "!").build();
    }

    @GET
    @Path("noentity")
    public Response noEntity() {
        return Response.ok().build();
    }

    @GET
    @Path("error/noentity")
    public Response errorNoEntity() {
        return Response.status(543).build();
    }

    @GET
    @Path("error/entity")
    public Response errorEntity() {
        return Response.status(543).entity("error-entity").build();
    }

    @GET
    @Path("error/thrown/noentity")
    public Response errorThrownNoEntity() {
        throw new WebApplicationException(543);
    }

    @GET
    @Path("error/thrown/entity")
    public Response errorThrownEntity() {
        throw new WebApplicationException(Response.status(543).entity("error-entity").build());
    }

    @GET
    @Path("error/thrown/error")
    public Response errorThrownError() {
        throw new Error("This is an error");
    }

    @GET
    @Path("error/thrown/unhandled")
    public Response errorThrownUnhandled() {
        throw new RuntimeException("unhandled-exception-error");
    }

    @POST
    @Path("stream")
    public Response checkSequenceStream(InputStream inputStream, @QueryParam("length") int length) throws IOException {

        String content = new String(inputStream.readAllBytes());

        try {
            assertEquals(JerseySupportTest.longData(length).toString(), content);
        } catch (Throwable e) {
            streamException = e;
            throw new IllegalStateException("NOT EQUALS");
        }
        return Response.accepted("OK").build();
    }

    @POST
    @Path("checksequence")
    public Response checkSequence(String content, @QueryParam("length") int length) {
        if (!JerseySupportTest.longData(length).toString().equals(content)) {
            throw new IllegalStateException("NOT EQUALS");
        }
        return Response.accepted("OK").build();
    }

    @POST
    @Path("hello")
    public Response hello(String content) {
        return Response.accepted("Hello: " + content + "!").build();
    }

    @POST
    @Path("content")
    public Response content(String content) {
        return Response.accepted(content).build();
    }

    @GET
    @Path("query")
    public Response query(@QueryParam("a") String a, @QueryParam("b") String b) {
        return Response.accepted("a='" + a + "';b='" + b + "'").build();
    }

    @GET
    @Path("path/{num}")
    public Response path(@PathParam("num") String num) {
        return Response.accepted("num=" + num).build();
    }

    @GET
    @Path("requestUri")
    public String getRequestUri(@Context UriInfo uriInfo) {
        return uriInfo.getRequestUri().getPath();
    }

    @GET
    @Path("encoding/{id}")
    public String pathEncoding1(@PathParam("id") String param) {
        return param;
    }

    @Path("encoding/{id:[^/_]*}/done")
    @GET
    public String pathEncoding2(@PathParam("id") String param) {
        return param;
    }

    @DELETE
    @Path("notfound")
    public Response deleteNotFound(@Context UriInfo uriInfo) {
    	throw new WebApplicationException(Response.status(404).entity("Not Found").build());
    }

    @GET
    @Path("/streamingOutput")
    @Produces("application/stream+json")
    public StreamingOutput getHelloOutputStream() {
        return out -> {
                try {
                    out.write(("{ value: \"first\" }\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Thread.sleep(500);     // wait before sending next chunk
                    out.write(("{ value: \"second\" }\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
        };
    }
}
