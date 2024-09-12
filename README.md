# publib

Publish image from source registry to target registry.

## Usage

```shell
Usage: pubimg [-hV] -n=<name> -ns=<namespace> [-prd=<delay>] [-rp=<regConf>]
              -s=<source> <target>
Publish image from source registry to target registry
      <target>          Target registry aliase
  -h, --help            Show this help message and exit.
  -n=<name>             Source name
      -ns=<namespace>   Target namespace
      -prd=<delay>      Progress refresh delay in seconds
      -rp=<regConf>     Path to registry.properties
  -s=<source>           Source registry alias
  -V, --version         Print version information and exit.
```

## Example

1. Create a file named `registry.properties` with the following content:

```properties
foo.host=harbor.foo.com
foo.username=foo
foo.password=foo

bar.host=harbor.bar.com
bar.username=bar
bar.password=bar
```

The file contains credentials for the source and target registries, with `foo` and `bar` as their aliases.

2. Publish image `dev/qux:latest` from `harbor.foo.com` to `harbor.bar.com`:

```shell
pubimg -rp='./registry.properties' -n='dev/qux:latest' -s=foo -ns=test bar
```

After all tasks are completed, you can find the image in `harbor.bar.com/test/qux:latest`.

## How to build native executable

### Prerequisites

* [GraalVM](https://www.graalvm.org/)
* [Apache Maven](https://maven.apache.org/)

### Building

```shell
mvn -Pnative native:compile
```
