To get the issue to manifest, use ```sbt "utilsBench/jmh:run --jvm /path/to/openj9/java"```

Pass JVM args with the command ```sbt "utilsBench/jmh:run --jvm /path/to/openj9/java -jvmArgs \"-Xarg1 -Xarg2:verbose\""```
