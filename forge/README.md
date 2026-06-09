# Music Machine - Forge

This directory contains the **Forge** implementation of the Music Machine mod.

## Prerequisites

- **Java 17** (Ensure your `JAVA_HOME` environment variable is correctly set)
- **Forge Mod Loader**

## Setup & Build Instructions

1. **Clone the repository** and navigate to the `forge` folder:
   ```bash
   cd forge
   ```

2. **Build the mod**:
   We use the Gradle Wrapper to ensure the correct version of Gradle is used. Run the following command in your terminal:
   - On Windows: `.\gradlew.bat clean build`
   - On Mac/Linux: `./gradlew clean build`

3. **Locate the compiled `.jar`**:
   Once the build completes successfully, the compiled jar file will be located in the `build/libs/` directory.

## Testing Locally

If you are developing features and want to run a local test instance of Minecraft Forge from the command line:
- Run `.\gradlew.bat runClient` (Windows) or `./gradlew runClient` (Mac/Linux).
- This will launch a sandboxed Minecraft client with your local mod loaded.
