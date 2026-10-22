# Rain's Discord Link (`discordlink`)

Lilly's Discord and her Minecraft servers, joined.

- **Join codes.** Somebody who is not whitelisted is turned away with a code. `/link <code>` in Lilly's Discord
  whitelists them and links the account in one go, so the code proves the account is theirs.
- **One account per person**, unless they hold a role the Discord admins picked; those may also `/whitelist <friend>`.
- **Discord roles → LuckPerms groups**, one way, on link, on every role change and every `sync-minutes`.
  Only mapped groups are ever touched.
- **More servers for `/link`** (`more-link-guild-ids`): `/link`, `/unlink`, `/accounts`, `/whitelist`, `/status`
  and `/player` work there too. Roles, groups and `/mcadmin` stay Lilly's: whitelister roles and LuckPerms groups
  come from somebody's roles on her server, and only leaving her server takes groups away.
- **One bot for the network.** Every server ticks; whoever holds RainsCore's network role `discordlink-bot` runs it,
  and another server takes over within a lease if that one stops.

## Set up
1. Discord developer portal → the app → **Bot**: switch on **Server Members Intent**, reset the token.
2. Server console: `/discordlink token <token>` (never in chat), then `/settings` → Discord link → server id.
3. Invite the bot (no permissions needed): `https://discord.com/oauth2/authorize?client_id=<app id>&scope=bot%20applications.commands&permissions=0`
4. In Discord: `/mcadmin whitelisters` (pick roles), `/mcadmin map role:<role> group:<luckperms group>`, `/mcadmin show`.

In game: `/link` (a code for an already whitelisted player), `/link unlink`; `/discordlink status|restart`.
Discord: `/link`, `/unlink`, `/accounts`, `/whitelist`, `/mcadmin …` (Manage Server).

A test server started from a copy of the live data must run with `RAINS_DISCORDLINK_BOT=off`: the copy holds the
real token, and a second bot on it would answer every command twice.

## Build
`mvn install` — module tests (SQLite, and Postgres in CI), then the shaded plugin and its jar test.
