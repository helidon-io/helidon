WebServer Jersey Application Example
=====================

An example of **Jersey** integration into the **Web Server**.

This is just a simple Hello World example. A user can start the application using the `WebServerJerseyMain` class
and `GET` the `Hello World!` response by accessing `http://localhost:8080/jersey/hello`.

Running the example
-------------------

Running this example requires 
 * To have checked out the Java sources of this Maven module locally
 * JDK 9
 * Maven 3 
 * CURL

Execute from this directory (where this `README.md` file is located):

    mvn exec:java -pl examples/jersey
    curl http://localhost:8080/jersey/hello
