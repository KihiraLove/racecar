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
		description = "Raises or lowers the pet-sized burrowed Doom model. Positive values raise the model.",
		position = 0
	)
	default int burrowedVerticalOffset()
	{
		return 10;
	}

	@Range(min = 50, max = 150)
	@ConfigItem(
		keyName = "modelScalePercent",
		name = "Scale (%)",
		description = "Scales the final pet-sized burrowed Doom model. 100 is the current calculated pet scale.",
		position = 1
	)
	default int modelScalePercent()
	{
		return 100;
	}
}
