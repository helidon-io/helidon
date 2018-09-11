Streaming Example
=================

The **Streaming NIO** example.

Uses NIO and data buffers to show the implementation of a simple streaming service. Files can be 
uploaded and downloaded in a streaming fashion using `Subscriber<DataChunk>` and 
`Producer<DataChunk>`. As a result, service runs in constant space instead of proportional
to the size of the file being uploaded or downloaded.
    
Running the example
-------------------

Running this example requires:

 * To have checked out the Java sources of this Maven module locally
 * JDK 8
 * Maven 3 
 * Curl

Execute as follows:

    mvn clean install exec:java 
    curl --data-binary "@target/classes/large-file.bin" http://localhost:8080/upload
    curl http://localhost:8080/download