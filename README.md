# Chat Commands

A client-side [Fabric](https://fabricmc.net) mod for **Minecraft 26.2** (made for Hypixel SkyBlock).
When any player writes a command starting with `!` in the chat (`!paff`, `!pchim`, `!comp1`...), the mod
answers in the public chat with your account. You can also create your own commands in game, browse
all commands in a GUI, and link a Last.fm account to share the song you are listening to.

> Not affiliated with or endorsed by Hypixel, Mojang or Microsoft.

## Requirements

- Minecraft **26.2**, Java **25**
- [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.3 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api) for 26.2

## Install

1. Download the jar (see *Build* below) and put it in `.minecraft/mods/`.
2. Start the game once, then follow *Setup*.

## Setup (all in game, no file to edit)

| What | Command |
|---|---|
| Hypixel API key (needed by `!comp`, `!pb`, `!catalvl`, `!bank`, `!sblvl`, `!mp`, `!rng`) | `/cc key <key>` |
| Last.fm API key (needed by `!playing_music`) | `/cc lastfmkey <key>` |
| Last.fm account | `/cc link <username>` |
| Check what is configured | `/cc status` |

- Get a Hypixel key on <https://developer.hypixel.net>. Development keys expire: when a command answers
  that the key was rejected, create a new one and run `/cc key <new key>` again. No restart needed.
- Get a Last.fm key on <https://www.last.fm/api/account/create>. Spotify can scrobble to Last.fm
  (Spotify settings > connect to Last.fm), which is what lets the mod read your current song.
- `/cc ...` commands only run on your computer, they are **never sent to the server**. Always type the
  leading `/`: without it your key would be sent in the public chat.
- Keep your keys private. Never put them in the repository or share the config folder.

## Chat commands

Anyone can use them (the answers come from **your** account, stats are **yours**), except the owner-only ones.

| Command | Answer |
|---|---|
| `!paff [name]` | Random size, 6 to 22 cm (the only command that answers in French) |
| `!math <expr>` | Calculator: `+ - * / % ^` and parentheses. Try `!math 10^300` |
| `!pchim` | Chimera I Bazaar prices (insta buy / insta sell) |
| `!precomb` | Recombobulator 3000 Bazaar prices |
| `!phandle` | Lowest BIN price of Necron's Handle (first call can take several seconds) |
| `!inflation` | Price index of the old *Consumer Inflation Index* basket, base 100 (see below) |
| `!comp1` ... `!comp7` | Boss collection of the floor (normal run = 1, master run = 2) and master runs missing for 1000 |
| `!pb1` ... `!pb7` | Master mode best time of the floor (S+, else S, else any) |
| `!catalvl` | Catacombs level and class levels |
| `!bank` | Bank balance (bank API must be enabled on your profile) |
| `!sblvl` | SkyBlock level |
| `!mp` | Magical Power |
| `!role` | Class and class level of the Party Finder members |
| `!rng <category> <option> <drop>` | RNG meter progress: `x rng / x req, x% fill` (see below) |
| `!playing_music` | Song you are playing right now (Last.fm) |
| `!music`, `!kayoux`, `!angrelin`, `!roast [name]`, `!metal [name]`, `!credit` | Fun commands |

**Owner-only** (only the logged-in player can use them, the answer is shown only to you):

| Command | Effect |
|---|---|
| `!cm-inch cm\|inch` | Unit used by `!paff` |
| `!link <lastfm user>` | Link your Last.fm account (same as `/cc link`, but visible in the chat) |
| `!mute <player>` | Ignore all the commands of this player. Does not work on yourself |
| `!unmute <player>` | Stop ignoring them |

Each command has a 3 second cooldown to avoid spam.

## Slash commands (only on your computer)

| Command | Effect |
|---|---|
| `/dh` | Same as `/warp dh` |
| `/cc gui` | Opens the list of all commands (built-in and custom) |
| `/cc help` | Lists the `/cc` subcommands |
| `/cc add <name> <response>` | Creates a custom command |
| `/cc remove <name>` | Deletes a custom command |
| `/cc list` | Lists your custom commands |
| `/cc unit <cm\|inch>`, `/cc mute <player>`, `/cc unmute <player>` | Same as the owner-only chat commands |
| `/cc reload` | Reloads the config files |
| `/cc rng list\|add\|remove` | Manages the RNG entries |
| `/cc find <text>` | Looks for a key in your SkyBlock profile (helps to set up `!rng`) |
| `/cc inflation reset` | Makes today the new base 100 of `!inflation` |
| `/rng <category> <option> <drop>` | Same as `!rng`, with tab completion |

## Custom commands

```
/cc add lucky {user} has {percent} luck today
/cc add dice {user} rolled {random:1-6}
/cc add eightball {pick:Yes|No|Maybe|Ask again later}
/cc add hug {user} hugs {target}
```

Then anyone can write `!lucky`, `!dice`, `!eightball` or `!hug Bob` in the chat.
Custom commands are saved and come back at every start.

| Placeholder | Replaced by |
|---|---|
| `{random:1-100}` | A random number in the range (included) |
| `{percent}` | A random percentage, for example `57%` |
| `{pick:a\|b\|c}` | One random item of the list |
| `{user}` | Name of the player who wrote the command |
| `{arg}` | First word written after the command (empty if none) |
| `{target}` | `{arg}`, or `{user}` when there is no argument |

Rules: a name has 1 to 20 letters, digits, `_` or `-`, and cannot be a built-in command. A response has at most
200 characters and cannot start with `/` or `!`. For safety, the mod never sends a message starting with `/`
or `!` and never sends more than 256 characters.

## RNG meter (`!rng`)

`!rng <category> <option> <drop>` answers `x rng / x req, x% fill`. The mod needs to know, for each drop,
how many points are required and where your current score is stored in your SkyBlock profile, so the entries
are defined by you:

```
/cc find rng                                                     # find where the score is stored
/cc rng add slayer revenant scythe_blade 100000 slayer.slayer_bosses.zombie.some_key
/rng slayer revenant scythe_blade                                # tab completion works from here
```

Use `_` instead of spaces in names. Entries are saved in `config/chatcmds-rng.json`.
The mod ships **without** entries because the required scores and the profile fields must come from you
(they change with game updates).

## Inflation

`!inflation` follows the basket of the *Consumer Inflation Index* of skyblock.finance (Booster Cookie,
Recombobulator 3000, Chimera I, perfect gemstones, Master Stars and three scrolls, with the same weights),
priced at the middle between insta buy and insta sell on the Bazaar.

The answer looks like `Inflation index : 104.3 (base 100 on 2026-11-13), 24h : +1.2%`.
100 is the day the mod first measured the basket, so the number only has a meaning relative to that day.
While you play, the mod measures the basket every 30 minutes to be able to show the 24 hour change.
This is **not** the exact number displayed by the website.

## Files

All in `.minecraft/config/`:

| File | Content |
|---|---|
| `chatcmds.json` | Keys, Last.fm account, unit, muted players |
| `chatcmds-commands.json` | Your custom commands |
| `chatcmds-rng.json` | Your RNG entries |
| `chatcmds-data.json` | Inflation base and history |

Manual edits of `chatcmds.json` are picked up automatically.

## Build

You do not need to install anything: push the repository to GitHub, open the **Actions** tab, and download the
`chatcmds-jar` artifact of the latest run (use the jar without `-sources`).
To build on your computer: JDK 25 and Gradle 9.5 or newer, then `gradle build`; the jar is in `build/libs/`.

## Good to know

- The mod answers **in the public chat with your account**. Servers can mute players who spam or whose
  messages break the rules (including links, or the content of custom commands). You are responsible for
  what your commands say. Use `!mute` / `/cc mute` against abusers.
- Hypixel API policy: use your own key, for your own account. Do not publish a jar containing a key and do not
  share your key. The mod caches API answers (profile 60 s, Bazaar 30 s, Auction House 60 s).
- `!role` reads the Party Finder join messages of the chat (`Name joined the dungeon group! (Mage Level 40)`).
  It only knows the players who joined while the mod was running.

## Troubleshooting

| Problem | Fix |
|---|---|
| `Hypixel rejected the API key` | Key expired or wrong: create a new one and run `/cc key <key>` |
| `Hypixel API key missing` | Run `/cc key <key>` |
| Nothing happens | Check `/cc status`; errors are shown only to you, prefixed with `[chatcmds]` |
| A command is slow | `!phandle` reads the whole Auction House the first time (then cached for 60 s) |
| `!playing_music` says nothing is playing | Spotify must scrobble to Last.fm and the account must be public |
| A message is not sent | Hypixel chat filter (for example links). Rephrase the response |
