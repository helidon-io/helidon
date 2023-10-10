/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.jersey.connector;

import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.BodyPartEntity;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.message.internal.ReaderWriter;
import org.junit.jupiter.api.Assertions;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@HelidonTest
@DisableDiscovery
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)

@AddBean(MultipartTest.MultipartApplication.class)
public class MultipartTest {
    private static final String ENTITY = "hello";

    public static class MultipartApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            Set<Class<?>> set = new HashSet<>();
            set.add(MultiPartFeature.class);
            set.add(MultipartResource.class);
            return set;
        }
    }

    @Path("/")
    public static class MultipartResource {
        @POST
        @Path("upload")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        public String upload(@Context HttpHeaders headers, MultiPart multiPart) throws IOException {
//            headers.getRequestHeaders().forEach((k,v) -> System.out.println(k + ":" + v));
            return ReaderWriter.readFromAsString(
                    ((BodyPartEntity) multiPart.getBodyParts().get(0).getEntity()).getInputStream(),
                    MediaType.TEXT_PLAIN_TYPE);
        }
    }

    @ParameterizedTest
    @EnumSource(value = HelidonEntity.HelidonEntityType.class)
    void testMultipart(HelidonEntity.HelidonEntityType entityType, WebTarget webTarget) {
        // For each entity type make 10 consecutive requests
        for (int i = 0; i != 10; i++) {
//                System.out.append(entityType.name()).println(i);
            MultiPart multipart = new MultiPart().bodyPart(new BodyPart().entity(ENTITY));
            multipart.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);
            try (Response r = ClientBuilder.newBuilder()
                    .property(HelidonConnector.INTERNAL_ENTITY_TYPE, entityType.name())
                    .build().target(webTarget.getUri())
                    .path("upload")
                    .register(MultiPartFeature.class)
                    .request()
                    .post(Entity.entity(multipart, multipart.getMediaType()))) {
                Assertions.assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
                Assertions.assertEquals(ENTITY, r.readEntity(String.class));
            }
        }
    }
}
