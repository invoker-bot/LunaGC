# Selected 7.1 upstream upgrades

This project keeps its 7.0 protocol and local server changes. The changes below were
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

The upstream 7.1 protocol and generated Java changes span more than 1,600 files and
need a coordinated client and resource update. They are outside this selective port;
this project still identifies itself as 7.0 and does not claim 7.1 client support.

Verification: `compileJava` and the `VersionDataCompatibilityTest` and
`AbilityValueTest` test classes pass with the local JDK 17 and Gradle 8.5 setup.
