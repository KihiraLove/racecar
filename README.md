# Racecar

Transmog your Dom pet to the burrowed form of the Doom of Mokhaiotl. It requires **GPU**, **GPU Experimental**, **GPU Legacy**, or **117 HD**. Only affects your own pet.

Only your current Dom follower is eligible, identified by its NPC ID (`DOM_PET` or `POH_DOM_PET`). Other pets and other players' followers are left unchanged.

New right click menu options:
- `Metamorphosis`to change between regular Dom and car phase
- `Emote` car phase slam animation (only while burrowed)

## Rendering
Racecar draws an alternative model while retaining the original NPC for its clickbox and right-click menu.
Requires a renderer plugin, either:
- Built-in GPU
- GPU (Experimental)
- 117 HD

For these three zone renderer use RuneLite's object rendering callback to hide the original visual.
- GPU (Legacy)
- 117 HD's legacy

These use a forwarding adapter that skips the original visual but performs click detection with the model.

## Big Pets integration

With both updated plugins enabled, Big Pets ([Plugin Hub](https://runelite.net/plugin-hub/show/big-pets))([GitHub](https://github.com/KihiraLove/big-pets)) resizes the Metamorphed follower.