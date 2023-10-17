/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.server;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.MatrixParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

@HelidonTest
@AddBean(EntityMatrixTest.TestResource.class)
class EntityMatrixTest {

    private static final String FOO = "foo";
    private static final String BAR = "bar";

    @Test
    void defaultValueField(WebTarget target) {
        String getResponse = target.path("/field").request().get(String.class);
        assertThat(getResponse, is(equalTo(FOO)));
    }

    @Test
    void customValueField(WebTarget target) {
        String getResponse = target.path("/field;matrix=" + BAR).request().get(String.class);
        assertThat(getResponse, is(equalTo(BAR)));
    }

    @Test
    void defaultValueParam(WebTarget target) {
        String getResponse = target.path("/param").request().get(String.class);
        assertThat(getResponse, is(equalTo(FOO)));
    }

    @Test
    void customValueParam(WebTarget target) {
        String getResponse = target.path("/param;matrix=" + BAR).request().get(String.class);
        assertThat(getResponse, is(equalTo(BAR)));
    }

    @Test
    void entityMatrix(WebTarget target) {
        String getResponse = target.path("/entitymatrix;param=" + BAR).request().get(String.class);
        assertThat(getResponse, is(equalTo(BAR)));
    }

    @Path("/")
    @RequestScoped
    public static class TestResource {

        @BeanParam
        MatrixBeanParamEntity entity;

        @GET
        @Path("field")
        @Produces(MediaType.TEXT_PLAIN)
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        public String field() {
            return entity.field.value;
        }

        @GET
        @Path("param")
        @Produces(MediaType.TEXT_PLAIN)
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        public String param(@BeanParam MatrixBeanParamEntity entity) {
            return entity.field.value;
        }

        @GET
        @Path("entitymatrix")
        public String entitymatrix(
            @MatrixParam("param") ParamEntityWithFromStringAndValueOf param) {
          return param.getValue();
        }
    }

    public static class MatrixBeanParamEntity {

        @DefaultValue(FOO)
        @MatrixParam("matrix")
        public FieldStr field;

    }

    public static class FieldStr {

        private final String value;

        public FieldStr(String value) {
            this.value = value;
        }

    }

    public static class ParamEntityWithFromStringAndValueOf {

        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public static ParamEntityWithFromStringAndValueOf valueOf(String arg) {
            ParamEntityWithFromStringAndValueOf newEntity = new ParamEntityWithFromStringAndValueOf();
            newEntity.value = arg;
            return newEntity;
        }

        public static ParamEntityWithFromStringAndValueOf fromString(String arg) {
            ParamEntityWithFromStringAndValueOf newEntity = new ParamEntityWithFromStringAndValueOf();
            newEntity.value = arg;
            return newEntity;
        }
    }

}
