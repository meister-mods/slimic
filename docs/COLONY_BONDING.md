# Colony Bonding

[한국어](COLONY_BONDING.kr.md) · [The Aimless Colony](AIMLESS_COLONY.md) · [Royal synthesis](SYNTHESIS.md)

## Make a reusable station

Craft a Colony Bonding Station with **one Colony Heart, two iron ingots, three slimeballs and three stone bricks**. The heart comes from an assembly chamber's personal first-clear reward, its Peaceful alternative, or four Cohesion Shards. A completed station is reusable; binding and unbinding do not consume another heart.

Colony Bonding preserves the original two companions, which can be separated again. Royal Synthesis consumes an ordinary companion and an ancient companion to create a new one; that process cannot be reversed. Each process uses its own station and materials.

## Bind two companions

Supported pairs are **two ordinary Transport Slimes** or **two ordinary Combat Slimes**. The exact entity types must match. Advanced variants, other professions, mixed professions and groups larger than two are unsupported.

Recover both living companions and place their stored items in the station. You must be the primary owner of both. Equipment and cargo may stay inside. Wait until both companions have no status effects, absorption health, fire or freezing, and have recovered their full air supply. If combined health or stamina exceeds the supported numeric range, bonding is refused and both companions are preserved. An already active, reserved, bound, damaged or invalid identity is rejected without consuming the inputs. Unsupported external attachments prevent bonding, preserving the originals.

The screen shows both names, current health and stamina, unused individual points and occupied bag slots. It previews the colony's maximum health, capacity, attack and stamina. Inspect the result before confirming **Bind**. One stored representative is delivered to your inventory; pending delivery can be recovered at any bonding station.

## Operate the colony

The colony is managed as **one companion** and retains both original members. They cannot be deployed separately while bound. New deployment and recovered deployment select **Wait**; ordinary world reloads preserve an existing explicit order.

A **Transport Slime Colony** has two moving bodies joined by a thick, drooping green slime cord. They share one bag and order, but can collect cargo, unload and deliver supplies separately within the cord’s reach. They coordinate their jobs so they do not reserve the same worker or supply item twice. Interacting with either body manages the whole colony; Wait stops both. A **Combat Slime Colony** also has two cord-linked bodies. Each selects targets, attacks and blocks independently using its own equipment. The management screen provides two labelled groups of sword, shield and slime-armor slots. Both bodies share orders and target filters; Wait stops both.

Both bodies use one health and stamina pool. Damage or healing on either body changes the same remaining total. The second body has no separate name or status label. Combat members regroup before moving too far apart, and coordinate distant pursuits so they can rejoin.

The shared unused-point balance starts at the **arithmetic mean of both members’ balances, rounded down**. For example, 2 and 5 unused points become a shared balance of 3. Their existing stat investments remain separate.

| Property | Behavior |
| --- | --- |
| Maximum health and stamina | Sum of both members, within the supported numeric range. |
| Current health and stamina | Actual remaining totals; damage, healing and expenditure are reflected in the constituent records. |
| Cargo | Sum of the two real capacities. Transfers address the actual member bags, with no parallel copy. |
| Transport bonus | Already included in each ordinary Transport member's capacity. It is not applied again to the total. |
| Movement and work speed | The lower of the two member values. |
| Defense, Reach and Detection Range | Average of the two member values. |
| Attack base | 60% of the combined individual attack values, before the active weapon's native effects. |
| Combat equipment | Each member uses its own sword, shield and armor. The struck body's armor and raised shield apply to shared damage. Wear, swaps and supply replacements update that member's real items. Attack delays, shield-disable cooldowns and resting state survive recovery and unbinding. |
| Combat target filters | Initial group filters follow the first member. Group filter changes do not overwrite either member's saved individual filters. |
| Growth | Activity counts at half rate while any stat used by the role can still be upgraded for both members. Once no such stat remains, it counts at the normal rate, including when all stats are capped. The point-cost formula stays unchanged, and earned points join the shared pool. |
| Point upgrades | One shared point raises the selected stat by one step for each member below its cap. If only one can improve, only that member is upgraded; a capped member stays unchanged. If both are capped, no point is spent. Existing investments are not reset. |
| Creative tuning | Editing unused points changes only the shared pool and preserves unchanged stats and capacities. Direct stat edits still affect both members using the existing colony stat rules; health, stamina, and capacity are totals. |

**Earning a point and spending a point are separate.** While bound, the colony uses the higher member requirement for the activity needed to earn its next shared point. An upgrade still costs **one shared point**, whether one or both members can improve. The difference between their activity requirements is not refunded as points.

For example, A needs **224 activity** for its next point and B needs **288**. Starting from zero progress, the colony earns one shared point after **288 credited activity**; the credited amount already accounts for any half-rate growth. Spending that point upgrades both if neither has reached the selected stat’s cap. After unbinding, A needs **288** for its next point and B needs **352**: each original growth tier advances by the one point earned together. The 64-activity difference does not produce extra points.

Reaching a cap on one stat does not restore full growth while another role stat still allows both members to improve. When both can upgrade the chosen stat, they receive the same increment and retain their prior difference.

Each member retains its own body color, held items and armor, including material, dye and glint. The representative can follow, wait, work, change dimension, receive logistics support, heal, be recovered and be revived through the common companion systems. Both colony types remain within the link range and share health and stamina. Each combat member performs its own attacks, consuming its own weapon durability and one shared stamina per successful hit. Supply workers can replace a missing sword on either member.

Recovering or separating a colony does not restore its members’ health or stamina. Rename the representative independently if desired; original member names are preserved for unbinding. Dyeing or washing the colony updates member one. When unbound, status effects, absorption health, fire, freezing and the colony’s air supply transfer once to member one, retaining their remaining amount and duration. Temporary bonus health survives recovery and unbinding.

When one combat member acquires a target, the other can **join with a leaping charge**. It needs a sword, at least four shared stamina and a clear, supported route within twelve blocks. After a brief wind-up it follows a low arc, with a visible trail; a successful joining hit applies **1.5× native attack damage**, critical particles and a distinct impact sound. Taking off costs three stamina, and a hit costs one more. The colony can start one such leap every **five seconds**. A changed order, invalid target, insufficient stamina, obstacles or an unsafe landing interrupt the leap. It never passes through walls or loads distant chunks.

If an initial joining leap cannot start, the colony retries during the ongoing encounter when the path and cooldown allow it. It does not need to lose and reacquire the enemy.

## Unbind safely

Recover the colony, place it **alone in the first station slot**, inspect both records and confirm **Unbind**. The station needs two nearby loaded landing spaces with solid floors, sufficient collision clearance and no fluids. Blocked space, cancelled admission or failed pre-commit storage keeps the colony available.

On success, the original **two companions** return nearby in **Wait**, retaining their remaining health, stamina, equipment, cargo, ownership, invested upgrades and profession settings. The remaining shared balance is copied to each member: a colony with 3 unused points returns two companions with 3 unused points each. Each member resumes its own pre-binding growth-cost tier, advanced only by the points actually earned while bound. Unfinished activity is converted in proportion to that member’s next-point requirement. The representative is retired. Copied pre-binding items and old colony items cannot deploy duplicate identities.

If the colony collapses, the common broken-companion system preserves its member records. A slimeball revival restores the representative with **one total health**, shared across the members. It does not restore two full-health companions. Recover and unbind it after revival when safe.

## Persistence and recovery

Binding saves both members and the recoverable colony before clearing the station’s inputs. Unbinding verifies that both original companions can safely return before completing the separation. Saved progress allows interrupted transitions to be recovered and prevents old member or colony items from creating duplicates. Neither operation loads distant chunks.

The [development guide](DEVELOPMENT.md#assembly-and-colony-persistence) describes the saved records and transaction checks.

Use **Recover** at any Colony Bonding Station for pending delivery or a recoverable interrupted transition. Keep inventory space for the result. A full inventory does not erase the record. JEI lists the two binding and two unbinding operations; Patchouli provides the process in all four supported languages. Neither optional mod is required to use the station.
