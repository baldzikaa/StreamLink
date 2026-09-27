# StreamLink

Hook your Twitch, Kick, YouTube and TikTok live streams up to your Minecraft server. A follow pops a title on screen, a sub spawns zombies next to you, `!tnt` in chat drops a TNT, a big donation calls down lightning. You decide what happens in `config.yml`.

Built for Paper and Folia, written in Kotlin.

## Platforms

| | Chat | Follows | Subs | Gifted subs | Money | Raids | Likes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Twitch | yes | no | yes | yes | bits | yes | |
| Kick | yes | yes | yes | yes | Kicks | hosts | |
| YouTube | yes | | memberships | gifted memberships | Super Chats, Super Stickers | | |
| TikTok | yes | yes | yes | | gifts | | yes |

No accounts, tokens or API keys needed:

- **Twitch** reads chat anonymously over IRC. Follows need an OAuth token on Twitch's side, so they aren't supported.
- **Kick** looks up the chatroom and listens on Kick's Pusher websocket.
- **YouTube** polls live chat the same way the website does, so there's no API quota to run out of. It finds the live stream from a handle and picks it up again when you go live.
- **TikTok** uses [TikTokLiveJava](https://github.com/jwdeveloper/TikTokLiveJava). Gift streaks count once, when the streak ends.

Every connection reconnects by itself, backing off from 5 seconds up to 5 minutes. Offline channels are checked every couple of minutes, so you can leave it running and it picks up when you go live.

## Setup

1. Drop the jar in `plugins` and start the server once.
2. Link a player to their channels in `plugins/StreamLink/config.yml`:

```yaml
links:
  baldzika:
    twitch: baldzika
    kick: baldzika
    youtube: "@baldzika"
    tiktok: baldzika
```

3. `/streamlink reload`, then `/streamlink` to see what connected.

Chat from every linked platform shows up in game for the streamer, so they can keep an eye on it without alt-tabbing.

## Rules

Each rule says which events it reacts to, some optional filters, and a list of actions.

```yaml
rules:
  sub-zombies:
    events: [sub, gift-subs]
    actions:
      - message: "<green>{user} subscribed, here come {count} zombies"
      - spawn:
          entity: zombie
          amount: "{count}"
          radius: 6

  big-gifts:
    events: [donation, gift]
    platforms: [tiktok, youtube]
    min: 100
    actions:
      - broadcast: "<light_purple>{user} just sent {amount} {currency} to {player}"
      - lightning:
          harmless: true

  chat-tnt:
    events: chat
    match: "^!tnt$"
    cooldown: 30s
    actions:
      - spawn:
          entity: tnt
          radius: 3
```

**Events:** `chat`, `follow`, `sub`, `gift-subs`, `donation`, `gift`, `raid`, `like`, `share`

**Filters:**

| | |
| --- | --- |
| `platforms` | only react to some platforms |
| `min`, `max` | compared to the event amount: bits or money for donations, coins for TikTok gifts, months for subs, how many for gifted subs and likes, viewers for raids |
| `match` | regex checked against chat and donation messages, case insensitive |
| `gift` | TikTok gift names, like `[Rose, Galaxy]` |
| `chance` | `0.25` fires a quarter of the time |
| `cooldown` | `30s`, `5m`, `1h`. Counted per rule and per streamer |
| `enabled` | `false` turns the rule off without deleting it |

**Actions:**

| | |
| --- | --- |
| `message`, `actionbar`, `title` | shown to the streamer, [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting |
| `broadcast` | shown to everyone |
| `sound` | `entity.player.levelup`, or with `volume` and `pitch` |
| `spawn` | any entity near the streamer, `amount` can use placeholders, `radius` up to 16 blocks |
| `effect` | potion effect with `type`, `seconds` and `level` |
| `give` | item and amount |
| `lightning` | `harmless: false` if you want it to hurt |
| `command` | runs from the console |

**Placeholders:** `{user}` `{player}` `{platform}` `{amount}` `{count}` `{message}` `{currency}` `{gift}` `{months}`

Anything a viewer typed is escaped before it goes into MiniMessage, so nobody can slip a click event into your chat. In `command` actions, names and messages are stripped down to letters, numbers and basic punctuation, so they can't break out of the command. `spawn` and `give` amounts are capped by `limits` in the config, so a 10,000 coin gift doesn't spawn 10,000 zombies.

## Folia

Everything that touches the streamer (messages, spawns, effects, items) runs on the streamer's own region thread, and console commands run on the global region. Spawns stay within a few blocks of the player, so they land in the same region. Network work runs on StreamLink's own threads and never blocks a tick.

## Commands

| Command | Permission | |
| --- | --- | --- |
| `/streamlink` | `streamlink.admin` | Connection status for every link |
| `/streamlink reload` | `streamlink.admin` | Reload the config and reconnect |
| `/streamlink test <player> <platform> <event> [amount] [message]` | `streamlink.admin` | Fire a fake event to try your rules without going live |
| `/streamlink pause` | `streamlink.pause` (everyone) | Lets a streamer pause their own events. Chat still shows |

`/sl` works as a short alias.

## Building

```
./gradlew build
```

Needs Java 25. The jar in `build/libs` works on Paper and Folia 26.1.2 or newer. Kotlin isn't bundled, Paper downloads it on first start.

The tests run the real Twitch, Kick and YouTube clients against local fake servers, so they don't need a network connection.

## License

MIT
