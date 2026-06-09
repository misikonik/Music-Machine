# Music Machine - Fabric

This directory contains the **Fabric** port of the Music Machine mod.

## Prerequisites

- **Java 17** (Ensure your `JAVA_HOME` environment variable is correctly set)
- **Fabric Loader**
- **Fabric API** (Required dependency for the mod to run)

## Setup & Build Instructions

1. **Clone the repository** and navigate to the `fabric` folder:
   ```bash
   cd fabric
   ```

2. **Build the mod**:
   We use the Gradle Wrapper to ensure the correct version of Gradle is used. Run the following command in your terminal:
   - On Windows: `.\gradlew.bat clean build`
   - On Mac/Linux: `./gradlew clean build`

3. **Locate the compiled `.jar`**:
   Once the build completes successfully, the compiled jar file will be located in the `build/libs/` directory. You will want to use the standard jar (not the `-sources` or `-dev` jar).

## Troubleshooting

If you encounter issues during compilation (such as a `Gson` or `IllegalAccessException` error on Java 17), ensure you are using the precise `fabric-loom` version specified in the `build.gradle` file (`1.4-SNAPSHOT`). Newer versions of Loom may cause reflection crashes with Gson 2.9.1 on Windows Java 17 environments.
