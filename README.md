# Racecar

Racecar is a RuneLite plugin that transmogrifies a follower pet into the burrowed form of the Doom of Mokhaiotl.

## Current test mode

The plugin currently targets the **Pug** follower so the transmog can be tested without owning Dom.

While a Pug is following the local player, Racecar:

- hides the Pug's normal 3D render;
- renders the Doom of Mokhaiotl's burrowed NPC model at the Pug's location;
- preserves the real follower's server-driven pathing and orientation;
- uses Doom's burrow-idle animation while stationary;
- uses Doom's burrowed-movement animation while the follower is moving.

The target model is built at runtime from RuneLite's `NpcID.DOM_BOSS_BURROWED` composition rather than hardcoded model IDs.

## Final mode

Once testing is complete, set `TEST_WITH_PUG` in `Racecar.java` to `false`. The source follower then becomes either of the real Dom pet NPC variants:

- `NpcID.DOM_PET`
- `NpcID.POH_DOM_PET`
