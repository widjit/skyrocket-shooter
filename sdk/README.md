# ATAK SDK jars (git-ignored, never packaged)

| ATAK target | Put `main.jar` at | Build |
|---|---|---|
| 4.6.0 (public GitHub SDK) | `sdk/main.jar` or `sdk/4.6.0/main.jar` | `./gradlew assembleCivDebug` |
| 5.8.0 (tak.gov SDK) | `sdk/5.8.0/main.jar` | `./gradlew assembleCivRelease -PatakVersion=5.8.0` |
| any | anywhere | `-PatakSdkJar=/path/main.jar -PatakVersion=X.Y.Z` |

If no SDK is found for a version other than 4.6.0, the build fails. `-PallowStubs` compiles against the stubs to check the code only; the resulting APK does not run.
