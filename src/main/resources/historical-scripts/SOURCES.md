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
