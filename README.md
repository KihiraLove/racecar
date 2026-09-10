# Racecar

Racecar is a RuneLite pet transmog plugin intended to replace **Dom** with the burrowed form of the Doom of Mokhaiotl.

## Current test mode

The current build is deliberately in test mode. It only transmogs a follower whose in-game name is **Yami**, so other pets can be taken out without Racecar affecting them.

The implementation is based on the known-working code in the `pet-to-npc-transmog` plugin:

- uses the real `client.getFollower()` NPC for all server-driven following/pathing;
- hides that follower through RuneLite's render listener;
- creates a client-side `RuneLiteObject` replacement;
- keeps the replacement registered on the follower's exact local point and orientation every client tick;
- builds the replacement from the model parts of `NpcID.DOM_BOSS_BURROWED`;
- animates Doom at its native scale first, then scales the complete animated frame from its native 5x5 footprint to a 1x1 pet-sized visual;
- applies the configurable vertical correction after animation and scaling;
- uses `AnimationID.DOM_BURROW_IDLE` while stationary;
- uses `AnimationID.DOM_BURROWED_MOVEMENT` while moving;
- rewrites the hidden follower's right-click menu to use the actions and name from `NpcID.DOM_PET`.

Animating before scaling is important for the burrowed Doom animations because animation transforms can contain model-space translation. Scaling the base mesh first leaves those translations at boss scale and can make the visible model appear several tiles away from the follower even though the `RuneLiteObject` itself is on the correct tile.

Because the right-click menu remains attached to the real hidden follower, the simulated Dom entries still act on the actual follower during testing.

## Configuration

`Vertical offset` controls how far the pet-sized burrowed Doom model is raised or lowered relative to the follower tile. Positive values raise the model. The default is `64`, with a tuning range of `-512` to `512`; changes are reflected while the plugin is running.

## Final mode

After the test transmog is visually confirmed, disabling `TEST_MODE` limits the source follower to:

- `NpcID.DOM_PET`
- `NpcID.POH_DOM_PET`

The reference `pet-to-npc-transmog` repository is read-only for this project; Racecar contains the implementation changes.
