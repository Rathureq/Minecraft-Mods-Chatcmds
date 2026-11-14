# Chat Commands

A client-side [Fabric](https://fabricmc.net) mod for **Minecraft 26.2** (made for Hypixel SkyBlock).
When any player writes a command starting with `!` in the chat (`!pb7`, `!pchim`, `!comp1`...), the mod
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
| `!math <expr>` | Calculator: `+ - * / % ^` and parentheses. Try `!math 10^300` |
| `!pchim` | Chimera I Bazaar prices (insta buy / insta sell) |
| `!pprice <item>` | Bazaar price of any item (`!pprice booster cookie`); if it is not in the Bazaar, the lowest BIN. Shortcuts: `cookie`, `kismet`, `chimera`, `recomb`, `handle` |
| `!ah <item>` | Lowest BIN of an item whose name contains the text (`!ah necron's handle`), can take several seconds |
| `!mayor` | Current mayor, his perks and the minister |
| `!pets` | Active pet and its level |
| `!skills` | Farming, mining, combat... levels and the average |
| `!slayer` | Level and XP of the 6 slayers |
| `!purse` | Coins in your purse |
| `!pcookie` | Booster Cookie Bazaar prices |
| `!pkismet` | Kismet Feather Bazaar prices |
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
| `!rng [drop]` | Fill rate of your selected RNG meter drop: `x rng / x req, x% fill` (see below) |
| `!playing_music` | Song you are playing right now (Last.fm) |
| `!music`, `!kayoux`, `!angrelin`, `!roast [name]`, `!metal [name]`, `!credit` | Fun commands |

**Owner-only** (only the logged-in player can use them, the answer is shown only to you):

| Command | Effect |
|---|---|
| `!cm-inch cm\|inch` | Change unit for the account |
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
| `/cc censor add\|remove\|list <word>` | Words the mod masks in its answers, to get past the Hypixel chat filter. Whole words only |
| `/cc censor defaults` | Re-adds the starter list (drugs, sexual and swear words, violence), which is also loaded on the first start. It is a guess: Hypixel's filter is private |
| `/cc alias <name> <command>` | Shortcut: `/cc alias !p !pcookie` makes `!p` run `!pcookie`. Extra words are kept: `/cc alias !k pprice kismet`. Also `/cc alias list` and `/cc alias remove <name>` |
| `/cc reload` | Reloads the config files |
| `/cc rng select <drop>`, `/cc rng score <category> <value>` | Set your selected drop / stored score by hand (Tab completes) |
| `/cc find <text>` | Looks for a key in your SkyBlock profile (helps to set up `!rng`) |
| `/cc inflation reset` | Makes today the new base 100 of `!inflation` |
| `/rng [drop]` | Same as `!rng`, with tab completion on all drops |

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

`!rng` answers the fill rate of your **selected** drop, for example
`Bonzo Staff (f1) : 15,900 rng / 31,800 req, 50% fill`. `!rng <drop>` (or `/rng <drop>`) does the same for any drop.
The mod ships the list of drops and required scores of the Catacombs floors (F1-M7) and the six slayers
(data from the NotEnoughUpdates repo, may change with game updates). A drop is written `<category>_<drop>`,
for example `m7_necron_handle`, `f1_bonzo_staff`, `revenant_foul_flesh`. Press **Tab** after `/rng ` to list them.

Where your selected drop and stored score come from (first found wins):
1. your SkyBlock profile, if the Hypixel API exposes them (the mod searches for an "rng meter" object;
   the layout is undocumented, so this may find nothing: check with `/cc find meter`);
2. the `RNG Meter - 15,900 Stored Score/XP` line that Hypixel prints after a run or a slayer boss;
3. what you saved by hand: `/cc rng select <drop>` and `/cc rng score <category> <value>`.

Select your drop once with `/cc rng select m7_necron_handle`; the score then follows the chat line after each run.

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
| `chatcmds.json` | Keys, Last.fm account, unit, muted players, masked words, aliases |
| `chatcmds-commands.json` | Your custom commands |
| `chatcmds-rng.json` | Selected RNG drop and stored scores |
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
| A message is not sent, or `We blocked your comment` | Hypixel chat filter (links, adult words, song titles...). The mod warns you in your chat. Mask the word with `/cc censor add <word>` |
