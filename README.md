# [playstrategy.org](https://playstrategy.org)

[![Build server](https://github.com/Mind-Sports-Games/lila/workflows/Build%20server/badge.svg)](https://github.com/Mind-Sports-Games/lila/actions?query=workflow%3A%22Build+server%22)
[![Build assets](https://github.com/Mind-Sports-Games/lila/workflows/Build%20assets/badge.svg)](https://github.com/Mind-Sports-Games/lila/actions?query=workflow%3A%22Build+assets%22)

<img src="https://raw.githubusercontent.com/Mind-Sports-Games/lila/master/public/images/home-bicolor.png" alt="Playstrategy homepage" title="Playstrategy comes with light and dark theme, this screenshot shows both." />

Lila (li[chess in sca]la) is a free online chess game server focused on [realtime](https://playstrategy.org/games) gameplay and ease of use.

It features a [search engine](https://playstrategy.org/games/search),
[computer analysis](https://playstrategy.org/ANYMwuhU) distributed with [fishnet](https://github.com/niklasf/fishnet),
[tournaments](https://playstrategy.org/tournament),
[simuls](https://playstrategy.org/simul),
[forums](https://playstrategy.org/forum),
[teams](https://playstrategy.org/team),
and a [shared analysis board](https://playstrategy.org/study).
The UI is available in more than 130 languages.

Playstrategy is written in [Scala 2.13](https://www.scala-lang.org/),
and relies on the [Play 2.8](https://www.playframework.com/) framework.
[scalatags](https://www.lihaoyi.com/scalatags/) is used for templating.
Pure chess logic is contained in the [strategygames](https://github.com/Mind-Sports-Games/strategygames) submodule.
The server is fully asynchronous, making heavy use of Scala Futures and [Akka streams](https://akka.io).
WebSocket connections are handled by a [separate server](https://github.com/Mind-Sports-Games/lila-ws) that communicates using [redis](https://redis.io/).
Playstrategy talks to [Stockfish](https://stockfishchess.org/) deployed in an [AI cluster](https://github.com/niklasf/fishnet) of donated servers.
It uses [MongoDB](https://mongodb.org) to store games.
HTTP requests and WebSocket connections can be proxied by [nginx](https://nginx.org).
The web client is written in [TypeScript](https://www.typescriptlang.org/) and [snabbdom](https://github.com/snabbdom/snabbdom), using [Sass](https://sass-lang.com/) to generate CSS.
Proxy detection done with [IP2Proxy database](https://www.ip2location.com/database/ip2proxy).

See [playstrategy.org/source](https://playstrategy.org/source) for a list of repositories.

Join us on discord for more info (coming soon...).
Use [GitHub issues](https://github.com/Mind-Sports-Games/lila/issues) for bug reports and feature requests.

## Installation

```
./lila # thin wrapper around sbt
run
```

The Lichess Wiki describes [how to setup a development environment](https://github.com/lichess-org/lila/wiki/Lichess-Development-Onboarding), which is the same process as PlayStrategy.

## HTTP API

Feel free to use the [Playstrategy API](https://playstrategy.org/api) in your applications and websites.

## Supported browsers

| Name              | Version | Notes                                             |
| ----------------- | ------- | ------------------------------------------------- |
| Chromium / Chrome | last 10 | Full support                                      |
| Firefox           | 61+     | Full support (fastest local analysis since FF 79) |
| Opera             | 55+     | Reasonable support                                |
| Safari            | 11.1+   | Reasonable support                                |
| Edge              | 17+     | Reasonable support                                |

Older browsers (including any version of Internet Explorer) will not work.
For your own sake, please upgrade. Security and performance, think about it!

## License

Playstrategy is licensed under the GNU Affero General Public License 3 or any later
version at your choice with an exception for Highcharts. See [copying](https://github.com/Mind-Sports-Games/lila/blob/master/COPYING.md) for
details.

## Credits

This code is forked from, and exists because of [ornicar](https://github.com/ornicar), and the whole [Lichess project](https://github.com/ornicar/lila).

[playstrategy.org](https://playstrategy.org/) currently supports the [Mind Sports Olympiad](https://mindsportsolympiad.com/).
