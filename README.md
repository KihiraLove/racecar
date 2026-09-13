# Racecar

Transmog your Dom pet to the burrowed form of the Doom of Mokhaiotl. It requires **GPU**, **GPU Experimental**, **GPU Legacy**, or **117 HD**, and only affects your own pet.

Only your current Dom follower is eligible, identified by its NPC ID (`DOM_PET` or `POH_DOM_PET`). Other pets and other players' followers are left unchanged.

New right click menu options:
- `Metamorphosis`to change between regular Dom and car phase
- `Emote` car phase slam animation (only while burrowed)

## Rendering
The real follower remains in the scene, with its original clickbox and native menu. The plugin only hides its drawing while a replacement model is active.

Modern renderers use RuneLite's object rendering callback. Legacy renderers use the same forwarding adapter tested in Big Pets: it checks the original follower's clickbox, skips its original visual, and forwards other drawing calls. Software rendering leaves the follower unchanged.

The adapters expose their delegate through `Supplier<DrawCallbacks>` so Racecar and Big Pets can recognize an existing adapter anywhere in the forwarding chain. When running both, use the updated Big Pets build containing this companion fix.

## Big Pets integration

With both updated plugins enabled, Big Pets resizes the active Racecar visual instead of drawing a second copy of the original follower. **200%** doubles Racecar's normal visual size, **100%** restores it, and **0%** hides it while retaining the original follower's clickbox and menus. Burrowing, emerging, and Emote use the same size. Big Pets filters still apply to the source pet.

Disabling Big Pets restores Racecar's normal size. Emerging or disabling Racecar returns sizing to the normal pet. Both plugins can still run independently.

## Combined local development build

From the Racecar directory, with Big Pets in the sibling `../big-pets` directory:

```powershell
./gradlew :run -PwithBigPets
```

This compiles both local plugins and opens **one** development client with both available. Use the explicit `:run` root task so Gradle does not also run Big Pets' standalone launcher. If Big Pets is elsewhere, add `-PbigPetsDir=path/to/big-pets`.

The combined launcher is `RacecarBigPetsTest`; it is available when the Gradle project is imported with `withBigPets` enabled. Without that property, the existing standalone Racecar build is unchanged. `./gradlew test -PwithBigPets` runs both plugins' tests and the combined integration tests. `./gradlew :shadowJar -PwithBigPets` builds a single development launcher JAR containing both plugins.

## Checking this build

The user confirmed the combined visual behavior worked flawlessly during development testing. The temporary test-pet targeting has been removed; automated tests now exercise Dom-only targeting and the combined integration.

For future regressions, use Dom with both plugins enabled: check 200% with Metamorphosis and Emote, movement, 0%, 100%, emerging, toggling each plugin, and renderer switching. Other pets should not receive Racecar's menu options or transmog.
