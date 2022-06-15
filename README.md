# AlexBotOlex
A custom bot for my twitch channel alexshadowolex

If you want to use that bot yourself, be advised that it has full access to the assigned twitch and spotify account. I will take no responsibilty for breaking your accounts!

## Setup
To setup the bot, you need to build the repository to an executable (jar, exe, ...).<br>

Before executing the program, you need a folder "data" on the same level as the executable with following content:
* botconfig.properties
  * channel=<channel_name>
  * only_mods=<true/false>
  * spotify_client_id=<spotify client id>
  * spotify_client_secret=<spotify client secret>
  * command_prefix=<prefix for commands>
* twitchtoken.txt
  * The only content: twitch token
* spotifytoken.json:
* Spotify token in JSON structure, will be filled by executing the file "SetupToken.kt"

Just replace the stuff in <> with the value described.

You will also need to install the following dependencies and add them to your path:
* FFmpeg
