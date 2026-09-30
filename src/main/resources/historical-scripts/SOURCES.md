# Recovered historical activity scripts

`Scene/3/scene3_group305001001.lua` is the original Elemental Crucible control group,
recovered from the [2.8 client script archive](https://github.com/Ahanlei123/2.8_live_data/blob/f25e3155e38f9dc8f647493bf23a935147caf59e/lua/activity/5001/activity5001_group305001001.lua).

- Pinned source commit: `f25e3155e38f9dc8f647493bf23a935147caf59e`
- Original filename: `lua/activity/5001/activity5001_group305001001.lua`
- SHA-256 of the source bytes: `c966f867f3df3f71f380e57d0a3e9b66e7e2a28bc480601ab21f800a502ef2c0`
- Only its path has changed, to the existing `SceneGroup` loader convention.

The script is retained as historical client resource data. It is not newly authored
LunaGC code. `ScriptLoader` uses it only when the selected resource checkout does not
provide that path. Availability of this control group does not activate a scene or
establish compatibility with newer client messages.

## Unreconciled Stars exploration (2001)

The six original exploration groups `302001005`, `302001012`, `302001013`,
`302001007`, `302001014`, and `302001015` are recovered from the same pinned
[activity archive](https://github.com/Ahanlei123/2.8_live_data/tree/f25e3155e38f9dc8f647493bf23a935147caf59e/lua/activity/2001).
Their bytes are unchanged; only the paths follow the `SceneGroup` convention.
`Activity/2001/sources.json` records each original URL and SHA-256.
`Activity/2001/fragments.json` derives positions from `activity2001_block200101.lua`
and collection IDs from those six scripts (118 points in total). It excludes the
three disused exploration missions. Each world's controller creates its own group
definitions and bindings; collected points are kept in the player's activity document.

The archived `Common/AsterMiddle.lua` and `Common/AsterBig.lua` are not activated by
this recovery. They need the original camp/reward and scene-play battle interfaces.
