To generate a project containing the types which need to be mocked--in case of changes in the upstream generator:

1. Download the generator: see [these instructions](https://github.com/OpenAPITools/openapi-generator?tab=readme-ov-file#13---download-jar).
2. `mkdir seServer`
3. `cd seServer`
4. ```java
   java -jar {downloadLocation}/openapi-generator-cli.jar generate \
   -g java-helidon-server \
   --library se \
   -i ~/mic/j4c/helidon/docs/src/main/resources/petstorex.yaml \
   -p useAbstractClass=true \
   --helidonVersion x.y.z
   ```
   where `x.y.z` is the version of Helidon you are working with.
5. `mkdir ../seClient`
6. `cd ../seClient`
7. ```java
   java -jar {downloadLocation}/openapi-generator-cli.jar generate \
   -g java-helidon-client \
   --library se \
   -i ~/mic/j4c/helidon/docs/src/main/resources/petstorex.yaml \
      --helidonVersion x.y.z
   ```
   where `x.y.z` is the version of Helidon you are working with.

   


