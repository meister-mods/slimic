# Companion interactions

[한국어](INTERACTIONS.kr.md) · [Gameplay](GAMEPLAY.md) · [Development](DEVELOPMENT.md)

Companions cooperate through ownership, orders and work requests. Follow and Wait withdraw work requests. Transport and Healing use same-primary-owner teams, or teams whose primary owners have both shared access with each other. Transfers check actual requests and capacity. Guardians protect available owners, and combat excludes shared allies. One-sided sharing grants manual management without opening the invited player’s stores.

The following scenarios have independent native game regressions. Transfers check exact quantities and original tags, durability, equipment and current health.

| ID | Participants | Expected behavior |
| --- | --- | --- |
| I01 | Transport / Mining | Unload a full mining bag and resume mining. |
| I02 | Transport / Mining | Supply the exact spare tool, retaining its data. |
| I03 | Transport / Mining | Deliver one bucket and resume fluid removal. |
| I04 | Transport / Mining | Limit cobblestone delivery to one requested stack. |
| I05 | Transport / Farming / Lumberjack | Match hoes and axes to each worker. |
| I06 | Transport / Combat | Replace a missing sword without replacing other gear. |
| I07 | Transport / Cleaning | Unload a full cleaner and resume collection. |
| I08 | Transport / Healing / Warehouse | Use an owned waiting warehouse as a supply source. |
| I09 | Warehouse / worker | Collect requested cargo without a Slime Chest. |
| I10 | Transport / Warehouse | Prefer an eligible owned warehouse over a nearer chest. |
| I11 | Transport / Warehouse | Fall back to a chest when warehouse storage is full. |
| I12 | Transport / Warehouse | Deposit a remainder after a partial warehouse transfer. |
| I13 | Transport / Warehouse | Exclude storage without same-owner or mutual sharing authorization. |
| I14 | Transport / Warehouse | Revalidate ownership immediately before transfer. |
| I15 | Healing / patient | Charge only the healer that completes treatment. |
| I16 | Healing / Herald | Retain blocked duration across failed approaches. |
| I17 | Healing / stored companion | Reject duplicate identity revival without costs. |
| I18 | Healing / Warehouse | Revive with tuned capacity and final-slot cargo intact. |
| I19 | Workers / Transport / Warehouse | Conserve exact cargo and source tags under competition. |
| I20 | Transport / Healing | Concurrent deliveries cannot exceed the request limit. |
| I21 | Guardian / owner | Held totems act first; one rescue consumes one reserve. |
| I22 | Healing / Saddle | Heal without changing armor, wings, saddle or Wait. |
| I23 | Healing / Saddle | Revive at one health with all three exact gear items. |
| I24 | Guardian / Saddle / owner | Protect a mounted owner without removing rider or gear. |
| I25 | Combat / all twenty-five professions | Same-owner companions remain excluded with all filters enabled. |
| I26 | Transport / worker | Competing carriers cannot duplicate spare tools. |
| I27 | Herald / worker | A signal callback cannot continue a cancelled monitoring order. |
| I28 | Warehouse / worker | Use a real loaded detour to reach cargo. |
| I29 | Healing / Soggy | Keep Roam and its fixed origin during treatment. |
| I30 | Mining / Mining | Share one chunk staircase across start heights; preserve legacy floors, queued protection, exact repair costs and loot. |
| I31 | Transport / Fishing | Unload fishing cargo and supply replacement rods with their item data intact. |
| I32 | Transport / Tunnel | Supply at most 64 matching lining blocks; retain the ghost template. |
| I33 | Abundance / plants / Healing | Apply growth with stamina, retain patient care, and respect cancelled bonemeal events. |
| I34 | Meow / Creeper | Use native cat avoidance while active, independently of Wait or Roam. |
| I35 | Combat / Dragon / Transport | Collect own kill loot and request unloading without stopping full-bag combat. |
| I36 | Fishing / recovery | Preserve pending catches through recovery and successive partial unloadings. |
| I37 | Commander / workers | Gather tagged living companions across chunks and dimensions, preserve traits, and arrive in Wait. |
| I38 | Shared owners / all companions | Allow delegated management, preserve the primary owner, and revoke access immediately. |
| I39 | Transport / shared Warehouse | Reject one-sided access; allow mutual cooperation and stop transfers on revocation. |
| I40 | Ender / player | Retain identity-bound tokens and exact pinned chunks across cold restarts; reject unsafe or revoked travel without costs. |
| I41 | Glow / Glow | Share temporary light leases without deleting another companion’s light or changing fluids and containers. |
| I42 | Shared owners / Saddle / Combat | Eject revoked riders; enemy assignments retain PvP, team, Creative, and ally protection. |
| I43 | Recovery / all twenty-five professions | Re-release with saved traits in Wait; revive in Wait at one health while retaining other traits. |
| I44 | Scanner / workers | Preview bounded current or planned areas without starting work, loading chunks, or recovering the companion; enforce current access and range. |
| I45 | Herald / blocked worker / owners | Report type, custom name, and cause to authorized online owners, including other dimensions; respect cooldowns and revoked access. |
| I46 | Wild Slime / player | Convert one size-one Slime with one Survival golden apple; start owned and waiting, retain its name, and use normal owner-only Shift recovery. Event or spawn vetoes preserve inputs. |
| I47 | Ancient remnant / companions | Remnants recognize companions as intruders; companion combat recognizes the ancient faction as hostile. Home boundaries end pursuit. |
| I48 | Ancient carrier / depot / player | Move exact physical cargo only between matching ruin depots. Opening a chest claims it; mob griefing can deny transfers. |
| I49 | Core / pedestal / player | Restore with new ownership and UUID at one health in Wait; consume each material once and reject duplicate or reentrant redemption. |
| I50 | Combat / Royal Guard | Preserve identity, growth, equipment and cargo through seal specialization; keep safe initial orders. |
| I51 | Royal Guard / allies / attacker | Redirect one eligible hit, honor ownership, PvP, sight and target filters, spend actual resources, reject canceled damage and prevent guard chains. |
| I52 | Mine corpse / core / restoration station | Break once for one core; restore once in the Overworld with a fragment and slimeball, separate from owned companion revival. |
| I53 | Ancient worker / logistics / growth | Shares ordinary tool, stair, bag, and supply contracts; restored ancient roles retain random invested training across recovery, portals, and saves. |
| I54 | Guardian / Royal Guard / persistence | Apply fourfold common Health once, retain training, clamp legacy excess health without healing, and preserve Creative health. |
| I55 | Creative editor / bags / logistics | Accept values beyond recommendations, synchronize exact values, preserve sparse cargo across pages and recovery, and reject occupied-slot removal. |

Endurance validation records real elapsed server time while repeating obstacle detours, deliveries, treatment, collection, stamina recovery and exact item conservation. Each independent scenario uses a separate work area and waits until its entities can tick. Save/reload checks retain work orders, companion identities and equipment. Real route and species-specific terrain fixtures use isolated areas so unrelated work targets cannot substitute for the intended scenario.

Historical release results are recorded in [CHANGELOG.md](../CHANGELOG.md). Current regression coverage also includes fourfold profession health, Creative values above recommendations, exact menu number transport, and expanded bag pages.
