# Selected 7.1 upstream upgrades

This document records the initial selective port while the project still used its 7.0 protocol.
The changes below were
adapted from [capyb2222/LunaGC_7.1.0](https://github.com/capyb2222/LunaGC_7.1.0),
branch `7.1` at `1a77ca1e` (2026-09-27).

| Upstream commit | Applied behavior |
| --- | --- |
| `21b30ad3` | Accept the renamed 7.1 Excel fields while retaining the older field names. |
| `4c8cb8fc` | Include currency prices in shop goods cost items so the client can display them. |
| `94cebc39` | Handle generated element ball invokes and their energy ratio fallback. |
| `34c9a912`, `4d75804a` | Make `/give avatars` skip invalid entries and continue past one failed grant. |
| `5876bd6e` | Select the battle pass schedule from the loaded data for the active client version. Keep schedule `2700` when no matching data is available. |
| `079e3454` (partial) | Load the renamed costume field and look up open states by their numeric ID. |

The upstream 7.1 protocol and generated Java changes span more than 1,600 files.
After this selective port, the project migrated its proto sources and packet IDs to 7.1,
recovered missing historical message definitions, and switched to the pinned resource submodule.
See the root README for the current migration and validation status.

Verification of the original selective port: `compileJava` and the `VersionDataCompatibilityTest` and
`AbilityValueTest` test classes pass with the local JDK 17 and Gradle 8.5 setup.
