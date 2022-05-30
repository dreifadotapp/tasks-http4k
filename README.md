# The "Tasks-Http" framework

[![Circle CI](https://circleci.com/gh/dreifadotapp/tasks-http4k.svg?style=shield)](https://circleci.com/gh/dreifadotapp/tasks-http4k)
[![Licence Status](https://img.shields.io/github/license/dreifadotapp/tasks-http4k)](https://github.com/dreifadotapp/tasks-http4k/blob/master/licence.txt)

## What it does

[Tasks](https://github.com/dreifadotapp/tasks) remoting using the [http4k](https://www.http4k.org/) toolkit.

Deployed to jitpack. See releases for version details. To include in your project, if using gradle:

```groovy
//add jitpack repo 
maven { url "https://jitpack.io" }

//include the dependency (note this pulls in both client and server)
implementation 'com.github.dreifadotapp:tasks-http4k:<version>'

// to pull in just client or server 
implementation 'com.github.dreifadotapp.tasks-http4k:server:<version>'

```
JitPack build status is at:

* https://jitpack.io/com/github/dreifadotapp/tasks-http4k/$releaseTag/build.log
* https://jitpack.io/com/github/dreifadotapp/tasks-http4k/server/$releaseTag/build.log



## Dependencies

As with everything in [Dreifa dot App](https://driefa.app), this library has minimal dependencies.

* Kotlin 1.5
* Java 11
* The [http4k](https://www.http4k.org/) toolkit
* The [Tasks](https://github.com/dreifadotapp/tasks#readme) framework
* The object [Registry](https://github.com/dreifadotapp/registry#readme)
* The [Commons](https://github.com/dreifadotapp/commons#readme) module
* The [Open Telemetry](https://github.com/dreifadotapp/open-telemetry#readme) module

## Testing with Open Telemetry 

Open Telemetry support is being added and certain test cases will produce telemetry. The telemetry will be captured 
by Zipkin if running locally. The easiest way to run Zipkin is via Docker.

```bash
docker run --rm -it --name zipkin \
  -p 9411:9411 -d \
  openzipkin/zipkin:latest
```

Then open the [Zipkin UI](http://localhost:9411/zipkin/).

Each test run is tagged with a unique booking ref style test id. To filter on a specific 
id edit open [this link](http://localhost:9411/zipkin/?annotationQuery=dreifa.correlation.testId%3Datestid) and 
edit `atestid` 


## Next Steps

More on building and using Tasks is [here](./docs/tasks.md)



