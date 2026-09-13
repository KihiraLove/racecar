package com.racecar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.gpu.GpuPlugin;

@Slf4j
@PluginDescriptor(
	name = "Racecar",
	description = "Adds a Metamorphosis option to Dom to change it to the burrowed phase of Doom. Requires a GPU renderer (GPU, 117 HD, GPU(Experimental), GPU (Legacy)",
	tags = {"doom", "mokhaiotl", "dom", "pet", "transmog", "car", "metamorph", "metamorphosis"}
)
public class Racecar extends Plugin
{
	private static final String CONFIG_GROUP = "racecar";
	private static final String BURROWED_KEY = "burrowed";
	private static final String METAMORPHOSIS = "Metamorphosis";
	private static final String EMOTE = "Emote";
	private static final boolean YAMI_TEST_MODE = false;

	private static final int TARGET_NPC_ID = NpcID.DOM_BOSS_BURROWED;
	private static final int IDLE_ANIMATION_ID = AnimationID.DOM_BURROW_IDLE;
	private static final int MOVEMENT_ANIMATION_ID = AnimationID.DOM_BURROWED_MOVEMENT;

	private static final Set<RacecarObject> ACTIVE_OBJECTS = ConcurrentHashMap.newKeySet();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private EventBus eventBus;

	private final RenderCallback renderCallback = new RenderCallback()
	{
		@Override
		public boolean drawObject(Scene scene, TileObject object)
		{
			return !(object instanceof GameObject)
				|| !isHiddenFollower(((GameObject) object).getRenderable());
		}
	};

	private volatile boolean transmogInitialized;
	private volatile boolean running;
	private boolean burrowed;
	private ConfigProfile profile;
	private NPC sourceFollower;
	private MovementState movementState;
	private RacecarObject transmogObject;
	private PetDrawCallbacks petDrawCallbacks;
	private Plugin gpuPlugin;

	@Override
	protected void startUp()
	{
		gpuPlugin = pluginManager.getPlugins().stream()
			.filter(plugin -> plugin instanceof GpuPlugin)
			.findFirst().orElse(null);
		log.debug("Racecar started");
		clientThread.invokeLater(() ->
		{
			running = true;
			loadProfile();
			renderCallbackManager.register(renderCallback);
		});
	}

	@Override
	protected void shutDown()
	{
		log.debug("Racecar stopped");
		running = false;
		clientThread.invokeLater(() ->
		{
			running = false;
			try
			{
				clearTransmog();
			}
			finally
			{
				renderCallbackManager.unregister(renderCallback);
			}
		});
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (!running)
		{
			return;
		}

		if (profile != configManager.getProfile())
		{
			loadProfile();
		}

		NPC follower = client.getFollower();

		if (!canTransmog(follower))
		{
			if (transmogInitialized || sourceFollower != null || transmogObject != null)
			{
				clearTransmog();
			}
			restoreDrawCallbacks();
			return;
		}

		if (sourceFollower != follower)
		{
			clearTransmog();
			sourceFollower = follower;
		}

		if (!transmogInitialized)
		{
			if (!burrowed
				|| !initializeTransmogObject(follower, movementAnimationId(follower), RacecarObject.Form.BURROWED))
			{
				return;
			}
		}

		updateDrawCallbacks();
		setTransmogLocation(follower);
		if (transmogObject.isPlayingAction())
		{
			if (!transmogObject.isAnimationFinished())
			{
				return;
			}
			if (!burrowed)
			{
				clearTransmog();
				return;
			}
		}
		updateFollowerMovement(follower);
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		MenuEntry entry = event.getMenuEntry();
		NPC follower = entry.getNpc();
		if (!running || entry.getType() != MenuAction.EXAMINE_NPC
			|| follower != client.getFollower() || !canTransmog(follower))
		{
			return;
		}

		ConfigProfile menuProfile = configManager.getProfile();
		client.getMenu().createMenuEntry(-1)
			.setOption(METAMORPHOSIS)
			.setTarget(entry.getTarget())
			.setIdentifier(entry.getIdentifier())
			.setWorldViewId(entry.getWorldViewId())
			.setType(MenuAction.RUNELITE)
			.onClick(clicked -> metamorphose(follower, menuProfile));

		if (profile == menuProfile && isBurrowedFollower(follower))
		{
			client.getMenu().createMenuEntry(-2)
				.setOption(EMOTE)
				.setTarget(entry.getTarget())
				.setIdentifier(entry.getIdentifier())
				.setWorldViewId(entry.getWorldViewId())
				.setType(MenuAction.RUNELITE)
				.onClick(clicked -> emote(follower, menuProfile));
		}
	}

	private boolean isBurrowedFollower(NPC follower)
	{
		return burrowed && sourceFollower == follower && transmogInitialized
			&& transmogObject != null && !transmogObject.isTransitioning();
	}

	private void emote(NPC follower, ConfigProfile menuProfile)
	{
		if (!running || menuProfile != configManager.getProfile() || profile != menuProfile
			|| follower != client.getFollower() || !canTransmog(follower)
			|| !isBurrowedFollower(follower) || transmogObject.isPlayingAction())
		{
			return;
		}

		if (setTransmogAnimation(AnimationID.DOM_BURROWED_EXPLOSION, RacecarObject.Form.EMOTE))
		{
			movementState = null;
		}
	}

	private void metamorphose(NPC follower, ConfigProfile menuProfile)
	{
		if (!running || menuProfile != configManager.getProfile()
			|| follower != client.getFollower() || !canTransmog(follower))
		{
			return;
		}
		if (profile != menuProfile)
		{
			loadProfile();
		}
		if (sourceFollower != follower)
		{
			clearTransmog();
			sourceFollower = follower;
		}
		if (transmogObject != null && transmogObject.isPlayingAction())
		{
			return;
		}

		boolean nextBurrowed = !burrowed;
		int animationId = nextBurrowed ? AnimationID.DOM_BURROW : AnimationID.DOM_BURROWED_EMERGE;
		if (transmogObject == null)
		{
			if (!initializeTransmogObject(follower, animationId, RacecarObject.Form.TRANSITION))
			{
				return;
			}
		}
		else if (!setTransmogAnimation(animationId, RacecarObject.Form.TRANSITION))
		{
			return;
		}

		movementState = null;
		burrowed = nextBurrowed;
		configManager.setConfiguration(CONFIG_GROUP, BURROWED_KEY, burrowed);
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		clientThread.invoke(this::loadProfile);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CONFIG_GROUP.equals(event.getGroup()) && BURROWED_KEY.equals(event.getKey())
			&& event.getProfile() == null)
		{
			clientThread.invoke(() ->
			{
				if (running && (profile != configManager.getProfile() || burrowed != readBurrowed()))
				{
					loadProfile();
				}
			});
		}
	}

	private void loadProfile()
	{
		if (running)
		{
			clearTransmog();
			profile = configManager.getProfile();
			burrowed = readBurrowed();
		}
	}

	private boolean readBurrowed()
	{
		return Boolean.parseBoolean(configManager.getConfiguration(CONFIG_GROUP, BURROWED_KEY));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			clearTransmog();
		}
	}

	private boolean canTransmog(NPC follower)
	{
		return client.getGameState() == GameState.LOGGED_IN
			&& client.isGpu() && client.getDrawCallbacks() != null && isSourceFollower(follower);
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		if (CONFIG_GROUP.equals(event.getNamespace()) && "request-pet-visual".equals(event.getName()))
		{
			clientThread.invoke(this::publishPetVisual);
		}
	}

	private void publishPetVisual()
	{
		if (!running || !transmogInitialized || transmogObject == null)
		{
			return;
		}
		RacecarObject object = transmogObject;
		NPC follower = sourceFollower;
		eventBus.post(new PluginMessage(CONFIG_GROUP, "pet-visual", Map.of(
			"npc", follower,
			"setSize", (IntConsumer) object::setSizePercentage,
			"isActive", (BooleanSupplier) () -> running && transmogInitialized
				&& transmogObject == object && client.getFollower() == follower)));
	}

	private boolean isHiddenFollower(Renderable renderable)
	{
		return running && transmogInitialized && renderable instanceof NPC && renderable == sourceFollower;
	}

	private void updateDrawCallbacks()
	{
		DrawCallbacks current = client.getDrawCallbacks();
		if (current instanceof GpuPlugin || gpuPlugin != null && pluginManager.isPluginActive(gpuPlugin))
		{
			restoreDrawCallbacks();
			return;
		}
		if (petDrawCallbacks != null && petDrawCallbacks.isInstalled(current))
		{
			return;
		}
		restoreDrawCallbacks();
		if (current != null && client.isGpu())
		{
			petDrawCallbacks = new PetDrawCallbacks(client, current, this::isHiddenFollower);
			client.setDrawCallbacks(petDrawCallbacks);
		}
	}

	private void restoreDrawCallbacks()
	{
		if (petDrawCallbacks != null)
		{
			petDrawCallbacks.deactivate();
			if (client.getDrawCallbacks() == petDrawCallbacks)
			{
				client.setDrawCallbacks(petDrawCallbacks.getDelegate());
			}
			petDrawCallbacks = null;
		}
	}

	private boolean isSourceFollower(NPC follower)
	{
		if (follower == null)
		{
			return false;
		}
		return follower.getId() == NpcID.DOM_PET || follower.getId() == NpcID.POH_DOM_PET
			|| YAMI_TEST_MODE && (follower.getId() == NpcID.YAMA_PET || follower.getId() == NpcID.POH_YAMA_PET);
	}

	private boolean initializeTransmogObject(NPC follower, int animationId, RacecarObject.Form form)
	{
		NPCComposition composition = client.getNpcDefinition(TARGET_NPC_ID);
		Model model = createDoomModel(composition);
		Model transitionModel = createDoomModel(client.getNpcDefinition(NpcID.DOM_BOSS));
		if (composition == null || model == null || transitionModel == null)
		{
			log.debug("Unable to create Racecar models for follower {} ({})",
				follower.getName(), follower.getId());
			return false;
		}

		int footprintSize = Math.max(1, composition.getSize());
		int horizontalScale = Math.max(1, Math.round((float) composition.getWidthScale() / footprintSize));
		int verticalScale = Math.max(1, Math.round((float) composition.getHeightScale() / footprintSize));

		transmogObject = new RacecarObject(client, model, transitionModel, horizontalScale, verticalScale);
		setTransmogLocation(follower);

		movementState = form == RacecarObject.Form.TRANSITION ? null : getMovementState(follower);
		if (!setTransmogAnimation(animationId, form))
		{
			transmogObject.clear();
			transmogObject = null;
			movementState = null;
			return false;
		}

		client.registerRuneLiteObject(transmogObject);
		ACTIVE_OBJECTS.add(transmogObject);
		transmogInitialized = true;
		updateDrawCallbacks();
		publishPetVisual();

		log.debug(
			"Racecar transmog initialized for follower {} ({}) using target NPC {}, base scale {}/{}",
			follower.getName(), follower.getId(), TARGET_NPC_ID, horizontalScale, verticalScale);
		return true;
	}

	private void setTransmogLocation(NPC follower)
	{
		if (transmogObject == null)
		{
			return;
		}

		WorldView worldView = follower.getWorldView();
		transmogObject.setLocation(follower.getLocalLocation(), worldView.getPlane());
		transmogObject.setZ(Perspective.getTileHeight(client, follower.getLocalLocation(), worldView.getPlane()));
		transmogObject.setOrientation(follower.getCurrentOrientation());
	}

	private void updateFollowerMovement(NPC follower)
	{
		MovementState newState = getMovementState(follower);
		if (newState == movementState)
		{
			return;
		}

		if (setTransmogAnimation(movementAnimationId(follower), RacecarObject.Form.BURROWED))
		{
			movementState = newState;
		}
	}

	private MovementState getMovementState(NPC follower)
	{
		return follower.getPoseAnimation() == follower.getWalkAnimation()
			? MovementState.MOVING
			: MovementState.STANDING;
	}

	private int movementAnimationId(NPC follower)
	{
		boolean moving = getMovementState(follower) == MovementState.MOVING;
		return moving ? MOVEMENT_ANIMATION_ID : IDLE_ANIMATION_ID;
	}

	private boolean setTransmogAnimation(int animationId, RacecarObject.Form form)
	{
		if (transmogObject == null)
		{
			return false;
		}

		Animation animation = client.loadAnimation(animationId);
		if (animation == null)
		{
			log.debug("Unable to load Racecar animation {}", animationId);
			return false;
		}

		transmogObject.setAnimation(animation, form);
		return true;
	}

	private Model createDoomModel(NPCComposition composition)
	{
		if (composition == null)
		{
			return null;
		}

		int[] modelIds = composition.getModels();
		if (modelIds == null || modelIds.length == 0)
		{
			return null;
		}

		ModelData[] modelDataArray = Arrays.stream(modelIds)
			.mapToObj(client::loadModelData)
			.toArray(ModelData[]::new);

		if (Arrays.stream(modelDataArray).anyMatch(Objects::isNull))
		{
			return null;
		}

		ModelData mergedModelData = client.mergeModels(modelDataArray);
		return mergedModelData == null ? null : mergedModelData.light();
	}

	private void clearTransmog()
	{
		restoreDrawCallbacks();
		cleanupTrackedObjects();
		resetState();
	}

	private void cleanupTrackedObjects()
	{
		assert client.isClientThread();
		List<RacecarObject> objects = new ArrayList<>(ACTIVE_OBJECTS);
		if (transmogObject != null && !objects.contains(transmogObject))
		{
			objects.add(transmogObject);
		}

		if (objects.isEmpty())
		{
			return;
		}

		for (RacecarObject object : objects)
		{
			if (object != null)
			{
				object.clear();
				disposeRacecarObject(object);
			}
		}
	}

	private void disposeRacecarObject(RacecarObject object)
	{
		if (object == null)
		{
			return;
		}

		try
		{
			if (client.isRuneLiteObjectRegistered(object))
			{
				client.removeRuneLiteObject(object);
			}

			if (!client.isRuneLiteObjectRegistered(object))
			{
				ACTIVE_OBJECTS.remove(object);
			}
			else
			{
				log.debug("Racecar controller remained registered after removal attempt");
			}
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to remove Racecar controller cleanly", ex);
		}
	}

	private void resetState()
	{
		transmogInitialized = false;
		sourceFollower = null;
		movementState = null;
		transmogObject = null;
	}

	private enum MovementState
	{
		STANDING,
		MOVING
	}
}
