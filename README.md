# BOC Lietuva (RuneLite plugin)

Adds a side-panel button to RuneLite that shows players how to join the BOC Lietuva clan:
about text, join steps, requirements, who to ask for an invite, and buttons for
"Copy clan name", Discord and an extra link (website / stream).

Everything is edited in the plugin's settings (wrench icon → BOC Lietuva) – no rebuild needed.
Multi-line fields (join steps, requirements) use one item per line.

## Run it locally
Requires JDK 11+ (IntelliJ IDEA recommended).

Open the folder in IntelliJ IDEA (it imports the Gradle project automatically) and run
the `run` Gradle task, or run `ClanInfoPluginTest.main` in src/test.
This starts a developer RuneLite client with the plugin loaded – log in normally.

## Publish to the Plugin Hub
1. Push this folder to a public GitHub repo.
2. Fork https://github.com/runelite/plugin-hub, add `plugins/boc-lietuva` with:
       repository=https://github.com/AndriusButas/boc-lietuva.git
       commit=<full commit hash>
3. Open a pull request and wait for review.

Note: the defaults in ClanInfoConfig.java are what new users see before editing
settings – set them to your real clan name/Discord before publishing a clan-specific build.
