# Running the LRA TCKs

1. Due to a bug in the LRA TCKs, there is a need to clone their repo and compile a version of them
manually.

```git clone  https://github.com/eclipse/microprofile-lra.git```

Apply diff below:

```
diff --git a/tck/src/main/java/org/eclipse/microprofile/lra/tck/participant/api/AfterLRAListener.java b/tck/src/main/java/org/eclipse/microprofile/lra/tck/participant/api/AfterLRAListener.java
index ff690aa..49df32b 100644
--- a/tck/src/main/java/org/eclipse/microprofile/lra/tck/participant/api/AfterLRAListener.java
+++ b/tck/src/main/java/org/eclipse/microprofile/lra/tck/participant/api/AfterLRAListener.java
@@ -19,11 +19,6 @@
  *******************************************************************************/
 package org.eclipse.microprofile.lra.tck.participant.api;
 
-import org.eclipse.microprofile.lra.annotation.LRAStatus;
-import org.eclipse.microprofile.lra.annotation.AfterLRA;
-import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
-import org.eclipse.microprofile.lra.tck.service.LRATestService;
-
 import javax.enterprise.context.ApplicationScoped;
 import javax.inject.Inject;
 import javax.ws.rs.HeaderParam;
@@ -32,6 +27,11 @@ import javax.ws.rs.Path;
 import javax.ws.rs.core.Response;
 import java.net.URI;
 
+import org.eclipse.microprofile.lra.annotation.AfterLRA;
+import org.eclipse.microprofile.lra.annotation.LRAStatus;
+import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
+import org.eclipse.microprofile.lra.tck.service.LRATestService;
+
 import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
 import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_ENDED_CONTEXT_HEADER;
 import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;
@@ -63,7 +63,8 @@ public class AfterLRAListener extends ResourceParent {
     @PUT
     @Path(AFTER_LRA)
     @AfterLRA // this method will be called when the LRA associated with the method activityWithLRA finishes
-    public Response afterEnd(@HeaderParam(LRA_HTTP_ENDED_CONTEXT_HEADER) URI lraId, LRAStatus status) {
+    @Override
+    public Response afterLRA(@HeaderParam(LRA_HTTP_ENDED_CONTEXT_HEADER) URI lraId, LRAStatus status) {
         return lraTestService.processAfterLRAInfo(lraId, status, AfterLRAListener.class,
             AFTER_LRA_LISTENER_PATH + AFTER_LRA);
     }
```

2. Built the TCKs and update pom.xml in this directory to point to the new artifact

3. Run the coordinator from the `lra` directory and make sure it runs on port 8070
```java -Dlra.logging.enabled=false -jar coordinator/target/lra-coordinator-helidon-2.3.0-SNAPSHOT.jar```

4. Run the TCKs by typing ```mvn test```