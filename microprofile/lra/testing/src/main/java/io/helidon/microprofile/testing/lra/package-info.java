/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

/**
 * Test LRA coordinator.
 * Allows simplified testing of LRA enabled JAX-RS resources.
 *
 * <pre>{@code
 * @HelidonTest
 * @AddBean(TestLraCoordinator.class)
 * @Path("/test")
 * public class LraTest {
 *
 *     private final WebTarget target;
 *     private final Set<String> completedLras;
 *     private final Set<String> cancelledLras;
 *     private final TestLraCoordinator coordinator;
 *
 *     @Inject
 *     public LraTest(WebTarget target,
 *                    TestLraCoordinator coordinator) {
 *         this.target = target;
 *         this.coordinator = coordinator;
 *         this.completedLras = new CopyOnWriteArraySet<>();
 *         this.cancelledLras = new CopyOnWriteArraySet<>();
 *     }
 *
 *     @PUT
 *     @Path("/withdraw")
 *     @LRA(LRA.Type.REQUIRES_NEW)
 *     public Response withdraw(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) Optional<URI> lraId, String content) {
 *         ...
 *         return Response.ok().build();
 *     }
 *
 *     @Complete
 *     public void complete(URI lraId) {
 *         completedLras.add(lraId.toString());
 *     }
 *
 *     @Compensate
 *     public void rollback(URI lraId) {
 *         cancelledLras.add(lraId.toString());
 *     }
 *
 *     @Test
 *     public void testLra() {
 *         try (Response res = target
 *                 .path("/test/withdraw")
 *                 .request()
 *                 .put(Entity.entity("test", MediaType.TEXT_PLAIN_TYPE))) {
 *             assertThat(res.getStatus(), is(200));
 *             String lraId = res.getHeaderString(LRA.LRA_HTTP_CONTEXT_HEADER);
 *             Lra lra = coordinator.lra(lraId);
 *             assertThat(lra.status(), is(LRAStatus.Closed));
 *             assertThat(completedLras, contains(lraId));
 *         }
 *     }
 * }
 * }</pre>
 */
package io.helidon.microprofile.testing.lra;
