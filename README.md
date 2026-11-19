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
| Hypixel API key (needed by `!comp`, `!pb`, `!catalvl`, `!bank`, `!sblvl`, `!mp`, `!rng`) | `/cmd key <key>` |
| Last.fm API key (needed by `!playing_music`) | `/cmd lastfmkey <key>` |
| Last.fm account | `/cmd link <username>` |
| Check what is configured | `/cmd status` |

- Get a Hypixel key on <https://developer.hypixel.net>. Development keys expire: when a command answers
  that the key was rejected, create a new one and run `/cmd key <new key>` again. No restart needed.
- Get a Last.fm key on <https://www.last.fm/api/account/create>. Spotify can scrobble to Last.fm
  (Spotify settings > connect to Last.fm), which is what lets the mod read your current song.
- The mod's own messages in your chat are colored : green for information, yellow for a warning or a usage, red for an error, after a gold `[chatcmds]`.
- `/cmd ...` commands only run on your computer, they are **never sent to the server**. Always type the
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
| `!pets [player]` | Active pet and its level |
| `!skills [player]` | Farming, mining, combat... levels and the average |
| `!slayer [player]` | Level and XP of the 6 slayers |
| `!purse [player]` | Coins in your purse |
| `!pcookie` | Booster Cookie Bazaar prices |
| `!pkismet` | Kismet Feather Bazaar prices |
| `!precomb` | Recombobulator 3000 Bazaar prices |
| `!phandle` | Lowest BIN price of Necron's Handle (first call can take several seconds) |
| `!inflation` | Price index of the old *Consumer Inflation Index* basket, base 100 (see below) |
| `!comp1` ... `!comp7` [player] | Boss collection of the floor (normal run = 1, master run = 2) and master runs missing for 1000 |
| `!pb1` ... `!pb7` [player] | Master mode best time of the floor (S+, else S, else any) |
| `!catalvl [player]` | Catacombs level and class levels |
| `!bank [player]` | Bank balance (bank API must be enabled on your profile) |
| `!sblvl [player]` | SkyBlock level |
| `!mp [player]` | Magical Power |
| `!role` | Class and class level of the Party Finder members |
| `!count [cmd]` | Top 5 most used commands, or top 5 users of a command (stored in `config/chatcmds-usage.json`) |
| `!seymour` | Hex color of the dyed armor piece in your main hand (owner only) |
| `!rng [drop]` | Fill rate of your selected RNG meter drop: `x rng / x req, x% fill` (see below) |
| `!playing_music` | Song you are playing right now (Last.fm) |
| `!music`, `!kayoux`, `!angrelin`, `!roast [name]`, `!metal [name]`, `!credit` | Fun commands |

**Owner-only** (only the logged-in player can use them, the answer is shown only to you):

| Command | Effect |
|---|---|
| `!cm-inch cm\|inch` | Change unit for the account |
| `!link <lastfm user>` | Link your Last.fm account (same as `/cmd link`, but visible in the chat) |
| `!mute <player>` | Ignore all the commands of this player. Does not work on yourself |
| `!unmute <player>` | Stop ignoring them |

**Stats of another player:** the commands marked `[player]` read **your** profile by default, and the profile of
another player when you add a name: `!mp Steve`, `!catalvl Steve`, `!comp7 Steve`. The answer then starts with the name
(`Steve - Magical Power : 1200`). The name is turned into a UUID with the Mojang API, then the profile is read with your
Hypixel key, so the player's own API settings apply (a hidden Skills, Inventory or Banking API gives
"disabled or empty"). Lookups of other players are limited to 12 per minute to protect your key (300 requests per 5 minutes).

Each command has a 3 second cooldown to avoid spam.

## Slash commands (only on your computer)

| Command | Effect |
|---|---|
| `/dh` | Same as `/warp dh` |
| `/cmd gui` | Opens the list of all commands (built-in and custom) |
| `/cmd help` | Lists the `/cmd` subcommands |
| `/cmd add <name> <response>` | Creates a custom command |
| `/cmd editor [name]` | Opens the editor of custom commands |
| `/cmd test <name> [args]` | Runs a custom command with real data and shows the answer only to you |
| `/cmd remove <name>` | Deletes a custom command |
| `/cmd list` | Lists your custom commands |
| `/cmd unit <cm\|inch>`, `/cmd mute <player>`, `/cmd unmute <player>` | Same as the owner-only chat commands |
| `/cmd censor add\|remove\|list <word>` | Words the mod masks in its answers (`cocaine` is sent as `c******`), to get past the Hypixel chat filter. Whole words only |
| `/cmd censor defaults` | Re-adds the starter list (drugs, sexual and swear words, violence), which is also loaded on the first start. It is a guess: Hypixel's filter is private |
| `/cmd alias <name> <command>` | Shortcut: `/cmd alias !p !pcookie` makes `!p` run `!pcookie`. Extra words are kept: `/cmd alias !k pprice kismet`. Also `/cmd alias list` and `/cmd alias remove <name>` |
| `/cmd reload` | Reloads the config files |
| `/cmd rng select <drop>`, `/cmd rng score <category> <value>` | Set your selected drop / stored score by hand (Tab completes) |
| `/cmd find <text>` | Looks for a key in your SkyBlock profile (helps to set up `!rng`) |
| `/cmd inflation reset` | Makes today the new base 100 of `!inflation` |

## Custom commands

```
/cmd add lucky {user} has {percent} luck today
/cmd add dice {user} rolled {random:1-6}
/cmd add eightball {pick:Yes|No|Maybe|Ask again later}
/cmd add hug {user} hugs {target}
```

Then anyone can write `!lucky`, `!dice`, `!eightball` or `!hug Bob` in the chat.
Custom commands are saved and come back at every start.

| Placeholder | Replaced by |
|---|---|
| `{user}` | Name of the player who wrote the command |
| `{me}` | Your own name |
| `{owner}` | `true` when the sender is you, else `false` |
| `{arg}`, `{arg2}`, `{arg3}` | First, second, third word written after the command (empty if none) |
| `{args}` | Everything written after the command |
| `{target}` | `{arg}`, or `{user}` when there is no argument |
| `{random:1-100}` | A random number in the range (included) |
| `{percent}` | A random percentage, for example `57%` |
| `{pick:a\|b\|c}` | One random item of the list |
| `{time}`, `{date}` | `21:35` and `2026-11-16` |
| `{count}` | How many times the command was used (everyone) |
| `{mycount}` | How many times the sender used it |
| `{top}` | The 3 players who used it the most |
| `{math:2*{arg}}` | Calculator, only the value |
| `{price:cookie}`, `{sell:cookie}` | Bazaar insta buy / insta sell price of an item (same names as `!pprice`) |
| `{replace:text\|from\|to}` | Replaces every `from` by `to` in `text` |
| `{set:x=5}` and `{get:x}` | Variable used only inside this answer. `{x}` is a short form of `{get:x}`. `{get:x\|0}` gives `0` when `x` does not exist. `{get:x}` also finds a variable saved by `{store:}` in another command |
| `{store:x=5}` and `{load:x}` | Variable kept between answers (shared by all commands). `{load:x\|0}` also takes a default |
| `{inc:x}`, `{inc:x=5}` | Adds 1 (or 5, or a negative number) to the variable `x`; the answer is empty. `{sinc:x}` does the same on a stored variable |
| `{area}` | Where you are (`Hub`, `Dungeon`, `Private Island`...), asked with `/locraw`. It is the island, not the sub-zone |
| `{br}` | New line: the answer is sent as several chat messages, 1.2 s apart, 5 at most |
| `{until:2026-12-25}` | Number of days left until the date (negative once it is past) |
| `{if:A>B?then\|elif:C==D?other\|else}` | Condition. Operators `>` `<` `>=` `<=` `==` `!=`, numbers or text (case ignored). `elif` and `else` are optional |

Placeholders can be nested: the innermost is resolved first, for example `{if:{random:1-100}>50?Win|Lose}`.
The answer is read once from left to right: a variable must be set **before** it is read, and a value can contain other placeholders (`{set:x={random:1-6}}`). In `{if:...}` and `{pick:...}` only the chosen part is evaluated. Inside `{if:...}` the texts cannot contain a bare `?` or `|` (inside `{...}` they can).
What players write in the chat is cleaned: the characters `{ } | ?` are removed, so nobody can inject placeholders.

```
/cmd add countdown Christmas in {until:2026-12-25} days
/cmd add where {user}, I am in {area} right now
/cmd add rules Rule 1 : be nice{br}Rule 2 : have fun
/cmd add visits Visit n°{count} (you : {mycount})
/cmd add coin {if:{random:1-2}==1?Heads|Tails}
/cmd add hello {if:{arg}==?Hello everyone|Hello {arg}}
/cmd add boss {if:{owner}==true?Yes boss|Who are you ?}
/cmd add grade {if:{arg}>=16?Great|elif:{arg}>=10?Pass|Fail}
/cmd add cookie Booster Cookie costs {price:cookie} coins
/cmd add double {arg} x 2 = {math:{arg}*2}
/cmd add leet {replace:{args}|e|3}
/cmd add hug #{count} : {user} hugs {target}. Top huggers : {top}
```

### Test before sending

- In the editor, **Test** opens a test bench: choose who writes the command and what they type, then **Run (sample data)**
  (instant, fake prices) or **Run live** (real prices, area and counters, nothing is sent and nothing is counted).
- `/cmd test <name> [arguments]` runs a saved command the same way and shows the answer only to you.

### Editor

`/cmd editor` (or **Edit custom commands** in `/cmd gui`) opens the list of your commands: click one to edit it,
or **+ New command**. `/cmd editor <name>` opens one command directly.

The editor has a name field, a response field and a toolbar of placeholders sorted in tabs
(**Sender**, **Words**, **Random**, **Info**, **Text**, **Variables**, **If**, **Examples**).
Click a button to insert it where the cursor is. Hover a button (or click it) to read what it does and the result it
gives on sample data. The **Examples** tab replaces the response with a complete example. The line under the response
shows a live preview, or the first error in red. **Test** opens the test bench.

Rules: a name has 1 to 20 letters, digits, `_` or `-`, and cannot be a built-in command. A response has at most
200 characters and cannot start with `/` or `!`. For safety, the mod never sends a message starting with `/`
or `!` and never sends more than 256 characters.

## RNG meter (`!rng`)

`!rng` answers the fill rate of your **selected** drop, for example
`Bonzo Staff (f1) : 15,900 rng / 31,800 req, 50% fill`. `!rng <drop>` does the same for any drop.
The mod ships the list of drops and required scores of the Catacombs floors (F1-M7) and the six slayers
(data from the NotEnoughUpdates repo, may change with game updates). A drop is written `<category>_<drop>`,
for example `m7_necron_handle`, `f1_bonzo_staff`, `revenant_foul_flesh`. Press **Tab** after `/cmd rng select ` to list them.

Where your selected drop and stored score come from (first found wins):
1. your SkyBlock profile, if the Hypixel API exposes them (the mod searches for an "rng meter" object;
   the layout is undocumented, so this may find nothing: check with `/cmd find meter`);
2. the `RNG Meter - 15,900 Stored Score/XP` line that Hypixel prints after a run or a slayer boss;
3. what you saved by hand: `/cmd rng select <drop>` and `/cmd rng score <category> <value>`.

Select your drop once with `/cmd rng select m7_necron_handle`; the score then follows the chat line after each run.

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
| `chatcmds-commands.json` | Your custom commands, their use counters and stored variables |
| `chatcmds-rng.json` | Selected RNG drop and stored scores |
| `chatcmds-data.json` | Inflation base and history |
| `chatcmds-usage.json` | Command usage counts per player (`!count`) |

Manual edits of `chatcmds.json` are picked up automatically.

## Build

You do not need to install anything: push the repository to GitHub, open the **Actions** tab, and download the
`chatcmds-jar` artifact of the latest run (use the jar without `-sources`).
To build on your computer: JDK 25 and Gradle 9.5 or newer, then `gradle build`; the jar is in `build/libs/`.

## Good to know

- The mod answers **in the public chat with your account**. Servers can mute players who spam or whose
  messages break the rules (including links, or the content of custom commands). You are responsible for
  what your commands say. Use `!mute` / `/cmd mute` against abusers.
- Hypixel API policy: use your own key, for your own account. Do not publish a jar containing a key and do not
  share your key. The mod caches API answers (profile 60 s, Bazaar 30 s, Auction House 60 s).
- `!role` reads the Party Finder join messages of the chat (`Name joined the dungeon group! (Mage Level 40)`).
  It only knows the players who joined while the mod was running.

## Troubleshooting

| Problem | Fix |
|---|---|
| `Hypixel rejected the API key` | Key expired or wrong: create a new one and run `/cmd key <key>` |
| `Hypixel API key missing` | Run `/cmd key <key>` |
| Nothing happens | Check `/cmd status`; errors are shown only to you, prefixed with `[chatcmds]` |
| A command is slow | `!phandle` reads the whole Auction House the first time (then cached for 60 s) |
| `!playing_music` says nothing is playing | Spotify must scrobble to Last.fm and the account must be public |
| A message is not sent, or `We blocked your comment` | Hypixel chat filter (links, adult words, song titles...). The mod warns you in your chat. Mask the word with `/cmd censor add <word>` |
