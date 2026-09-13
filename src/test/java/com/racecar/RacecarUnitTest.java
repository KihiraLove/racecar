package com.racecar;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.gpu.GpuPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class RacecarUnitTest
{
	private final PluginManager pluginManager = mock(PluginManager.class);
	private final GpuPlugin builtin = mock(GpuPlugin.class);
	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final RenderCallbackManager callbacks = mock(RenderCallbackManager.class);
	private final ConfigManager configManager = mock(ConfigManager.class);
	private final NPC follower = mock(NPC.class);
	private final Model original = mock(Model.class);
	private final DrawCallbacks renderer = mock(DrawCallbacks.class);
	private final Menu menu = mock(Menu.class);
	private final MenuEntry examine = mock(MenuEntry.class);
	private final MenuEntry metamorphosis = mock(MenuEntry.class, RETURNS_SELF);
	private final MenuEntry emote = mock(MenuEntry.class, RETURNS_SELF);
	private Racecar plugin;
	private RenderCallback callback;

	@Before
	public void setUp()
	{
		when(pluginManager.getPlugins()).thenReturn(java.util.Collections.singletonList(builtin));
		plugin = new Racecar();
		Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(client);
				bind(PluginManager.class).toInstance(pluginManager);
				bind(ClientThread.class).toInstance(clientThread);
				bind(RenderCallbackManager.class).toInstance(callbacks);
				bind(ConfigManager.class).toInstance(configManager);
			}
		}).injectMembers(plugin);
		doAnswer(invocation ->
		{
			invocation.getArgument(0, Runnable.class).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		doAnswer(invocation ->
		{
			when(client.getDrawCallbacks()).thenReturn(invocation.getArgument(0));
			return null;
		}).when(client).setDrawCallbacks(any());
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

		NPCComposition definition = mock(NPCComposition.class);
		when(definition.getModels()).thenReturn(new int[]{1});
		when(definition.getSize()).thenReturn(5);
		when(definition.getWidthScale()).thenReturn(128);
		when(definition.getHeightScale()).thenReturn(128);
		when(client.getNpcDefinition(NpcID.DOM_BOSS_BURROWED)).thenReturn(definition);
		when(client.getNpcDefinition(NpcID.DOM_BOSS)).thenReturn(definition);
		ModelData modelData = mock(ModelData.class);
		when(client.loadModelData(1)).thenReturn(modelData);
		when(client.mergeModels(any(ModelData[].class))).thenReturn(modelData);
		when(modelData.light()).thenReturn(mock(Model.class));
		Animation animation = mock(Animation.class);
		when(animation.isMayaAnim()).thenReturn(true);
		when(animation.getDuration()).thenReturn(2);
		when(client.loadAnimation(anyInt())).thenReturn(animation);

		when(client.getMenu()).thenReturn(menu);
		when(examine.getNpc()).thenReturn(follower);
		when(examine.getType()).thenReturn(MenuAction.EXAMINE_NPC);
		when(examine.getTarget()).thenReturn("Dom");
		when(menu.createMenuEntry(-1)).thenReturn(metamorphosis);
		when(menu.createMenuEntry(-2)).thenReturn(emote);
		plugin.startUp();
		ArgumentCaptor<RenderCallback> captor = ArgumentCaptor.forClass(RenderCallback.class);
		verify(callbacks).register(captor.capture());
		callback = captor.getValue();
	}

	@After
	public void tearDown()
	{
		plugin.shutDown();
	}

	@Test
	public void alternativeGpuDrawsReplacementAndKeepsOriginalPicking()
	{
		plugin.onClientTick(new ClientTick());
		assertTrue(client.getDrawCallbacks() instanceof PetDrawCallbacks);
		verify(client).registerRuneLiteObject(any(RuneLiteObjectController.class));
		assertFalse(drawsFollower());
		assertTrue(callback.addEntity(follower, false));
		client.getDrawCallbacks().draw(null, null, follower, 512, 6400, -20, 6500, 123L);
		verify(client).checkClickbox(null, original, 512, 6400, -20, 6500, 123L);
		verify(renderer, never()).draw(null, null, follower, 512, 6400, -20, 6500, 123L);
	}

	@Test
	public void decoratedBuiltinGpuKeepsCallbacksAcrossDecoratorTogglesAndShutdown()
	{
		when(pluginManager.isPluginActive(builtin)).thenReturn(true);
		DrawCallbacks decorated = mock(DrawCallbacks.class);
		for (DrawCallbacks current : new DrawCallbacks[]{decorated, builtin, decorated})
		{
			when(client.getDrawCallbacks()).thenReturn(current);
			plugin.onClientTick(new ClientTick());
			assertSame(current, client.getDrawCallbacks());
			assertFalse(drawsFollower());
		}
		plugin.shutDown();
		assertSame(decorated, client.getDrawCallbacks());
		assertTrue(drawsFollower());
		verify(client, never()).setDrawCallbacks(any());
	}

	@Test
	public void builtinActivationRemovesAnAlreadyInstalledAdapter()
	{
		DrawCallbacks decorated = mock(DrawCallbacks.class);
		when(client.getDrawCallbacks()).thenReturn(decorated);
		plugin.onClientTick(new ClientTick());
		DrawCallbacks adapter = client.getDrawCallbacks();
		assertTrue(adapter instanceof PetDrawCallbacks);

		when(pluginManager.isPluginActive(builtin)).thenReturn(true);
		plugin.onClientTick(new ClientTick());
		assertSame(decorated, client.getDrawCallbacks());
		assertFalse(drawsFollower());
		adapter.draw(null, null, follower, 0, 1, 2, 3, 4L);
		verify(decorated).draw(null, null, follower, 0, 1, 2, 3, 4L);
	}

	@Test
	public void switchingBetweenAlternativeAndDecoratedBuiltinPreservesTheCurrentRenderer()
	{
		DrawCallbacks alternative = mock(DrawCallbacks.class);
		when(client.getDrawCallbacks()).thenReturn(alternative);
		plugin.onClientTick(new ClientTick());
		DrawCallbacks oldAdapter = client.getDrawCallbacks();

		DrawCallbacks decorated = mock(DrawCallbacks.class);
		when(pluginManager.isPluginActive(builtin)).thenReturn(true);
		when(client.getDrawCallbacks()).thenReturn(decorated);
		clearInvocations(client);
		plugin.onClientTick(new ClientTick());
		assertSame(decorated, client.getDrawCallbacks());
		verify(client, never()).setDrawCallbacks(any());
		oldAdapter.draw(null, null, follower, 0, 1, 2, 3, 4L);
		verify(alternative).draw(null, null, follower, 0, 1, 2, 3, 4L);

		when(pluginManager.isPluginActive(builtin)).thenReturn(false);
		when(client.getDrawCallbacks()).thenReturn(alternative);
		plugin.onClientTick(new ClientTick());
		assertSame(alternative, ((PetDrawCallbacks) client.getDrawCallbacks()).getDelegate());
		assertFalse(drawsFollower());
	}

	@Test
	public void builtinGpuKeepsItsIdentity()
	{
		DrawCallbacks builtin = mock(GpuPlugin.class);
		when(client.getDrawCallbacks()).thenReturn(builtin);
		plugin.onClientTick(new ClientTick());
		assertSame(builtin, client.getDrawCallbacks());
		verify(client, never()).setDrawCallbacks(any());
		assertFalse(drawsFollower());
	}

	@Test
	public void softwareModeRestoresOriginalVisual()
	{
		plugin.onClientTick(new ClientTick());
		when(client.isGpu()).thenReturn(false);
		plugin.onClientTick(new ClientTick());
		assertSame(renderer, client.getDrawCallbacks());
		assertTrue(drawsFollower());
		plugin.onMenuEntryAdded(new MenuEntryAdded(examine));
		verify(menu, never()).createMenuEntry(anyInt());
	}

	@Test
	public void rendererSwitchAndStopDoNotResurrectOldCallbacks()
	{
		plugin.onClientTick(new ClientTick());
		DrawCallbacks oldAdapter = client.getDrawCallbacks();
		DrawCallbacks next = mock(DrawCallbacks.class);
		when(client.getDrawCallbacks()).thenReturn(next);
		plugin.onClientTick(new ClientTick());
		assertSame(next, ((PetDrawCallbacks) client.getDrawCallbacks()).getDelegate());
		oldAdapter.draw(null, null, follower, 0, 1, 2, 3, 4L);
		verify(renderer).draw(null, null, follower, 0, 1, 2, 3, 4L);
		when(client.getDrawCallbacks()).thenReturn(null);
		plugin.onClientTick(new ClientTick());
		assertTrue(drawsFollower());
		verify(client, never()).setDrawCallbacks(renderer);
		verify(client, never()).setDrawCallbacks(next);
	}

	@Test
	public void anotherPluginsWrapperDoesNotCauseRepeatedWrapping()
	{
		plugin.onClientTick(new ClientTick());
		DrawCallbacks adapter = client.getDrawCallbacks();
		DrawCallbacks outer = mock(DrawCallbacks.class, withSettings().extraInterfaces(Supplier.class));
		doReturn(adapter).when((Supplier<?>) outer).get();
		when(client.getDrawCallbacks()).thenReturn(outer);
		clearInvocations(client);
		for (int i = 0; i < 100; i++)
		{
			plugin.onClientTick(new ClientTick());
		}
		assertSame(outer, client.getDrawCallbacks());
		verify(client, never()).setDrawCallbacks(any());
		plugin.shutDown();
		assertSame(outer, client.getDrawCallbacks());
		adapter.draw(null, null, follower, 0, 1, 2, 3, 4L);
		verify(renderer).draw(null, null, follower, 0, 1, 2, 3, 4L);
	}

	@Test
	public void domHouseVariantUsesNpcIdRatherThanName()
	{
		when(follower.getId()).thenReturn(NpcID.POH_DOM_PET);
		when(follower.getName()).thenReturn(null);
		plugin.onClientTick(new ClientTick());
		assertFalse(drawsFollower());
		plugin.onMenuEntryAdded(new MenuEntryAdded(examine));
		verify(metamorphosis).setOption("Metamorphosis");
		verify(emote).setOption("Emote");
	}

	@Test
	public void yamiTestModeSupportsFollowerAndHouseVariant()
	{
		when(follower.getName()).thenReturn("Yami");
		for (int id : new int[]{NpcID.YAMA_PET, NpcID.POH_YAMA_PET})
		{
			when(follower.getId()).thenReturn(id);
			plugin.onClientTick(new ClientTick());
			assertFalse(drawsFollower());
		}
		plugin.onMenuEntryAdded(new MenuEntryAdded(examine));
		verify(metamorphosis).setOption("Metamorphosis");
		verify(emote).setOption("Emote");
	}

	@Test
	public void otherPetsAreExcludedEvenIfNamedDom()
	{
		when(follower.getName()).thenReturn("Yami");
		when(follower.getId()).thenReturn(NpcID.POH_ROCK);
		plugin.onClientTick(new ClientTick());
		plugin.onMenuEntryAdded(new MenuEntryAdded(examine));
		when(follower.getName()).thenReturn("Dom");
		plugin.onClientTick(new ClientTick());
		plugin.onMenuEntryAdded(new MenuEntryAdded(examine));
		when(follower.getName()).thenReturn(null);
		plugin.onClientTick(new ClientTick());
		assertTrue(drawsFollower());
		verify(client, never()).registerRuneLiteObject(any());
		verify(menu, never()).createMenuEntry(anyInt());
	}

	@Test
	public void someoneElsesDomDoesNotGetMenuOptions()
	{
		NPC other = mock(NPC.class);
		when(other.getName()).thenReturn("Dom");
		when(other.getId()).thenReturn(NpcID.DOM_PET);
		when(examine.getNpc()).thenReturn(other);
		plugin.onMenuEntryAdded(new MenuEntryAdded(examine));
		verify(menu, never()).createMenuEntry(anyInt());
	}

	@Test
	public void missingModelLeavesOriginalVisibleWithoutAnAdapter()
	{
		when(client.getNpcDefinition(NpcID.DOM_BOSS)).thenReturn(null);
		plugin.onClientTick(new ClientTick());
		assertTrue(drawsFollower());
		assertSame(renderer, client.getDrawCallbacks());
		verify(client, never()).registerRuneLiteObject(any());
	}

	@Test
	public void dismissingPetRestoresRenderer()
	{
		plugin.onClientTick(new ClientTick());
		when(client.getFollower()).thenReturn(null);
		plugin.onClientTick(new ClientTick());
		assertSame(renderer, client.getDrawCallbacks());
		assertTrue(drawsFollower());
	}

	@Test
	public void metamorphosisInstallsAdapterAndEmergingRemovesIt()
	{
		when(configManager.getConfiguration("racecar", "burrowed")).thenReturn("false");
		plugin.shutDown();
		plugin.startUp();
		plugin.onClientTick(new ClientTick());
		assertSame(renderer, client.getDrawCallbacks());
		plugin.onMenuEntryAdded(new MenuEntryAdded(examine));
		click(metamorphosis);
		assertFalse(drawsFollower());
		verify(client).loadAnimation(AnimationID.DOM_BURROW);
		ArgumentCaptor<RuneLiteObjectController> captor = ArgumentCaptor.forClass(RuneLiteObjectController.class);
		verify(client).registerRuneLiteObject(captor.capture());
		RuneLiteObjectController object = captor.getValue();
		object.tick(2);
		plugin.onClientTick(new ClientTick());
		clearInvocations(metamorphosis);
		plugin.onMenuEntryAdded(new MenuEntryAdded(examine));
		click(metamorphosis);
		verify(client).loadAnimation(AnimationID.DOM_BURROWED_EMERGE);
		object.tick(2);
		plugin.onClientTick(new ClientTick());
		assertTrue(drawsFollower());
		assertSame(renderer, client.getDrawCallbacks());
	}

	@Test
	public void emoteRemainsAvailableWithAlternativeRenderer()
	{
		plugin.onClientTick(new ClientTick());
		plugin.onMenuEntryAdded(new MenuEntryAdded(examine));
		click(emote);
		verify(client).loadAnimation(AnimationID.DOM_BURROWED_EXPLOSION);
		assertFalse(drawsFollower());
	}

	@SuppressWarnings("unchecked")
	private void click(MenuEntry entry)
	{
		ArgumentCaptor<Consumer<MenuEntry>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(entry).onClick(captor.capture());
		captor.getValue().accept(entry);
	}

	private boolean drawsFollower()
	{
		GameObject object = mock(GameObject.class);
		when(object.getRenderable()).thenReturn(follower);
		return callback.drawObject(null, object);
	}
}
