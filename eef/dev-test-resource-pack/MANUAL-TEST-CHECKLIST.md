# EEF developer pack manual test checklist

Install `entity-event-features-0.1.0.jar`, enable this resource pack, turn subtitles on,
and use a creative test world with cheats. Disable other EEF rule packs while testing so
their HUD output does not obscure these messages.

| Test action in Minecraft | Expected feedback | Coverage |
|---|---|---|
| Enter a world | Actionbar `WORLD_LOADED observed`, then player-joined and calendar subtitles | Dispatch, lifecycle events, delayed action, local calendar context |
| Walk | `PLAYER_MOVED` no more than once every 5 seconds | Nested movement context, `exists`, rule cooldown |
| Toggle sprinting | `ENTITY_STATE_CHANGED: sprinting toggled`, followed by a delayed before/after subtitle | Recursive snapshot diff, changed path, current entity state, delayed action |
| Equip armor, keep it equipped, remove it, then equip it again | One `ENTERED` message on each equip; no repeats while armor remains equipped | Armor context and entered-predicate rearming |
| Stand completely still for at least 3 seconds | One stationary `DWELL` subtitle; moving rearms it | Dwell timing and entered activation |
| Apply Speed, then let or force the effect to end | One `EXITED: Speed effect ended` message | Effect collection predicate and exited activation |
| Tame a baby wolf with no passengers (or summon one with equivalent entity data) | One `ENTITY TRAITS: baby + tamed wolf, no passengers` actionbar | Generic living/mob, age, tame-state, and passenger-count predicates |
| Jump several times | Guaranteed `PLAYER_JUMPED`; approximately half of jumps also show the random-chance subtitle | Inferred event, chance |
| Place or summon a pig within render distance | `ENTITY_LOADED: pig` | Entity-type equality and entity cooldown |
| Use a spawn egg | `ENTITY_SPAWNED: spawn_item_use` | Authoritative spawn reason and source attribution |
| Cure a zombie villager | `ENTITY_TRANSFORMED: minecraft:zombie_villager` | Authoritative generic mob transformation capture |
| Let a villager pick up a dropped item | `ITEM_PICKED_UP` with the item id | Item, owner/thrower, and enchantment attribution |
| Hit an entity with a snowball or arrow | `PROJECTILE_HIT_ENTITY` with the projectile id | Authoritative projectile, victim, and owner attribution |
| Move a pig into water, keep it there, take it out, then return it | One environment message per water entry; no repeat while it remains wet | Water/biome/block environment context and entered activation |
| Put a pig within 10 blocks and clear line-of-sight of one or more cows; wait up to one second | One cow-observer actionbar and delayed relationship subtitle | Periodic `ENTITY_OBSERVED`, role preservation, exclusion, random single-witness selection, pair distance and line-of-sight |
| Keep that pig and at least one cow together beyond all cooldowns | No additional observer message; move the pig beyond 10 blocks, wait up to one second, then return it to receive exactly one new message | After-exit encounter latch, explicit no-match rearming |
| Damage the pig while within 32 blocks in a single-player/integrated-server world | Weighted outcome A/B, authoritative damage actionbar, then both delayed subtitles | Server damage bridge, attacker/source context, numeric predicates, weighted selection, no-repeat, deduplication |
| Connect to a server without EEF and damage the pig | Weighted outcome still appears after a roughly 0.1-second fallback delay; no authoritative actionbar | Client inference fallback without duplicate playback |
| Kill the pig | `ENTITY_DIED: pig`; later an inferred removal message | Death/removal events, `exists`, global cooldown |
| Move far enough that a tracked entity unloads | `ENTITY_REMOVED inferred` | Inferred removal capture |
| Hold sneak, then release within 5 seconds | Start message followed by stop message saying prior state matched | Scoped state set/read/clear |
| Hold sneak and press F3+T before one second elapses | Start message appears, but the delayed subtitle does not | Reload clears scheduled work and state |
| Sprint-swim in water, then leave swimming posture | Start and stop swimming messages | Swimming transitions |
| In Creative, double-tap jump to fly, then land or double-tap again | Start and stop flying messages; the start confirms an unknown extension action was ignored | Flying transitions and unknown-action tolerance |
| Mount a boat, minecart, horse, or other vehicle and remain mounted for 2 seconds | Immediate riding sequence step 1, then subtitle step 2 | Target existence predicate and timed exclusive sequence |
| Mount and dismount in under 2 seconds | Start message, stop/cancel message, and no step-2 subtitle | Sequence cancellation |
| Wait at least 10 seconds | Periodic `TIME_CHANGED` feedback no more than once per 10 seconds | World context and global cooldown |
| Run `/time add 1000` without crossing day/night | `TIME SKIP: non-natural clock jump` | Absolute/cycle delta and skip inference |
| Run `/weather rain`, then `/weather clear` | One `WEATHER_CHANGED` message per transition | Weather transition capture |
| Run `/gamemode creative`, then `/gamemode survival` | One `PLAYER_GAME_MODE_CHANGED` message per actual transition | Game-mode before/after capture |
| Run `/difficulty hard`, then `/difficulty peaceful` | One `WORLD_DIFFICULTY_CHANGED` subtitle per actual transition | Difficulty before/after capture |
| Walk from more than 6 blocks away to within 6 blocks of a pig, then back away | Entered-range message followed by exited-range message | Pack-derived distance threshold and crossing state |
| Left-click an entity without necessarily damaging it | `ENTITY_INTERACTED: attack` | Actor/target/item interaction context |
| Right-click an entity | `ENTITY_INTERACTED: use` | Generic entity-use capture |
| Begin mining a block | `PLAYER_BREAKING_BLOCK` | Client-observed attack-block input |
| Right-click a block | Block-interaction actionbar plus item-use-on-block subtitle | Block, item, hand, and actor context |
| Use a non-block item in the air and move immediately | `ITEM_USED`, then two distinct test tones/actionbars play sequentially rather than together; the first sound is entity-bound and the second requests a fixed position | Generic item-use input, moving/fixed positional modes, and whole-bundle global audio-channel queueing |
| Finish breaking a watched block | `BLOCK_BROKEN inferred` | Client-inferred block transition and previous state |
| Place a block against another block | `BLOCK_PLACED inferred` | Client-inferred placement transition |
| Place a block within 12 blocks of one or more pigs | In addition to `BLOCK_PLACED`, actionbar says the nearest pig was selected, then a delayed subject-binding subtitle | Nearby-entity query, candidate predicate, nearest selection, event-position origin, bound-entity cooldown |
| Observe a villager moving between idle, work, play, rest, or panic activities | `ENTITY_ACTIVITY_CHANGED` appears when its authoritative brain state changes | Generic primary/active activity, running behaviors, navigation state, server synchronization |
| Ring a bell with several villagers within 16 blocks | Every nearby villager emits the chime and synchronized-fanout subtitle on the same tick | Resource-pack-defined all-subject fan-out and synchronized speaker scheduling |
| Right-click a villager, nitwit, unemployed villager, or wandering trader | `TRADE_ATTEMPTED` reports the authoritative refusal reason and reputation tier | Server merchant-attempt capture, reputation, raid, offers, and refusal context |
| Open a villager or wandering-trader trading screen, then close it | Open and close actionbar messages; close retains the number of completed trades in that screen session | Merchant-screen lifecycle, exact merchant association, and authoritative context when available |
| Complete a trade while the screen remains open | `TRADE_COMPLETED` subtitle | Authoritative merchant/player attribution, input/output items, counts, uses, XP, and session count |
| Trigger activity changes on two villagers at nearly the same time | Both can play independently; one villager's active line does not hold the other villager's line | Per-entity speaker audio channels |
| Supply the same `conversation.id` context to two activity events (developer/instrumented test) | The first cue plays immediately and the second waits until the first cue's duration plus gap | Conversation-scoped ordered turn-taking |

## Malformed-resource check

Copy `fixtures/malformed-rule.json.disabled` to
`assets/eef_test/eef/malformed-rule.json`, then press F3+T. EEF should log one warning,
ignore only that file, and continue loading `captured_events.json`. Remove the copied file
after the test.

## Notes

- `ENTITY_LOADED` means the entity became visible to the client; it is not proof of a
  server-side spawn.
- Damage/death is authoritative in single-player and on servers that install EEF. A
  client-inferred fallback remains available on servers without EEF; only that fallback
  omits attacker and damage-source authority. Jumping and removal remain client-inferred.
- Actionbar messages can replace each other when several events occur in the same tick.
