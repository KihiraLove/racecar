# Racecar

Racecar is a RuneLite pet transmog plugin intended to replace **Dom** with the burrowed form of the Doom of Mokhaiotl.

## Current test mode

The current build is deliberately in test mode. It only transmogs a follower whose in-game name is **Yami**, so other pets can be taken out without Racecar affecting them.

The real follower remains responsible for server-driven following/pathing and is hidden through RuneLite's render listener. Racecar registers a client-side replacement on the follower's exact local point and orientation.

## Doom model findings

Cache/model inspection showed that the burrowed form is primarily an animated pose of Doom's core mesh rather than a separate car-shaped model:

- normal Doom (`14707`) uses models `56469`, `56470`, `56467`, `56466`, and `55956`;
- burrowed Doom (`14709`) uses the shared core models `56469`, `56470`, and `56467`;
- Dom (`14519` and `14785`) uses model `56456` for both NPC definitions;
- applying the boss burrow animations directly to Dom's pet model mangles the mesh, so the pet and boss animation rigs are not compatible enough for that approach.

The incomplete-looking standing Doom seen with animations disabled was expected from `14709`: its composition intentionally omits the two extra model parts used by the normal boss.

## Rendering approach

Racecar keeps the burrowed boss core model at native scale internally and applies `AnimationID.DOM_BURROW_IDLE` or `AnimationID.DOM_BURROWED_MOVEMENT` at render time.

This is important because RuneLite documents `Client.applyTransformations()` as returning a shared temporary model which becomes invalid after another transformation call. The transformed model is therefore never retained between frames. Each frame is animated at boss scale, then immediately reduced from the 5x5 boss footprint to pet scale before it is drawn.

The scale also respects the NPC composition's width/height scale values rather than assuming a uniform 128/128 boss scale. A configurable percentage multiplier is then applied on top of that calculated pet scale.

## Interaction menu

`RuneLiteObjectController` does not expose a native NPC clickbox or interaction API, so the rendered burrowed Doom cannot itself participate in the game's NPC menu system.

Racecar instead uses the hidden server-backed follower as the interaction target:

- if RuneLite still generates native menu entries for the hidden follower, Racecar rewrites them to Dom's name and action slots;
- if hiding the follower also removes those native entries, hovering the hidden follower's convex hull or tile injects a simulated Dom menu;
- the simulated `Talk-to`, `Pick-up`, and `Examine` entries forward the corresponding NPC action to the real follower by NPC index.

During Yami test mode those actions still operate on Yami server-side. In final Dom mode they operate on the actual Dom pet.

## Configuration

`Vertical offset` raises or lowers the final pet-sized animated model relative to the follower tile. Positive values raise the model. The current tuned default is `15`, with a range of `-512` to `512`.

`Scale (%)` adjusts the final rendered size without changing follower positioning or animation behavior. The tuned default is `150`. The tuning range is `50` to `200` so the final value can still be adjusted if necessary.

The temporary `Use Dom pet model` and `Burrow animations` compatibility switches have been removed. The Dom-model experiment proved incompatible, and the burrow animation is required to produce the intended car form.

## Final mode

After the test transmog is visually confirmed, disabling `TEST_MODE` limits the source follower to:

- `NpcID.DOM_PET`
- `NpcID.POH_DOM_PET`

The reference `pet-to-npc-transmog` repository is read-only for this project; Racecar contains the implementation changes.
