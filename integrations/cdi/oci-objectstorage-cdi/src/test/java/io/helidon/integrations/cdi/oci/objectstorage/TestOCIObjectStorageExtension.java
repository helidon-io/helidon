/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.oci.objectstorage;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.ListBucketsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@ApplicationScoped
public class TestOCIObjectStorageExtension {

  private SeContainer cdiContainer;

  TestOCIObjectStorageExtension() {
    super();
  }

  @BeforeEach
  void startCdiContainer() {
    assumeFalse(System.getProperty("oci.objectstorage.compartmentId", "").isEmpty());
    assumeFalse(System.getProperty("oci.objectstorage.namespaceName", "").isEmpty());
    assumeFalse(System.getProperty("oci.objectstorage.region", "").isEmpty());
    final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
    assertNotNull(initializer);
    this.cdiContainer = initializer.initialize();
  }
  
  @AfterEach
  void shutDownCdiContainer() {
    if (this.cdiContainer != null) {
      this.cdiContainer.close();
    }
  }

  private void onStartup(@Observes @Initialized(ApplicationScoped.class) final Object event,
                         final ObjectStorage client) {
    assertNotNull(client);
    assertNotNull(client.toString()); // dereference the proxy
    final ListBucketsRequest request =
      ListBucketsRequest.builder()
      .compartmentId(System.getProperty("oci.objectstorage.compartmentId"))
      .namespaceName(System.getProperty("oci.objectstorage.namespaceName"))
      .build();
    client.listBuckets(request);
  }

  private void configure(@Observes final ObjectStorageClient.Builder builder) {
    assertNotNull(builder);
  }
  
  @Test
  void testSpike() {

  }
  
}
