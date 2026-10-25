# The Aimless Colony

[한국어](AIMLESS_COLONY.kr.md) · [The Slimic](THE_SLIMIC.md) · [Colony bonding](COLONY_BONDING.md)

## Finding an assembly chamber

Abandoned Assembly Chambers lie beneath newly generated Damp Plains in The Slimic. Look for a marked stair entrance on the surface. The stairs remain open during combat, so you can retreat.

Assembly Clues appear in sanctum and collapsed-mine supplies. Use one in The Slimic to mark a nearby chamber location. If the search fails, move elsewhere and retry. A clue is optional; you can enter a chamber found through exploration. The Last Rally Record and Endless Assembly Record recall the gathering through a slime's eyes and can be read without Patchouli.

## Starting and retrying

Use the chamber's original Assembly Core altar to begin. The first combat attempt is free, and failed attempts or retreats do not cost extra. When the encounter is loaded and all eligible players have left for one minute, it resets. This timer pauses while the encounter is unloaded.

After a successful clear, another encounter at that altar consumes **eight slimeballs and two Old Command Fragments**. The default wait is **24,000 ticks** from the previous clear. A failed repeat attempt can be retried freely. Costs and waiting time follow the server settings.

In **Peaceful**, no boss awakens. Each player can instead consume **eight Old Command Fragments and 32 slimeballs** to receive that chamber's first reward once. Repeat rewards are unavailable through this alternative. If a material payment is interrupted, the same player must retry with the same Peaceful/non-Peaceful setting.

If the altar is broken, replace the Assembly Core at its original position to resume access. Placing a core elsewhere does not create another encounter for that chamber.

## Combat

Watch the colony's movements and respond to the sights and sounds around you. Companions can help with fighting and support; no particular profession is required. Keep track of your companions and the way back to the entrance.

## Personal rewards

To qualify, be alive near the chamber when the encounter ends, spend at least **600 encounter ticks** nearby by default, and contribute damage or a successful support action. Damage and treatment by owned companions, Guardian Angel Slime protection and a completed Command Slime rally count toward their owner's contribution. Spectators, Creative players and passive bystanders do not qualify.

Each player's first clear of a chamber grants **one Colony Heart and a Last Rally Record**. Later clears there grant **two Cohesion Shards**. Four shards craft one heart, which is used to make a reusable [Colony Bonding Station](COLONY_BONDING.md).

Claim rewards individually at an Assembly Core. Leave room in your inventory. Pending rewards remain available after a disconnect or a broken altar and can be claimed at another Assembly Core. Retrying does not duplicate rewards already received.

## Server settings

The `aimlessColony` section of the server configuration provides these defaults and accepted ranges:

| Setting | Default | Range |
| --- | --- | --- |
| `health` | 320 | 40–10,000 |
| `armor` | 6 | 0–30 |
| `attackDamage` | 8 | 1–100 |
| `maxActiveFragments` | 6 | 0–6 |
| `repeatCooldownTicks` | 24,000 | 0–2,400,000 |
| `repeatSlimeballs` | 8 | 0–64 |
| `repeatCommandFragments` | 2 | 0–64 |
| `peacefulSlimeballs` | 32 | 1–64 |
| `peacefulCommandFragments` | 8 | 1–64 |
| `participationTicks` | 600 | 20–12,000 |

The bounded map search uses `sanctumMapSearchRadiusChunks`, default 160 chunks, range 16–512. Health, armor and attack settings apply to newly created bosses. Other encounter settings apply when their conditions are checked.
