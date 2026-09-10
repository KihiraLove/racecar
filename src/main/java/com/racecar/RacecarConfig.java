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
		description = "Raises or lowers the burrowed Doom model without changing the follower tile. Positive values raise the model.",
		position = 0
	)
	default int burrowedVerticalOffset()
	{
		return 64;
	}

	@ConfigItem(
		keyName = "enableBurrowAnimations",
		name = "Burrow animations",
		description = "Enable Doom's burrow idle and movement animations. Disabled by default while diagnosing the model-position offset.",
		position = 1
	)
	default boolean enableBurrowAnimations()
	{
		return false;
	}
}
