HTTP/2
----

HTTP Server socket

Threads used in HTTP/2

Server threads

1. `ServerListener` creates a *Platform thread* for each socket we listen on This thread is non-daemon and is blocked
   on `ServerSocketChannel.accept()` until a connection comes
2. `ConnectionHandler` is created and runs in a dedicated *Virtual thread*
   `ConnectionHandler` creates `ConnectionContext` instance, configures the `SocketChannel` and starts reading data from it.
   Identifies which `ServerConnection` implementation to use and delegates processing to it. If we should stop reading data, the
   current thread should be blocked
    1. `Http2Connection` takes over for HTTP/2 requests (represents a single client connection with x streams)
3. `Http2Stream` - each runs in its own *Virtual thread* (once Headers are received)

Socket operations

- Socket reads are happening on the `ConnectionHandler` thread
    - blocking any stream handler interested in reading data
- Socket writes are happening on either `ConnectionHanlder` thread (writes initiated for connection (stream 0x0))
  OR on `Http2Stream` 