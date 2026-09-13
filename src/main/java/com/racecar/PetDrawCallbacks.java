package com.racecar;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.experimental.Delegate;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Projection;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.hooks.DrawCallbacks;

final class PetDrawCallbacks implements DrawCallbacks, Supplier<DrawCallbacks>
{
	private final Client client;
	private final Predicate<Renderable> hidden;
	private volatile boolean active = true;

	@Getter
	@Delegate
	private final DrawCallbacks delegate;

	PetDrawCallbacks(Client client, DrawCallbacks delegate, Predicate<Renderable> hidden)
	{
		this.client = client;
		this.delegate = delegate;
		this.hidden = hidden;
	}

	void deactivate()
	{
		active = false;
	}

	@Override
	public DrawCallbacks get()
	{
		return delegate;
	}

	boolean isInstalled(DrawCallbacks current)
	{
		if (current == this)
		{
			return true;
		}
		Set<DrawCallbacks> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		while (current instanceof Supplier<?> && visited.add(current))
		{
			Object next = ((Supplier<?>) current).get();
			if (next == this)
			{
				return true;
			}
			if (!(next instanceof DrawCallbacks))
			{
				break;
			}
			current = (DrawCallbacks) next;
		}
		return false;
	}

	@Override
	public void draw(Projection projection, Scene scene, Renderable renderable,
		int orientation, int x, int y, int z, long hash)
	{
		if (!active || !hidden.test(renderable))
		{
			delegate.draw(projection, scene, renderable, orientation, x, y, z, hash);
			return;
		}

		Model model = renderable.getModel();
		if (model != null)
		{
			model.calculateBoundsCylinder();
			renderable.setModelHeight(model.getModelHeight());
			client.checkClickbox(projection, model, orientation, x, y, z, hash);
		}
	}
}
