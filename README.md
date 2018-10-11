CatchUp
=======

This is a fork of hzsweers/CatchUp for the purpose of measuring and improving Kotlin vs Java build performance. This was forked from git revision 48a418d of the original project, which was the last revision where the project was pure Java.

## How to use
- Install the [Gradle Profiler](https://github.com/gradle/gradle-profiler).
- `git clone <project_path>`
- `gradle-profiler --benchmark --project-dir . --scenario-file performance.scenarios incremental_build --output-dir ./profile-out-incremental_build`
