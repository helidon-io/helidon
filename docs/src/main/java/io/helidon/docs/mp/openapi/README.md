To generate a project containing the types which need to be mocked--in case of changes in the upstream generator:

1. Download the generator: see [these instructions](https://github.com/OpenAPITools/openapi-generator?tab=readme-ov-file#13---download-jar).
2. `mkdir mpClient`
3. `cd mpClient`
4. ```java
   java -jar {downloadLocation}/openapi-generator-cli.jar generate \
   -g java-helidon-client \
   --library mp \
   -i {helidon-root}/docs/src/main/resources/petstorex.yaml \
   --helidonVersion 4
   ```
   or specify the appropriate Helidon major or exact version.

   


