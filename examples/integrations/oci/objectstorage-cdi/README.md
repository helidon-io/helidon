The object storage (CDI) example. 

The example requires OCI config in some default place like ``.oci/config``
Also properties from the ``src/main/resources/application.yaml`` shall be configured. 
Like ``oci.objectstorage.bucketName``

Build and run the example by 
```shell
mvn package
java -jar ./target/helidon-examples-integrations-oci-objectstorage-cdi.jar
```  
