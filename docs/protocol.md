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
| `JOIN_GAME_REQUEST` | – | Enter matchmaking. |
| `SET_BET` | `bet` | Bet how many tricks you will take this set. |
| `PUT_CARD` | `seed`, `value` | Play a card. |
| `LOGOUT` | – | Leave the current match (if any) and close the session. |

## Messages (server → client)

Envelope: `{"messageType": "...", "executable": {...}}`.

| `messageType` | `executable` | Sent |
|---|---|---|
| `PLAYER_INFO_RESPONSE` | `nickname`, `isLogged`, `needsNickname`, `inMatch`, `error` | Answer to `PLAYER_INFO_REQUEST`. `inMatch` means the player has a match in progress and its table state follows. `error` is one of `INVALID_TOKEN`, `NICKNAME_MISSING`, `NICKNAME_INVALID`, `NICKNAME_TAKEN`. With `needsNickname` the client asks the user for a nickname and repeats the request. |
| `JOIN_GAME_RESPONSE` | `nickname`, `isJoined` | Seated in a match that is waiting for players. |
| `STARTING_GAME` | `connectedPlayers` | The match starts; players in betting order. |
| `HAND_UPDATE` | `cards` | Your hand for the new set (to you only). |
| `BRISCOLA_UPDATE` | `briscolaCard` (may be absent) | Trump card. Absent at the start of the peak set, where the card leading each trick sets it. |
| `PLAYER_STATE_UPDATE` | `nickname`, `playerState` | `WAIT`, `BET` or `PUT`: whose turn it is and what they must do. |
| `SETTED_BET` | `nickname`, `bet` | A bet was accepted. |
| `PLAYED_CARD` | `nickname`, `playedCard` | A card was accepted. |
| `END_ROUND` | `nextRoundNumber`, `nextPlayerOrderAndTaken` | A trick is over. Keys are in the play order of the next trick (winner first); values are tricks taken. |
| `END_SET` | `nextSetNumber`, `nextPlayerOrderAndScore` | A set is over. `nextSetNumber` is the next hand size; keys are in the next betting order. |
| `END_GAME` | `gameResult` | Final scores, winner first. A player who left scores -500. |
| `PLAYER_EXIT_GAME` | `nickname` | A player left the match (currently this ends the match). |
| `TEXT_MESSAGE` | `text` | Why your last command was rejected (to you only). |
| `INFO_AFTER_RECONNECTION` | `set`, `round`, `scores`, `bets`, `roundsWon`, `playedCards` | Table state for a player who reconnected, after `STARTING_GAME`, `BRISCOLA_UPDATE` and `HAND_UPDATE`, and before one `PLAYER_STATE_UPDATE` per player. |

Maps whose key order carries meaning (play order, standing) are sent in that order.

## A set, step by step

1. `END_SET` (or `STARTING_GAME` for the first set), `HAND_UPDATE`, `BRISCOLA_UPDATE`
2. `PLAYER_STATE_UPDATE` `BET` for each player in turn, each answered by `SET_BET` → `SETTED_BET`
3. `PLAYER_STATE_UPDATE` `PUT` for each player in turn, each answered by `PUT_CARD` → `PLAYED_CARD`
4. After the last card of a trick: `END_ROUND`, and the winner leads the next trick
5. After the last trick of the set: `END_SET`, or `END_GAME` after the last set

## Disconnections

If a player's socket drops during a match, the server keeps their seat for 60 seconds. Reconnecting with
`PLAYER_INFO_REQUEST` on a new socket resumes the match with `STARTING_GAME` … `INFO_AFTER_RECONNECTION`.
After 60 seconds the player leaves the match (`PLAYER_EXIT_GAME`, then `END_GAME`). Leaving a match that
has not started frees the seat immediately.
