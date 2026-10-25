# The Aimless Colony

[한국어](AIMLESS_COLONY.kr.md) · [The Slimic](THE_SLIMIC.md) · [Colony bonding](COLONY_BONDING.md)

## Finding an assembly chamber

Abandoned Assembly Chambers generate underground in new Damp Plains chunks in The Slimic. A marked stair entrance connects the surface to a vaulted arena with approximately 40×40 blocks of maneuvering space. The stair remains open during combat. Structure placement reserves space against sanctums, mines and small ruins, rejects existing full chunks, and checks construction before writing each generating section. Existing terrain is not regenerated.

Assembly Clues appear in sanctum and collapsed-mine supplies. Use one in The Slimic to mark a nearby candidate chamber. Like kingdom maps, it checks nearby predicted and saved structure locations without loading distant chunks. It can fail when no suitable candidate lies within the configured search radius. A clue is optional: finding the entrance directly is sufficient.

The Last Rally Record and Endless Assembly Record describe the gathering from a slime observer's perspective. Use their items to read them without Patchouli; the optional guidebook also contains the observations. They leave XXX's disappearance unexplained.

## Starting and retrying

Use the original Assembly Core altar. A two-second warning precedes awakening. Each chamber has one encounter at a time; repeated interaction does not create another boss. The first attempt is free. A failed attempt or retreat carries no additional fee. When the encounter remains loaded with no eligible players nearby for one minute, it resets. Unloaded encounters do not advance their timers or acquire chunks.

After a successful clear, another encounter at that altar costs **eight slimeballs and two Old Command Fragments**, with a **24,000-tick** cooldown measured from the previous clear. Costs and cooldown are configurable. Payment records preserve a paid attempt across interrupted saving. A failed repeat attempt can be retried freely.

In **Peaceful**, no boss awakens. **Eight Old Command Fragments and 32 slimeballs** unlock each player's first reward at a chamber once. This does not create an unlimited reward source. The costs are configurable. If a material payment is interrupted, the same player must resume it with Peaceful mode unchanged.

Encounter progress belongs to the chamber. Replacing a broken core at its original altar position restores its encounter interface. A copied core elsewhere cannot create another encounter for that site. Any Assembly Core can deliver an already pending personal reward.

## Combat

The default body has **320 health**, **six armor**, and **eight attack damage**. Server settings can change these values. Its translucent slug body contains multiple command cores. Collision is approximately 3.2 blocks wide and 2.4 blocks high, independent of the crawling animation.

| Health | Phase | Behavior |
| --- | --- | --- |
| Above 70% | Cohesion | Charges, slams and small fragment releases. |
| Above 35%, up to 70% | Division | Larger releases, periodic reabsorption and annular shockwaves; charges can lead into a ring attack. |
| 35% or less | Frenzy | Gathering pulls, crossed attack lanes, the last rally and vulnerable intervals; a pull can lead into a crossing attack. |

A charge locks its direction when its one-second warning begins, then moves straight and can hit each victim only once during that charge. A slam marks its surrounding area for 1.2 seconds before striking inside the circle. A weak horizontal pull precedes a nearby shockwave; movement checks avoid unsupported or fluid-filled landing paths. The last rally warns for 2.5 seconds before attempting reabsorption. Ground outlines, sounds and action-bar messages supplement particles.

From Division onward, an annular shockwave warns for **1.5 seconds** before striking between **3.5 and eight blocks** from the body. Move into the safe center or outside the outer ring. During Frenzy, a crossing attack locks onto the target’s position at the start of its **1.6-second warning**. Leave its two crossing lanes, each extending eight blocks from the center, by moving diagonally away. A charge can lead into a ring, and a gathering pull can lead into a crossing. Every follow-up receives its full warning; there is no immediate, unannounced second impact. These attacks respect line of sight and damage each target at most once per impact. New attacks and follow-ups require a valid target in the same dimension within 36 blocks. A crossing that has already been announced still strikes its original marked position when the target leaves.

The body targets players outside Creative and Spectator modes and owned combat companions. It respects alliances and native damage cancellation, and does not break blocks. No companion profession is required. The body remains damageable throughout the fight; killing every fragment is never an invulnerability requirement.

## Conserved cohesion

The encounter begins with **100 cohesion**. Each fragment borrows **12**. Spawning allows at most **six active fragments** and also requires enough free cohesion. The invariant is:

`free cohesion + active fragment cohesion + permanently lost cohesion = 100`

A killed fragment permanently loses its share for that encounter. Only a real returning fragment refunds its own allocation. An unloaded fragment still owns its allocation; unloading never creates more cohesion. Attack fragments pursue valid targets, Cohesion fragments assist nearby returns, and Returning fragments move back toward the body. Their colors and core patterns differ. Summoned fragments grant no loot or experience. Fragments that lose their connection to the body are removed without rewards; defeat or reset invalidates all remaining fragments, including those loaded later.

Remaining cohesion includes the body's free share and surviving allocated shares. At **60 or more**, incoming damage is reduced by 10%. Below 60 that reduction ends. At **40 or less**, the core can be exposed for five seconds, taking 20% extra damage, with a 25-second interval between exposures.

Reabsorption heals three health per returned fragment, at most nine per cast and 48 across the encounter, never above maximum health. Killing fragments during the warning prevents their individual returns. A failed last rally produces a short vulnerable interval. The body does not repeatedly cast reabsorption when it has no fragments.

## Personal rewards

An eligible player must be alive near the chamber when it ends, have accumulated at least **600 nearby encounter ticks** by default, and have contributed actual damage or a successful support action. Damage by an owned combat companion, healing, protection by a Guardian Angel Slime and a completed Command Slime rally count toward its owner's contribution. Spectators, Creative players and passive bystanders are excluded.

Each player's first clear of a particular chamber grants **one Colony Heart and a Last Rally Record**. Subsequent clears there grant **two Cohesion Shards**. Four shards craft one heart. Use the heart to craft a reusable [Colony Bonding Station](COLONY_BONDING.md).

Rewards are claimed individually at an Assembly Core. A full inventory, disconnected recipient or broken altar does not erase an entitlement. Claimed rewards remain recorded, including after an interrupted save. Moving or using a delivered reward does not make it available again. The [development guide](DEVELOPMENT.md#assembly-and-colony-persistence) describes how reward delivery is saved.

## Server settings

The `aimlessColony` section of the server configuration provides these defaults and accepted ranges:

| Setting | Default | Range |
| --- | --- | --- |
| `health` | 320 | 40–10,000 |
| `armor` | 6 | 0–30 |
| `attackDamage` | 8 | 1–100 |
| `maxActiveFragments` | 6 | 0–6; available cohesion also limits spawning |
| `repeatCooldownTicks` | 24,000 | 0–2,400,000 |
| `repeatSlimeballs` | 8 | 0–64 |
| `repeatCommandFragments` | 2 | 0–64 |
| `peacefulSlimeballs` | 32 | 1–64 |
| `peacefulCommandFragments` | 8 | 1–64 |
| `participationTicks` | 600 | 20–12,000 |

The bounded map search uses `sanctumMapSearchRadiusChunks`, default 160 chunks, range 16–512. Health, armor and attack settings apply to newly created bodies; existing bodies keep their saved attributes. Fragment spawning, reward participation, altar costs and repeat-cooldown checks use the current settings.
