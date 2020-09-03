#GraalVM native image integration test
_____

This is a manual (for the time being) test of integration with native-image.

To run this test:

```shell script
mvn clean package -Pnative-image
./target/helidon-tests-native-image-se-1
```  

Once the native image builds and is started, run the following
 curl requests:
 
```shell script
# Should return 200 code and "file-resource-text" as entity
curl -i http://localhost:7076/static/path/resource.txt

# Should return 200 code and "classpath-resource-text" as entity
curl -i http://localhost:7076/static/classpath/resource.txt

# Should return 200 code and "jar-resource-text" as entity
curl -i http://localhost:7076/static/jar/resource.txt

# Should return 200 code and "{"message":"SE Hallo World!"}" as entity
curl -i http://localhost:7076/greet

# Should return 401 code 
curl -i http://localhost:7076/greet/john

# Should return 200 code and "{"message":"SE Hallo jack!"}" as entity
curl -i -u jack:password http://localhost:7076/greet/john

# Should return 200 code and JSON response with two health checks
curl -i http://localhost:7076/health

# Should return 200 code and JSON response with metrics
curl -i -H "Accept: application/json" http://localhost:7076/metrics

# Should return ALL TESTS PASSED! after passing all webclient tests
curl -i http://localhost:7076/wc/test

# Should return: Upgrade: websocket
curl \
    --include \
    --no-buffer \
    --header "Connection: Upgrade" \
    --header "Upgrade: websocket" \
    --header "Host: localhost:7076" \
    --header "Origin: http://localhost:7076" \
    --header "Sec-WebSocket-Key: SGVsbG8sIHdvcmxkIQ==" \
    --header "Sec-WebSocket-Version: 13" \
    http://localhost:7076/ws/messages

# Bi-directional test is possible with websocat tool
# should return 'part1 part2'
for msg in "part1" "part2" "SEND"; do echo $msg; done \
| websocat ws://127.0.0.1:7076/ws/messages
```
