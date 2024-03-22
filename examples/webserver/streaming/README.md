# Streaming Example

This application is an example of a very simple streaming service. It leverages the
fact that Helidon uses virtual threads to perform simple input/output stream blocking
operations in the endpoint handlers. As a result, this service runs in constant space instead
of proportional to the size of the file being uploaded or downloaded.

There are two endpoints:

- `upload` : uploads a file to the service
- `download` : downloads the previously uploaded file

## Build and run

```shell
mvn package
java -jar target/helidon-examples-webserver-streaming.jar
```

Upload a file and download it back with `curl`:
```shell
curl --data-binary "@target/classes/large-file.bin" http://localhost:8080/upload
curl http://localhost:8080/download
```
