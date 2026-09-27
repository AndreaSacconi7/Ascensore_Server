# Ascensore protocol

Client and server exchange JSON text frames over one WebSocket (`/ws`). The server is authoritative: clients
send intentions (commands), the server validates them against the rules and broadcasts the resulting state
changes (messages). A client never changes game state on its own.

## Commands (client → server)

Envelope: `{"commandType": "...", "executable": {...}}`. The player is identified by the socket the command
arrives on, never by a field in the payload.

| `commandType` | `executable` | Meaning |
|---|---|---|
| `PLAYER_INFO_REQUEST` | `token`, `nickname` | First command on every socket. `token` is the Supabase access token; `nickname` is only read when the account has no public nickname yet, and is empty otherwise. |
| `JOIN_GAME_REQUEST` | `players` | Enter matchmaking for a match of `players` (2–4); the server default when absent or invalid. Each size has its own queue. |
| `LEAVE_GAME_REQUEST` | – | Leave matchmaking, or the match in progress for good (see *Leaving a match*). |
| `SET_BET` | `bet` | Bet how many tricks you will take this set. |
| `PUT_CARD` | `seed`, `value` | Play a card. |
| `LOGOUT` | – | Leave the current match (if any) and close the session. |
| `PING` | – | Heartbeat, every ~10 s. Answered with `PONG`. A session silent for 30 s is closed as dead. |

## Messages (server → client)

Envelope: `{"messageType": "...", "executable": {...}}`.

| `messageType` | `executable` | Sent |
|---|---|---|
| `PLAYER_INFO_RESPONSE` | `nickname`, `isLogged`, `needsNickname`, `inMatch`, `error` | Answer to `PLAYER_INFO_REQUEST`. `inMatch` means the player has a match in progress and its table state follows. `error` is one of `INVALID_TOKEN`, `NICKNAME_MISSING`, `NICKNAME_INVALID`, `NICKNAME_TAKEN`. With `needsNickname` the client asks the user for a nickname and repeats the request. |
| `JOIN_GAME_RESPONSE` | `nickname`, `isJoined`, `playersPerMatch` | Seated in a match that is waiting for players. |
| `WAITING_ROOM_UPDATE` | `playersPerMatch`, `players` | Who is waiting in your match (joining order), sent to everyone in it whenever someone joins or leaves. |
| `STARTING_GAME` | `connectedPlayers`, `maxHandSize` | The match starts; players in betting order. Hands go 1..`maxHandSize`..1, so the match has `2 × maxHandSize − 1` sets. |
| `HAND_UPDATE` | `cards` | Your hand for the new set (to you only). |
| `BRISCOLA_UPDATE` | `briscolaCard` (may be absent) | Trump card. Absent at the start of the peak set, where the card leading each trick sets it. |
| `PLAYER_STATE_UPDATE` | `nickname`, `playerState`, `turnMillisLeft`, `turnMillis` | `WAIT`, `BET` or `PUT`: whose turn it is and what they must do. For a turn, the time left and the full turn length (0 = no limit); clients count down from when they receive it. |
| `SETTED_BET` | `nickname`, `bet` | A bet was accepted. |
| `PLAYED_CARD` | `nickname`, `playedCard` | A card was accepted. |
| `END_ROUND` | `nextRoundNumber`, `nextPlayerOrderAndTaken` | A trick is over. Keys are in the play order of the next trick (winner first); values are tricks taken. |
| `END_SET` | `nextSetNumber`, `setsPlayed`, `nextPlayerOrderAndScore` | A set is over. `nextSetNumber` is the next hand size, `setsPlayed` the sets completed so far; keys are in the next betting order. |
| `END_GAME` | `gameResult` | Final scores, winner first. A player who left scores -500. |
| `PLAYER_EXIT_GAME` | `nickname` | A player left the match for good; their card, if any, is gone from the current trick. `END_GAME` follows if only one player remains. |
| `TEXT_MESSAGE` | `text` | Why your last command was rejected, or that the server acted for you (to you only). |
| `SESSION_REPLACED` | – | Your account logged in on another device; this socket is closed next (code 4001). Do not reconnect automatically. |
| `PONG` | – | Answer to `PING`. |
| `INFO_AFTER_RECONNECTION` | `set`, `round`, `setsPlayed`, `maxHandSize`, `scores`, `bets`, `roundsWon`, `playedCards` | Table state for a player who reconnected, after `STARTING_GAME`, `BRISCOLA_UPDATE` and `HAND_UPDATE`, and before one `PLAYER_STATE_UPDATE` per player. |

Maps whose key order carries meaning (play order, standing) are sent in that order.

## A set, step by step

1. `END_SET` (or `STARTING_GAME` for the first set), `HAND_UPDATE`, `BRISCOLA_UPDATE`
2. `PLAYER_STATE_UPDATE` `BET` for each player in turn, each answered by `SET_BET` → `SETTED_BET`
3. `PLAYER_STATE_UPDATE` `PUT` for each player in turn, each answered by `PUT_CARD` → `PLAYED_CARD`
4. After the last card of a trick: `END_ROUND`, and the winner leads the next trick
5. After the last trick of the set: `END_SET`, or `END_GAME` after the last set

## Turn time limit

Each turn (a bet or a card) has a time limit, 30 s by default. The turn that follows a finished trick or
set gets 3 s more, the time clients spend showing the cards before the next turn appears. When the time
runs out the server acts for the player (the lowest valid bet, or the weakest valid card) and tells them
with a `TEXT_MESSAGE`. A player whose turns run out three times in a row is taken out of the match as if
they had left, and receives `PLAYER_EXIT_GAME` with their own nickname.

## One device per account

Logging in (`PLAYER_INFO_REQUEST`) while the same account is connected elsewhere closes the older socket
after sending it `SESSION_REPLACED`. Commands still arriving from the old socket are ignored.

## Leaving a match

A player leaves with `LEAVE_GAME_REQUEST`, or by not reconnecting in time. Leaving is final. While at least
two players remain the match goes on without them:

- their bet no longer counts towards the betting round;
- their card, if already played, is taken out of the current trick (in the peak set, where the lead card
  is the briscola, the briscola becomes the next card on the table and `BRISCOLA_UPDATE` is sent);
- if it was their turn, it passes to the next player.

With one player left the match ends and that player wins. Players who left are listed last in `END_GAME`,
with a score of -500.

## Disconnections

If a player's socket drops during a match, the server keeps their seat for 60 seconds. Reconnecting with
`PLAYER_INFO_REQUEST` on a new socket resumes the match with `STARTING_GAME` … `INFO_AFTER_RECONNECTION`.
After 60 seconds the player leaves the match as above. Leaving a match that has not started frees the seat
immediately.

## Server restarts

A server restart (a deploy, or a crash) closes every socket. Matches in progress are restored from their
last saved move: to the client it looks like a dropped connection. It reconnects as usual, gets
`PLAYER_INFO_RESPONSE` with `inMatch: true` and the table state, and play goes on; the player on turn gets
20 extra seconds. Players who were waiting for a match are not in one after the restart
(`inMatch: false`) and need to join again. Logging in on another device while waiting for a match also
takes the player out of the queue.
