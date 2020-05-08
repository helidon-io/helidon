# Streaming Example

This application uses NIO and data buffers to show the implementation of a simple streaming service.
 Files can be uploaded and downloaded in a streaming fashion using `Subscriber<DataChunk>` and 
`Producer<DataChunk>`. As a result, service runs in constant space instead of proportional
to the size of the file being uploaded or downloaded.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-webserver-streaming.jar
```

Upload a file and download it back with `curl`:
```bash
curl --data-binary "@target/classes/large-file.bin" http://localhost:8080/upload
curl http://localhost:8080/download
```
