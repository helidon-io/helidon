/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.tests.functional.context.hello;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * HelloResource class.
 */
@Path("/")
public class HelloResource {

    private HelloBean helloBean;

    /**
     * Constructor.
     *
     * @param helloBean Injected bean.
     */
    @Inject
    public HelloResource(HelloBean helloBean) {
        this.helloBean = helloBean;
    }

    /**
     * Method getHello().
     *
     * @return Hello string.
     */
    @GET
    @Path("/hello")
    public String getHello() {
        return helloBean.getHello();
    }

    /**
     * Method getHelloTimeout().
     *
     * @return Hello string.
     */
    @GET
    @Path("/helloTimeout")
    public String getHelloTimeout() {
        return helloBean.getHelloTimeout();
    }

    /**
     * Method getHelloAsync().
     *
     * @return Hello string.
     * @throws Exception If an error occurs.
     */
    @GET
    @Path("/helloAsync")
    public String getHelloAsync() throws Exception {
        return helloBean.getHelloAsync().toCompletableFuture().get();
    }
}
