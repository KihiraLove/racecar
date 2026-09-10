# Racecar

Racecar is a RuneLite pet transmog plugin intended to replace **Dom** with the burrowed form of the Doom of Mokhaiotl.

## Current test mode

The current build is deliberately in test mode. It only transmogs a follower whose in-game name is **Yami**, so other pets can be taken out without Racecar affecting them.

The implementation is based on the known-working code in the `pet-to-npc-transmog` plugin:

- uses the real `client.getFollower()` NPC for all server-driven following/pathing;
- hides that follower through RuneLite's render listener;
- creates a client-side `RuneLiteObject` replacement;
- updates its location and orientation every client tick;
- builds the replacement from the model parts of `NpcID.DOM_BOSS_BURROWED`;
- scales the burrowed Doom from its native 5x5 footprint to a 1x1 pet-sized visual;
- anchors the replacement to the follower tile's terrain height and lifts it above the terrain to compensate for the burrowed boss model/animations;
- uses `AnimationID.DOM_BURROW_IDLE` while stationary;
- uses `AnimationID.DOM_BURROWED_MOVEMENT` while moving;
- follows the same `setAnimation` and looping path used by `pet-to-npc-transmog`;
- rewrites the hidden follower's right-click menu to use the actions and name from `NpcID.DOM_PET`.

Because the right-click menu remains attached to the real hidden follower, the simulated Dom entries still act on the actual follower during testing.

## Final mode

After the test transmog is visually confirmed, disabling `TEST_MODE` limits the source follower to:

- `NpcID.DOM_PET`
- `NpcID.POH_DOM_PET`

The reference `pet-to-npc-transmog` repository is read-only for this project; Racecar contains the implementation changes.
