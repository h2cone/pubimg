# publib

Publish image from source registry to target registries.

## Usage

```shell
Usage: pubimg [-hV] -n=<name> -ns=<namespace> [-prd=<delay>] [-rp=<regConf>]
              -s=<source> [<targets>...]
Publish image from source registry to target registries
      [<targets>...]    Target registry aliases
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

foobar.host=harbor.foobar.com
foobar.username=foobar
foobar.password=foobar
```

The file contains the credentials for the source and target registries, where `foo`, `bar`, and `foobar` are the aliases
of the registries.

2. Publish image `dev/qux:latest` from `harbor.foobar.com` to `harbor.foo.com` and `harbor.bar.com`:

```shell
pubimg -rp='./registry.properties' -n='dev/qux:latest' -s=foobar -ns=test foo bar
```

After all tasks are completed, you can find the image in `harbor.foo.com/test/qux:latest`
and `harbor.bar.com/test/qux:latest`.

## How to build native executable

### Prerequisites

* [GraalVM](https://www.graalvm.org/)
* [Apache Maven](https://maven.apache.org/)

### Building

```shell
mvn -Pnative native:compile
```
