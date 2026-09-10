package com.racecar;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(RacecarConfig.GROUP)
public interface RacecarConfig extends Config
{
	String GROUP = "racecar";

	@Range(min = -512, max = 512)
	@ConfigItem(
		keyName = "burrowedVerticalOffset",
		name = "Vertical offset",
		description = "Raises or lowers the rendered model without changing the follower tile. Positive values raise the model.",
		position = 0
	)
	default int burrowedVerticalOffset()
	{
		return 64;
	}

	@ConfigItem(
		keyName = "useDomPetModel",
		name = "Use Dom pet model",
		description = "Use Dom's pet-sized model instead of scaling the full Doom boss model. This tests whether the boss burrow animations are compatible with the pet rig.",
		position = 1
	)
	default boolean useDomPetModel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableBurrowAnimations",
		name = "Burrow animations",
		description = "Apply Doom's burrow idle and movement animations to the selected model.",
		position = 2
	)
	default boolean enableBurrowAnimations()
	{
		return true;
	}
}
