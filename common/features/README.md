Helidon Features
-----

# Registry file format

## Version 1

The registry is stored in each module in `META-INF/helidon/feature-metadata.properties`.
The following keys are supported:

| Key    | Name             | Description                                     |
|--------|------------------|-------------------------------------------------|
| `m`    | Module           | Name of the module                              |
| `n`    | Name             | Feature name                                    |
| `d`    | Description      | Feature description                             |
| `s`    | Since            | First version that contains this feature        |
| `p`    | Path             | Path of the feature                             |
| `in`   | Flavor           | Flavor(s) that should print this feature        |
| `not`  | Not in Flavor    | Flavor(s) that this feature is not supported in |
| `aot`  | Ahead of time    | Whether ahead of time compilation is supported  |
| `aotd` | Description aot  | Description of AOT support                      |
| `i`    | Incubating       | This is an incubating feature                   |
| `pr`   | Preview          | This is a preview feature                       |
| `dep`  | Deprecated       | This feature is deprecated                      |
| `deps` | Deprecated since | First version this feature was deprecated in    |

## Version 2

The registry is stored in each module in `META-INF/helidon/feature-registry.json`.
The root element is an array, to allow merging of all features into a single file.

The format is as follows (using `//` to comment sections, not part of the format):

```json
// root is an array of modules (we always generate a single module, but this allows a combined array, i.e. when using shading
[
    {
        // version of the metadata file, defaults to 2 (and will always default to 2)
        "version": 2,
        // name of the module (required)
        "module": "io.helidon.example.feature",
        // feature name (required)
        "name": "Feature",
        // optional, defaults to feature name  
        "path": [
            "Example",
            "Feature"
        ],
        // optional, recommended  
        "description": "Description of this feature",
        // optional  
        "since": "4.2.0",
        // which flavor(s) should print this feature (optional)  
        "flavor": [
            "SE"
        ],
        // in which flavor we should warn that this feature is present on classpath (optional)   
        "invalid-flavor": [
            "MP"
        ],
        // native image restrictions (optional), defaults to fully supported 
        "aot": {
            "supported": true,
            // description (optional)
            "description": "Only supports buzz and foo"
        },
        // status of this feature, defaults to "Production", or Deprecated if deprecation is set (optional)
        // allowed values: "Production", "Preview", "Incubating", "Deprecated"
        "status": "Production",
        // deprecation information, defaults to not-deprecated (optional)
        "deprecation": {
            "deprecated": true,
            // optional
            "description": "Use foo module instead",
            // optional
            "since": "4.3.0"
        }
    }
]
```