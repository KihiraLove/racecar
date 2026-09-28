package com.racecar;

import com.bigpets.BigPets;
import com.bigpets.BigPetsConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.gpu.GpuPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class CombinedPetsTest
{
	@Parameterized.Parameters(name = "renderer: {0}")
	public static Collection<Object[]> renderers()
	{
		return Arrays.asList(new Object[]{"builtin"}, new Object[]{"alternative"}, new Object[]{"decorated builtin"});
	}

	private final PluginManager pluginManager = mock(PluginManager.class);
	private final GpuPlugin builtin = mock(GpuPlugin.class);
	private final boolean builtinActive;
	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final ConfigManager configManager = mock(ConfigManager.class);
	private final BigPetsConfig config = mock(BigPetsConfig.class);
	private final EventBus eventBus = new EventBus(ex -> { throw new AssertionError(ex); });
	private final NPC follower = mock(NPC.class);
	private final Model original = mock(Model.class);
	private final Model animated = mock(Model.class);
	private final Set<RuneLiteObjectController> activeObjects = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Racecar racecar = new Racecar();
	private final TestBigPets bigPets = new TestBigPets();
	private final DrawCallbacks renderer;

	public CombinedPetsTest(String mode)
	{
		builtinActive = !mode.equals("alternative");
		renderer = mode.equals("builtin") ? builtin : mock(DrawCallbacks.class);
	}

	@Before
	public void setUp()
	{
		var injector = Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(client);
				bind(PluginManager.class).toInstance(pluginManager);
				bind(ClientThread.class).toInstance(clientThread);
				bind(ConfigManager.class).toInstance(configManager);
				bind(BigPetsConfig.class).toInstance(config);
				bind(EventBus.class).toInstance(eventBus);
				bind(RenderCallbackManager.class).toInstance(mock(RenderCallbackManager.class));
			}
		});
		injector.injectMembers(racecar);
		injector.injectMembers(bigPets);
		doAnswer(invocation ->
		{
			invocation.getArgument(0, Runnable.class).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		doAnswer(invocation ->
		{
			invocation.getArgument(0, Runnable.class).run();
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		doAnswer(invocation ->
		{
			when(client.getDrawCallbacks()).thenReturn(invocation.getArgument(0));
			return null;
		}).when(client).setDrawCallbacks(any());
		when(pluginManager.getPlugins()).thenReturn(java.util.Collections.singletonList(builtin));
		when(pluginManager.isPluginActive(builtin)).thenReturn(builtinActive);
		when(client.isClientThread()).thenReturn(true);
		when(client.isGpu()).thenReturn(true);
		when(client.getDrawCallbacks()).thenReturn(renderer);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getFollower()).thenReturn(follower);
		when(follower.getName()).thenReturn("Dom");
		when(follower.getId()).thenReturn(NpcID.DOM_PET);
		when(follower.getModel()).thenReturn(original);
		when(follower.getWorldView()).thenReturn(mock(WorldView.class));
		when(follower.getLocalLocation()).thenReturn(new LocalPoint(6400, 6400));
		when(configManager.getConfiguration("racecar", "burrowed")).thenReturn("true");
		when(config.petSizePercentage()).thenReturn(200);
		doAnswer(invocation ->
		{
			activeObjects.add(invocation.getArgument(0));
			return null;
		}).when(client).registerRuneLiteObject(any());
		doAnswer(invocation ->
		{
			activeObjects.remove(invocation.getArgument(0));
			return null;
		}).when(client).removeRuneLiteObject(any());
		when(client.isRuneLiteObjectRegistered(any())).thenAnswer(invocation -> activeObjects.contains(invocation.getArgument(0)));
		NPCComposition definition = mock(NPCComposition.class);
		when(definition.getModels()).thenReturn(new int[]{1});
		when(definition.getSize()).thenReturn(5);
		when(definition.getWidthScale()).thenReturn(128);
		when(definition.getHeightScale()).thenReturn(128);
		when(client.getNpcDefinition(NpcID.DOM_BOSS_BURROWED)).thenReturn(definition);
		when(client.getNpcDefinition(NpcID.DOM_BOSS)).thenReturn(definition);
		ModelData data = mock(ModelData.class);
		when(client.loadModelData(1)).thenReturn(data);
		when(client.mergeModels(any(ModelData[].class))).thenReturn(data);
		when(data.light()).thenReturn(mock(Model.class));
		when(client.loadAnimation(anyInt())).thenReturn(mock(Animation.class));
		when(client.applyTransformations(any(), any(), anyInt(), any(), anyInt())).thenReturn(animated);
	}

	@After
	public void tearDown()
	{
		bigPets.stop();
		racecar.shutDown();
		eventBus.unregister(bigPets);
		eventBus.unregister(racecar);
	}

	@Test
	public void startingRacecarAfterBigPetsReplacesTheNormalCopy()
	{
		startBigPets();
		eventBus.post(new BeforeRender());
		RuneLiteObjectController normalVisual = controller();
		assertTrue(normalVisual instanceof RuneLiteObject);
		assertNotNull(normalVisual.getModel());
		verify(animated).scale(256, 256, 256);
		startRacecar();
		eventBus.post(new ClientTick());
		eventBus.post(new BeforeRender());
		verify(client).removeRuneLiteObject(normalVisual);
		controller().getModel();
		verify(animated).scale(104, 104, 104);
		verify(client).registerRuneLiteObject(any(RuneLiteObject.class));
	}

	@Test
	public void startingBigPetsAfterRacecarFindsAndScalesTheExistingVisual()
	{
		startRacecar();
		eventBus.post(new ClientTick());
		startBigPets();
		eventBus.post(new BeforeRender());
		controller().getModel();
		verify(animated).scale(104, 104, 104);
		verify(client, never()).registerRuneLiteObject(any(RuneLiteObject.class));
	}

	@Test
	public void yamiUsesBigPetsVisualWhenRacecarTestModeIsDisabled()
	{
		when(follower.getId()).thenReturn(NpcID.YAMA_PET);
		when(follower.getName()).thenReturn("Yami");
		startBoth();
		controller().getModel();
		verify(animated).scale(256, 256, 256);
		verify(client, never()).registerRuneLiteObject(any(RacecarObject.class));
	}

	@Test
	public void sizeChangesAndZeroAffectOnlyTheRacecarVisual()
	{
		startBoth();
		RuneLiteObjectController car = controller();
		for (int size : new int[]{50, 100, 500})
		{
			when(config.petSizePercentage()).thenReturn(size);
			eventBus.post(new BeforeRender());
			assertNotNull(car.getModel());
			int scale = Math.round(52 * size / 100.0f);
			verify(animated).scale(scale, scale, scale);
		}
		when(config.petSizePercentage()).thenReturn(0);
		eventBus.post(new BeforeRender());
		assertNull(car.getModel());
		verify(client, never()).registerRuneLiteObject(any(RuneLiteObject.class));
	}

	@Test
	public void disablingBigPetsResetsScaleAndReenablingFindsTheCarAgain()
	{
		startBoth();
		RuneLiteObjectController car = controller();
		eventBus.unregister(bigPets);
		bigPets.stop();
		car.getModel();
		verify(animated).scale(52, 52, 52);
		startBigPets();
		eventBus.post(new BeforeRender());
		car.getModel();
		verify(animated).scale(104, 104, 104);
	}

	@Test
	public void disablingRacecarReturnsSizingToTheOriginalPet()
	{
		startBoth();
		eventBus.unregister(racecar);
		racecar.shutDown();
		eventBus.post(new BeforeRender());
		verify(client).registerRuneLiteObject(any(RuneLiteObject.class));
		RuneLiteObjectController normalVisual = controller();
		assertTrue(normalVisual instanceof RuneLiteObject);
		assertNotNull(normalVisual.getModel());
		verify(animated).scale(256, 256, 256);
	}

	@Test
	public void domRemainsResizableWithBothFiltersEnabled()
	{
		when(config.filterCatsAndDogs()).thenReturn(true);
		when(config.filterQuestAndEventPets()).thenReturn(true);
		startBoth();
		controller().getModel();
		verify(animated).scale(104, 104, 104);
		verify(client, never()).registerRuneLiteObject(any(RuneLiteObject.class));
	}

	@Test
	public void repeatedFramesDoNotCompoundScaleOrGrowTheAdapterChain()
	{
		startBoth();
		RuneLiteObjectController car = controller();
		DrawCallbacks chain = client.getDrawCallbacks();
		clearInvocations(client);
		for (int i = 0; i < 100; i++)
		{
			eventBus.post(new ClientTick());
			eventBus.post(new BeforeRender());
			car.getModel();
		}
		assertSame(chain, client.getDrawCallbacks());
		verify(client, never()).setDrawCallbacks(any());
		verify(animated, times(100)).scale(104, 104, 104);
		assertEquals(120, car.getRadius());
	}

	private void startBoth()
	{
		startBigPets();
		startRacecar();
		eventBus.post(new ClientTick());
		eventBus.post(new BeforeRender());
	}

	private void startBigPets()
	{
		bigPets.start();
		eventBus.register(bigPets);
	}

	private void startRacecar()
	{
		racecar.startUp();
		eventBus.register(racecar);
	}

	private RuneLiteObjectController controller()
	{
		assertEquals(1, activeObjects.size());
		if (builtinActive)
		{
			assertSame(renderer, client.getDrawCallbacks());
			verify(client, never()).setDrawCallbacks(any());
		}
		return activeObjects.iterator().next();
	}

	public static class TestBigPets extends BigPets
	{
		void start()
		{
			super.startUp();
		}

		void stop()
		{
			super.shutDown();
		}
	}
}
