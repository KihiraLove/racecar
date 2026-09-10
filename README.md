# Racecar

Racecar is a RuneLite pet transmog plugin intended to replace **Dom** with the burrowed form of the Doom of Mokhaiotl.

## Current test mode

The current build is deliberately in test mode. It only transmogs a follower whose in-game name is **Yami**, so other pets can be taken out without Racecar affecting them.

The implementation is based on the known-working code in the `pet-to-npc-transmog` plugin:

- uses the real `client.getFollower()` NPC for all server-driven following/pathing;
- hides that follower through RuneLite's render listener;
- creates a client-side `RuneLiteObject` replacement;
- keeps the replacement on the follower's real local tile and orientation every client tick;
- can render either `NpcID.DOM_PET` directly or the full `NpcID.DOM_BOSS_BURROWED` model;
- only scales the full boss model from its native 5x5 footprint to a 1x1 pet-sized visual;
- applies vertical correction in model space so height tuning cannot move the replacement to another tile;
- can apply `AnimationID.DOM_BURROW_IDLE` while stationary;
- can apply `AnimationID.DOM_BURROWED_MOVEMENT` while moving;
- rewrites the hidden follower's right-click menu to use the actions and name from `NpcID.DOM_PET`.

Because the right-click menu remains attached to the real hidden follower, the simulated Dom entries still act on the actual follower during testing.

## Configuration

`Vertical offset` raises or lowers the rendered model relative to the follower tile. Positive values raise the model. The default is `64`, with a tuning range of `-512` to `512`.

`Use Dom pet model` defaults to enabled. This loads the actual `NpcID.DOM_PET` model at its native pet scale instead of scaling down the full Doom boss model. It exists specifically to test whether the boss burrow animations are compatible with Dom's pet rig.

`Burrow animations` defaults to enabled. It applies `DOM_BURROW_IDLE` and `DOM_BURROWED_MOVEMENT` to whichever model source is selected.

For the current compatibility test, leave both `Use Dom pet model` and `Burrow animations` enabled. If Dom folds into the expected burrowed/car form, the pet and boss models are compatible enough to use the pet model directly. If it deforms or remains incorrect, disable `Use Dom pet model` to return to the scaled boss-model path.

## Final mode

After the test transmog is visually confirmed, disabling `TEST_MODE` limits the source follower to:

- `NpcID.DOM_PET`
- `NpcID.POH_DOM_PET`

The reference `pet-to-npc-transmog` repository is read-only for this project; Racecar contains the implementation changes.
