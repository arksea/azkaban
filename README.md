Azkaban2 [![Build Status](http://img.shields.io/travis/azkaban/azkaban.svg?style=flat)](https://travis-ci.org/azkaban/azkaban)
========

Building from Source
--------------------

To build Azkaban packages from source, run:

```
./gradlew distTar
```

The above command builds all Azkaban packages and packages them into GZipped Tar archives. To build Zip archives, run:

```
./gradlew distZip
```

Documentation
-------------

For Azkaban documentation, please go to [Azkaban Project Site](http://azkaban.github.io). The source code for the documentation is in the [gh-pages branch](https://github.com/azkaban/azkaban/tree/gh-pages).

For help, please visit the Azkaban Google Group: [Azkaban Group](https://groups.google.com/forum/?fromgroups#!forum/azkaban-dev)

Add Subproject: azkaban-flowlink
--------------
1、azkaban-flowlink allow a flow to depended other flows, simple add config at job file:
fronting-flows=testprj1:flow-aaa,testprj2:flow-bbb:sameday(),testprj3:flow-ccc:samehour()

2、You can add arg 'skip-following-flow' to skip following flows when execute the flow
