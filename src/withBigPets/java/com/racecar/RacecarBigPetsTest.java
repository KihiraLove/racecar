package com.racecar;

import com.bigpets.BigPets;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Local launcher built with -PwithBigPets. */
public class RacecarBigPetsTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(Racecar.class, BigPets.class);
		RuneLite.main(args);
	}
}
