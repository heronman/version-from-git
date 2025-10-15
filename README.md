# 📦 Git Version Utility for Gradle (Kotlin DSL)

This module provides a lightweight, dependency-free (except for JGit) way to derive a semantic version string directly from a Git repository.
It is designed for Gradle Kotlin DSL builds that need dynamic versioning — e.g., generating versions like `1.2.3-4-gabc1234-DIRTY-9a4bcd12`.

## 🚀 Features

* Extracts the latest annotated Git tag (e.g. v1.2.3)
* Computes commit distance from that tag (e.g. +4 commits → 1.2.3-4-gabc1234)
* Detects uncommitted changes (“dirty” state)
* Optionally generates an MD5 diff hash for dirty state
* Prints colorized logs in the console (ANSI)
* Gracefully falls back to a default version if no tags or no repo are found

### 🧩 Example Version Outputs
```
Repository state	         Example output
Tagged v1.2.3 clean          1.2.3
3 commits ahead of v1.2.3    1.2.3-3-gf7a1b23
Same but with dirty changes  1.2.3-3-gf7a1b23-DIRTY-9a4bcd12
No tag found                 0.0.0-SNAPSHOT
```

### ⚙️ Usage in build.gradle.kts
#### 1️⃣ Include the code

Place the file under your Gradle source tree, e.g.:

`buildSrc/src/main/kotlin/net/agl/gradle/GitVersion.kt`


Gradle automatically compiles everything in buildSrc and makes it available to the main build scripts.

#### 2️⃣ Use in your build script
```kotlin
import net.agl.gradle.versionFromGit

version = versionFromGit(
  gitRoot = project.rootDir.absolutePath,
  dirtyDetect = true,
  dirtyHash = true,
  commitsNo = true,
  fallbackVersion = "0.0.0-SNAPSHOT"
)
```
#### 3️⃣ Run a build

When you run any Gradle task, the version is computed dynamically:

```bash
 ./gradlew build
```

`Version calculated: 1.2.3-2-gab1c23f-DIRTY-a41b3e92`

## 🧠 How It Works
1. Open Git repository

   Uses JGit to open the repository at gitRoot.

2. Find latest tag

   Finds the latest annotated tag (refs/tags/...).
   If none is found → prints a warning and returns the fallback version.

3. Count commits since tag

   If enabled, counts commits from that tag to HEAD and appends -<N>-g<shortHash>.

4. Detect dirty state

   isDirty() checks for untracked, missing, or modified files.

   If dirtyHash is true, it computes an MD5 hash of the current diff to produce a short dirty fingerprint (-DIRTY-<hash>).

5. Output version

   Prints a colorized summary and returns the final version string.

### 🔧 Function Reference
```versionFromGit(...)
Parameter       Type              Default          Description
gitRoot         String (required)                  Path to repository root
dirtyDetect     Boolean           true             Whether to detect dirty working tree
dirtyHash       Boolean           true             Whether to compute MD5 hash of diff
commitsNo       Boolean           true             Whether to append number of commits since last tag
fallbackVersion String            "0.0.0-SNAPSHOT" Version returned if no tags or no repo found
```

### 🧪 Example Output Scenarios
* Clean tagged repo
    ```
    Version calculated: 1.0.0
    ```
* Few commits ahead
    ```
    Version calculated: 1.0.0-5-g8fa6b32
    ```

* Dirty working tree
    ```
    Version calculated: 1.0.0-5-g8fa6b32-DIRTY-3b29c0fa
    ```

* No Git repository
    ```
    No GIT repository found in [/path], falling back to default version 0.0.0-SNAPSHOT
    ```

### 🧱 Implementation Notes

* Uses Eclipse JGit (org.eclipse.jgit.*)
* ANSI color codes used for terminal output (RED, GREEN, YELLOW)
* Diff computed with DiffFormatter and RawTextComparator
* Dirty hash calculated via DigestOutputStream → MessageDigest(MD5)
* Safe against missing .git directory (RepositoryNotFoundException)

### 🧾 License

This utility is free to use and modify.
If you include it in your own builds, please credit the original source:

© 2025 Anton Garnik — net.agl.gradle.versionFromGit
