URI
----
URI abstraction as required for routing and request handling.

# URI Encoding
URI has a limited set of allowed characters, with some characters having special meaning
(such as `#`, `/`, `;`, `?`), also the URI is expected to be ASCII string.

Special characters (and UTF-8) can be used through encoding (using the `%##` format).
For example space character is encoded into `%20`.

We support encoding of paths, with access to raw (encoded) and decoded values.

Usage in components:
- Path (`UriPath`)
  - raw path - encoded path including path parameters
  - raw path no params - encoded path without path parameters
  - decoded path - decoded path without path parameters
  - decoded path parameter by name
  - path segments (may have path parameters associated) - ordered sequence
      of path parts (each segment is a section between `/`)
- Query (`UriQuery`)
  - raw query - full encoded query
  - raw parameter by name
  - decoded parameter by name
- Fragment (`UriFragment`)
  - raw fragment
  - decoded fragment

Method names - if a method returns raw (encoded) value,
it will always be prefixed with the word `raw`. All other 
methods are returning decoded values. 

There must be factory methods to create an instance from both encoded and decoded value.

# URI Components
## Scheme
Scheme is not used by this module - see HTTP.

## Authority
Authority is not used by this module.
Authority is `userinfo@server:port`, where both `userinfo@` and `port` are optional.

## Path
Path is telling us which resource to find (hierarchical location).

Path supports (and resolves) path parameters.
Path parameters are separated with a semicolon from path segments, values are separated
with an equals and parameter values may be separated by a comma.

Example: `/hello;version=1.0;supper/world;version=2.0;colors=blue,green,brown`
Such a path will be resolved into:
- path: `/hello`
- parameter `version=1.0,2.0` (2 values)
- parameter `colors=blue,green,brow` (3 values)
- parameter `supper=` (1 value, empty string)

Path parameters are resolved in order (from left to right) and values are appended
Path segments contain only parameters valid for them (expensive parsing!)

## Query
Query provides us with additional parameters to process the request.

Example: `/hello/world?lang=CZ`

Such a URI will have query:
- parameter: `lang=CZ`

## Fragment
Fragment provides a reference to a fragment of a page, such as a header (usually not used for queries to server).

Example: `/hello/world#message`

Fragment is `message`.