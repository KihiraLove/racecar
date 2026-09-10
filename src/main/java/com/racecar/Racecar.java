package com.racecar;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Racecar",
	description = "Transmogrifies Dom into the burrowed form of the Doom of Mokhaiotl",
	tags = {"doom", "mokhaiotl", "dom", "pet", "transmog", "racecar"}
)
public class Racecar extends Plugin
{
	/*
	 * Test mode uses Yami as the stand-in follower. Set to false once the plugin
	 * is ready to target the real Dom pet variants only.
	 */
	private static final boolean TEST_MODE = true;
	private static final String TEST_FOLLOWER_NAME = "Yami";

	private static final int BOSS_MODEL_NPC_ID = NpcID.DOM_BOSS_BURROWED;
	private static final int PET_MODEL_NPC_ID = NpcID.DOM_PET;
	private static final int MENU_NPC_ID = NpcID.DOM_PET;
	private static final int IDLE_ANIMATION_ID = AnimationID.DOM_BURROW_IDLE;
	private static final int MOVEMENT_ANIMATION_ID = AnimationID.DOM_BURROWED_MOVEMENT;
	private static final int PET_RENDER_RADIUS = 60;
	private static final int MODEL_SCALE_BASE = 128;

	@Inject
	private Client client;

	@Inject
	private Hooks hooks;

	@Inject
	private RacecarConfig config;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;
	private final List<RuneLiteObject> transmogObjects = new ArrayList<>();

	private boolean transmogInitialized;
	private boolean animationsApplied;
	private NPC sourceFollower;
	private MovementState movementState;

	@Provides
	RacecarConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RacecarConfig.class);
	}

	@Override
	protected void startUp()
	{
		log.debug("Racecar started");
		resetState();
		hooks.registerRenderableDrawListener(drawListener);
	}

	@Override
	protected void shutDown()
	{
		log.debug("Racecar stopped");
		hooks.unregisterRenderableDrawListener(drawListener);
		clearTransmog();
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		NPC follower = client.getFollower();

		if (!isSourceFollower(follower))
		{
			if (transmogInitialized || sourceFollower != null)
			{
				clearTransmog();
			}
			return;
		}

		if (sourceFollower != follower)
		{
			clearTransmog();
			sourceFollower = follower;
		}

		if (!transmogInitialized)
		{
			RuneLiteObject transmogObject = initializeTransmogObject(follower);
			if (transmogObject == null)
			{
				return;
			}

			transmogInitialized = true;
		}

		updateTransmogObject(follower);
		updateAnimationState(follower);
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!transmogInitialized || sourceFollower == null)
		{
			return;
		}

		NPCComposition domComposition = client.getNpcDefinition(MENU_NPC_ID);
		if (domComposition == null)
		{
			return;
		}

		String[] domActions = domComposition.getActions();
		List<MenuEntry> menuEntries = new ArrayList<>(Arrays.asList(event.getMenuEntries()));
		boolean changed = false;

		for (int i = menuEntries.size() - 1; i >= 0; i--)
		{
			MenuEntry menuEntry = menuEntries.get(i);
			if (menuEntry.getNpc() != sourceFollower)
			{
				continue;
			}

			String domTarget = replaceFollowerName(menuEntry.getTarget(), domComposition.getName());
			MenuAction menuAction = menuEntry.getType();

			if (menuAction == MenuAction.EXAMINE_NPC)
			{
				menuEntry.setOption("Examine");
				menuEntry.setTarget(domTarget);
				changed = true;
				continue;
			}

			int actionIndex = getNpcActionIndex(menuAction);
			if (actionIndex < 0)
			{
				continue;
			}

			String domAction = domActions != null && actionIndex < domActions.length
				? domActions[actionIndex]
				: null;

			if (domAction == null)
			{
				menuEntries.remove(i);
				changed = true;
				continue;
			}

			menuEntry.setOption(domAction);
			menuEntry.setTarget(domTarget);
			changed = true;
		}

		if (changed)
		{
			client.getMenu().setMenuEntries(menuEntries.toArray(new MenuEntry[0]));
		}
	}

	private boolean isSourceFollower(NPC follower)
	{
		if (follower == null)
		{
			return false;
		}

		if (TEST_MODE)
		{
			return TEST_FOLLOWER_NAME.equalsIgnoreCase(follower.getName());
		}

		return follower.getId() == NpcID.DOM_PET || follower.getId() == NpcID.POH_DOM_PET;
	}

	private int getModelNpcId()
	{
		return config.useDomPetModel() ? PET_MODEL_NPC_ID : BOSS_MODEL_NPC_ID;
	}

	private RuneLiteObject initializeTransmogObject(NPC follower)
	{
		Model model = createRacecarModel();
		if (model == null)
		{
			log.debug("Unable to create Racecar model for follower {} ({})",
				follower.getName(), follower.getId());
			return null;
		}

		RuneLiteObject transmogObject = client.createRuneLiteObject();
		if (transmogObject == null)
		{
			log.debug("Client returned null while creating Racecar RuneLiteObject");
			return null;
		}

		WorldView worldView = client.getTopLevelWorldView();
		transmogObject.setModel(model);
		transmogObject.setRadius(PET_RENDER_RADIUS);
		transmogObject.setLocation(follower.getLocalLocation(), worldView.getPlane());
		transmogObject.setOrientation(follower.getCurrentOrientation());
		transmogObject.setActive(true);

		transmogObjects.add(transmogObject);
		movementState = null;
		animationsApplied = false;

		log.debug("Racecar transmog initialized for follower {} ({}) using model NPC {}",
			follower.getName(), follower.getId(), getModelNpcId());
		return transmogObject;
	}

	private void updateTransmogObject(NPC follower)
	{
		WorldView worldView = client.getTopLevelWorldView();
		Model model = createRacecarModel();

		for (RuneLiteObject transmogObject : transmogObjects)
		{
			if (transmogObject == null)
			{
				continue;
			}

			transmogObject.setLocation(follower.getLocalLocation(), worldView.getPlane());
			transmogObject.setOrientation(follower.getCurrentOrientation());
			transmogObject.setRadius(PET_RENDER_RADIUS);

			if (model != null)
			{
				transmogObject.setModel(model);
			}
		}
	}

	private void updateAnimationState(NPC follower)
	{
		if (!config.enableBurrowAnimations())
		{
			if (animationsApplied)
			{
				for (RuneLiteObject transmogObject : transmogObjects)
				{
					if (transmogObject != null)
					{
						transmogObject.setAnimation(null);
					}
				}
			}

			animationsApplied = false;
			movementState = null;
			return;
		}

		animationsApplied = true;
		updateFollowerMovement(follower);
	}

	private void updateFollowerMovement(NPC follower)
	{
		MovementState newState = follower.getPoseAnimation() == follower.getWalkAnimation()
			? MovementState.MOVING
			: MovementState.STANDING;

		if (newState == movementState)
		{
			return;
		}

		movementState = newState;
		int animationId = newState == MovementState.MOVING
			? MOVEMENT_ANIMATION_ID
			: IDLE_ANIMATION_ID;

		applyAnimation(animationId);
	}

	@SuppressWarnings("deprecation")
	private void applyAnimation(int animationId)
	{
		Animation animation = client.loadAnimation(animationId);
		if (animation == null)
		{
			log.debug("Unable to load Racecar animation {}", animationId);
			return;
		}

		for (RuneLiteObject transmogObject : transmogObjects)
		{
			if (transmogObject == null)
			{
				continue;
			}

			transmogObject.setActive(true);
			transmogObject.setAnimation(animation);
			transmogObject.setShouldLoop(true);
		}
	}

	private Model createRacecarModel()
	{
		NPCComposition composition = client.getNpcDefinition(getModelNpcId());
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
		if (mergedModelData == null)
		{
			return null;
		}

		int footprintSize = Math.max(1, composition.getSize());
		int verticalOffset = config.burrowedVerticalOffset();

		if (footprintSize > 1 || verticalOffset != 0)
		{
			mergedModelData = mergedModelData.cloneVertices();

			/*
			 * The full boss model is 5x5 and needs reducing to pet scale. Dom's
			 * own model is already 1x1, so when that test path is selected no
			 * horizontal/model scaling is applied here.
			 */
			if (footprintSize > 1)
			{
				int petScale = Math.max(1, Math.round((float) MODEL_SCALE_BASE / footprintSize));
				mergedModelData.scale(petScale, petScale, petScale);
			}

			if (verticalOffset != 0)
			{
				mergedModelData.translate(0, -verticalOffset, 0);
			}
		}

		return mergedModelData.light();
	}

	private String replaceFollowerName(String target, String domName)
	{
		if (target == null || domName == null)
		{
			return target;
		}

		String followerName = sourceFollower != null ? sourceFollower.getName() : null;
		if (followerName != null && target.contains(followerName))
		{
			return target.replace(followerName, domName);
		}

		return target;
	}

	private static int getNpcActionIndex(MenuAction menuAction)
	{
		switch (menuAction)
		{
			case NPC_FIRST_OPTION:
				return 0;
			case NPC_SECOND_OPTION:
				return 1;
			case NPC_THIRD_OPTION:
				return 2;
			case NPC_FOURTH_OPTION:
				return 3;
			case NPC_FIFTH_OPTION:
				return 4;
			default:
				return -1;
		}
	}

	private boolean shouldDraw(Renderable renderable, boolean drawingUi)
	{
		if (renderable instanceof NPC && transmogInitialized)
		{
			NPC npc = (NPC) renderable;
			if (npc == client.getFollower())
			{
				return false;
			}
		}

		return true;
	}

	@SuppressWarnings("deprecation")
	private void clearTransmog()
	{
		for (RuneLiteObject transmogObject : transmogObjects)
		{
			if (transmogObject != null)
			{
				transmogObject.setActive(false);
				transmogObject.setAnimation(null);
				transmogObject.setModel(null);
				transmogObject.setFinished(true);
			}
		}

		transmogObjects.clear();
		resetState();
	}

	private void resetState()
	{
		transmogInitialized = false;
		animationsApplied = false;
		sourceFollower = null;
		movementState = null;
	}

	private enum MovementState
	{
		STANDING,
		MOVING
	}
}
