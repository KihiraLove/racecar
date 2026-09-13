package com.racecar;

import java.util.Collections;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Projection;
import net.runelite.api.Scene;
import net.runelite.api.hooks.DrawCallbacks;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class PetDrawCallbacksTest
{
	private final Client client = mock(Client.class);
	private final DrawCallbacks renderer = mock(DrawCallbacks.class);
	private final NPC pet = mock(NPC.class);
	private final Model model = mock(Model.class);
	private final Scene scene = mock(Scene.class);
	private final Projection projection = mock(Projection.class);
	private final PetDrawCallbacks callbacks = new PetDrawCallbacks(client, renderer, r -> r == pet);

	@Test
	public void findsItselfThroughAnotherPluginsAdapter()
	{
		DrawCallbacks outer = mock(DrawCallbacks.class, withSettings().extraInterfaces(Supplier.class));
		doReturn(callbacks).when((Supplier<?>) outer).get();
		assertTrue(callbacks.isInstalled(outer));
		assertTrue(callbacks.isInstalled(callbacks));
		assertFalse(callbacks.isInstalled(renderer));
		assertFalse(callbacks.isInstalled(null));
	}

	@Test
	public void malformedDelegateChainsDoNotLoopForever()
	{
		DrawCallbacks first = mock(DrawCallbacks.class, withSettings().extraInterfaces(Supplier.class));
		DrawCallbacks second = mock(DrawCallbacks.class, withSettings().extraInterfaces(Supplier.class));
		doReturn(second).when((Supplier<?>) first).get();
		doReturn(first).when((Supplier<?>) second).get();
		assertFalse(callbacks.isInstalled(first));
		doReturn("unrelated supplier value").when((Supplier<?>) second).get();
		assertFalse(callbacks.isInstalled(first));
	}

	@Test
	public void legacyPickingUsesTheOriginalModelAndFullDrawCoordinates()
	{
		when(pet.getModel()).thenReturn(model);
		when(model.getModelHeight()).thenReturn(42);
		callbacks.draw(projection, scene, pet, 512, 6400, -20, 6500, 123L);
		InOrder order = inOrder(model, pet, client);
		order.verify(pet).getModel();
		order.verify(model).calculateBoundsCylinder();
		order.verify(model).getModelHeight();
		order.verify(pet).setModelHeight(42);
		order.verify(client).checkClickbox(projection, model, 512, 6400, -20, 6500, 123L);
		verifyNoInteractions(renderer);
	}

	@Test
	public void missingModelDoesNotAttemptPicking()
	{
		callbacks.draw(projection, scene, pet, 512, 6400, -20, 6500, 123L);
		verifyNoInteractions(client, renderer);
	}

	@Test
	public void replacementsAndUnrelatedNpcsReachTheRenderer()
	{
		GraphicsObject replacement = mock(GraphicsObject.class);
		NPC other = mock(NPC.class);
		callbacks.draw(projection, scene, replacement, 512, 6400, -20, 6500, -1L);
		callbacks.draw(projection, scene, other, 1024, 6300, -30, 6600, 456L);
		verify(renderer).draw(projection, scene, replacement, 512, 6400, -20, 6500, -1L);
		verify(renderer).draw(projection, scene, other, 1024, 6300, -30, 6600, 456L);
		verifyNoInteractions(client);
	}

	@Test
	public void modernCallbacksKeepTheirOverloadsAndRenderThreadId()
	{
		GameObject object = mock(GameObject.class);
		callbacks.drawDynamic(3, projection, scene, object, pet, model, 512, 6400, -20, 6500);
		callbacks.drawDynamic(projection, scene, object, pet, model, 512, 6400, -20, 6500);
		callbacks.drawTemp(projection, scene, object, model, 512, 6400, -20, 6500);
		verify(renderer).drawDynamic(3, projection, scene, object, pet, model, 512, 6400, -20, 6500);
		verify(renderer).drawDynamic(projection, scene, object, pet, model, 512, 6400, -20, 6500);
		verify(renderer).drawTemp(projection, scene, object, model, 512, 6400, -20, 6500);
		verifyNoInteractions(client);
	}

	@Test
	public void sceneLifecycleAndFrustumResultsAreForwarded()
	{
		when(renderer.zoneInFrustum(1, 2, 3, 4)).thenReturn(true);
		callbacks.loadScene(scene);
		callbacks.swapScene(scene);
		callbacks.preSceneDraw(scene, projection, 1, 2, 3, 4, 5, 0, 1, 2, Collections.emptySet());
		callbacks.drawPass(projection, scene, DrawCallbacks.PASS_ALPHA);
		callbacks.postSceneDraw(scene);
		callbacks.draw(123);
		assertTrue(callbacks.zoneInFrustum(1, 2, 3, 4));
		verify(renderer).loadScene(scene);
		verify(renderer).swapScene(scene);
		verify(renderer).preSceneDraw(scene, projection, 1, 2, 3, 4, 5, 0, 1, 2, Collections.emptySet());
		verify(renderer).drawPass(projection, scene, DrawCallbacks.PASS_ALPHA);
		verify(renderer).postSceneDraw(scene);
		verify(renderer).draw(123);
	}
}
