package com.racecar;

import com.google.inject.Provides;
import java.awt.Polygon;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuEntryAdded;
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

	private static final int TARGET_NPC_ID = NpcID.DOM_BOSS_BURROWED;
	private static final int MENU_NPC_ID = NpcID.DOM_PET;
	private static final int IDLE_ANIMATION_ID = AnimationID.DOM_BURROW_IDLE;
	private static final int MOVEMENT_ANIMATION_ID = AnimationID.DOM_BURROWED_MOVEMENT;
	private static final int PET_RENDER_RADIUS = 60;
	private static final int SYNTHETIC_MENU_IDENTIFIER = 0x52414345; // "RACE"

	@Inject
	private Client client;

	@Inject
	private Hooks hooks;

	@Inject
	private RacecarConfig config;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	private boolean transmogInitialized;
	private NPC sourceFollower;
	private MovementState movementState;
	private RacecarObject transmogObject;

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
			if (transmogInitialized || sourceFollower != null || transmogObject != null)
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
			if (!initializeTransmogObject(follower))
			{
				return;
			}
			transmogInitialized = true;
		}

		updateTransmogObject(follower);
		updateFollowerMovement(follower);
	}

	/**
	 * RuneLiteObjectController has no native interaction/clickbox API. If hiding
	 * the real follower also prevents RuneLite from generating NPC menu entries,
	 * add an equivalent Dom menu while the mouse is over the hidden follower's
	 * model hull or tile. The menu callbacks dispatch the real NPC operation to
	 * the server-backed follower.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!transmogInitialized || sourceFollower == null
			|| event.getMenuEntry().getType() != MenuAction.WALK
			|| !isMouseOverFollowerInteractionArea()
			|| hasSyntheticDomMenuEntry())
		{
			return;
		}

		addSyntheticDomMenuEntries();
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
		boolean hasNativeFollowerEntries = menuEntries.stream()
			.anyMatch(menuEntry -> menuEntry.getNpc() == sourceFollower);

		/*
		 * Prefer native NPC entries when the hidden follower remains pickable.
		 * They already contain the exact server action parameters, so remove the
		 * synthetic fallback and only rewrite their visible Dom labels/actions.
		 */
		if (hasNativeFollowerEntries)
		{
			menuEntries.removeIf(this::isSyntheticDomMenuEntry);
		}

		boolean changed = hasNativeFollowerEntries;
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
				continue;
			}

			menuEntry.setOption(domAction);
			menuEntry.setTarget(domTarget);
		}

		if (changed)
		{
			client.getMenu().setMenuEntries(menuEntries.toArray(new MenuEntry[0]));
		}
	}

	private void addSyntheticDomMenuEntries()
	{
		NPCComposition domComposition = client.getNpcDefinition(MENU_NPC_ID);
		if (domComposition == null)
		{
			return;
		}

		String domName = domComposition.getName();
		String[] domActions = domComposition.getActions();

		/*
		 * Append Examine first, then NPC operations in reverse order so the first
		 * NPC operation (Talk-to for Dom) is the final/top entry, matching normal
		 * NPC left-click/right-click ordering.
		 */
		createSyntheticDomMenuEntry("Examine", domName, MenuAction.EXAMINE_NPC);

		if (domActions == null)
		{
			return;
		}

		for (int actionIndex = Math.min(4, domActions.length - 1); actionIndex >= 0; actionIndex--)
		{
			String option = domActions[actionIndex];
			if (option == null)
			{
				continue;
			}

			MenuAction action = getNpcMenuAction(actionIndex);
			if (action != null)
			{
				createSyntheticDomMenuEntry(option, domName, action);
			}
		}
	}

	private void createSyntheticDomMenuEntry(String option, String target, MenuAction followerAction)
	{
		client.getMenu().createMenuEntry(-1)
			.setOption(option)
			.setTarget(target)
			.setIdentifier(SYNTHETIC_MENU_IDENTIFIER)
			.setType(MenuAction.RUNELITE)
			.onClick(menuEntry -> invokeFollowerAction(followerAction, option, target));
	}

	private void invokeFollowerAction(MenuAction action, String option, String target)
	{
		NPC follower = sourceFollower;
		if (follower == null)
		{
			return;
		}

		LocalPoint location = follower.getLocalLocation();
		client.menuAction(
			location.getSceneX(),
			location.getSceneY(),
			action,
			follower.getIndex(),
			-1,
			option,
			target);
	}

	private boolean isMouseOverFollowerInteractionArea()
	{
		if (sourceFollower == null)
		{
			return false;
		}

		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return false;
		}

		Shape followerHull = sourceFollower.getConvexHull();
		if (followerHull != null && followerHull.contains(mouse.getX(), mouse.getY()))
		{
			return true;
		}

		Polygon tilePoly = sourceFollower.getCanvasTilePoly();
		return tilePoly != null && tilePoly.contains(mouse.getX(), mouse.getY());
	}

	private boolean hasSyntheticDomMenuEntry()
	{
		return Arrays.stream(client.getMenu().getMenuEntries()).anyMatch(this::isSyntheticDomMenuEntry);
	}

	private boolean isSyntheticDomMenuEntry(MenuEntry menuEntry)
	{
		return menuEntry.getType() == MenuAction.RUNELITE
			&& menuEntry.getIdentifier() == SYNTHETIC_MENU_IDENTIFIER;
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

	private boolean initializeTransmogObject(NPC follower)
	{
		NPCComposition composition = client.getNpcDefinition(TARGET_NPC_ID);
		Model model = createBurrowedDoomModel(composition);
		if (composition == null || model == null)
		{
			log.debug("Unable to create burrowed Doom model for follower {} ({})",
				follower.getName(), follower.getId());
			return false;
		}

		int footprintSize = Math.max(1, composition.getSize());
		int horizontalScale = Math.max(1, Math.round((float) composition.getWidthScale() / footprintSize));
		int verticalScale = Math.max(1, Math.round((float) composition.getHeightScale() / footprintSize));

		transmogObject = new RacecarObject(client, model, horizontalScale, verticalScale);
		transmogObject.setRadius(PET_RENDER_RADIUS);
		setTransmogLocation(follower);
		transmogObject.setVerticalOffset(config.burrowedVerticalOffset());
		transmogObject.setModelScalePercent(config.modelScalePercent());

		movementState = getMovementState(follower);
		if (!setTransmogAnimation(movementState))
		{
			transmogObject = null;
			movementState = null;
			return false;
		}

		client.registerRuneLiteObject(transmogObject);

		log.debug(
			"Racecar transmog initialized for follower {} ({}) using target NPC {}, base scale {}/{}, visual scale {}%",
			follower.getName(), follower.getId(), TARGET_NPC_ID, horizontalScale, verticalScale,
			config.modelScalePercent());
		return true;
	}

	private void updateTransmogObject(NPC follower)
	{
		if (transmogObject == null)
		{
			return;
		}

		setTransmogLocation(follower);
		transmogObject.setOrientation(follower.getCurrentOrientation());
		transmogObject.setRadius(PET_RENDER_RADIUS);
		transmogObject.setVerticalOffset(config.burrowedVerticalOffset());
		transmogObject.setModelScalePercent(config.modelScalePercent());
	}

	private void setTransmogLocation(NPC follower)
	{
		if (transmogObject == null)
		{
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
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

		if (setTransmogAnimation(newState))
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

	private boolean setTransmogAnimation(MovementState state)
	{
		if (transmogObject == null)
		{
			return false;
		}

		int animationId = state == MovementState.MOVING
			? MOVEMENT_ANIMATION_ID
			: IDLE_ANIMATION_ID;
		Animation animation = client.loadAnimation(animationId);
		if (animation == null)
		{
			log.debug("Unable to load Racecar animation {}", animationId);
			return false;
		}

		transmogObject.setAnimation(animation);
		return true;
	}

	private Model createBurrowedDoomModel(NPCComposition composition)
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

	@Nullable
	private static MenuAction getNpcMenuAction(int actionIndex)
	{
		switch (actionIndex)
		{
			case 0:
				return MenuAction.NPC_FIRST_OPTION;
			case 1:
				return MenuAction.NPC_SECOND_OPTION;
			case 2:
				return MenuAction.NPC_THIRD_OPTION;
			case 3:
				return MenuAction.NPC_FOURTH_OPTION;
			case 4:
				return MenuAction.NPC_FIFTH_OPTION;
			default:
				return null;
		}
	}

	private boolean shouldDraw(Renderable renderable, boolean drawingUi)
	{
		if (renderable instanceof NPC && transmogInitialized)
		{
			NPC npc = (NPC) renderable;
			if (npc == sourceFollower)
			{
				return false;
			}
		}

		return true;
	}

	private void clearTransmog()
	{
		if (transmogObject != null)
		{
			if (client.isRuneLiteObjectRegistered(transmogObject))
			{
				client.removeRuneLiteObject(transmogObject);
			}
			transmogObject.clear();
		}

		resetState();
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

	private static final class RacecarObject extends RuneLiteObjectController
	{
		private final Client client;
		private final Model baseModel;
		private final int horizontalScale;
		private final int verticalScale;

		@Nullable
		private AnimationController animationController;
		private int verticalOffset;
		private int modelScalePercent = 100;

		private RacecarObject(Client client, Model baseModel, int horizontalScale, int verticalScale)
		{
			this.client = client;
			this.baseModel = baseModel;
			this.horizontalScale = horizontalScale;
			this.verticalScale = verticalScale;
		}

		private void setAnimation(Animation animation)
		{
			animationController = new AnimationController(client, animation);
		}

		private void setVerticalOffset(int verticalOffset)
		{
			this.verticalOffset = verticalOffset;
		}

		private void setModelScalePercent(int modelScalePercent)
		{
			this.modelScalePercent = modelScalePercent;
		}

		private void clear()
		{
			animationController = null;
		}

		@Override
		public void tick(int ticksSinceLastFrame)
		{
			if (animationController != null)
			{
				animationController.tick(ticksSinceLastFrame);
			}
		}

		@Override
		public Model getModel()
		{
			if (animationController == null)
			{
				return null;
			}

			/*
			 * RuneLite documents applyTransformations() as returning a shared,
			 * temporary Model which becomes invalid after the next transformation
			 * call. AnimationController.animate() uses that API. Therefore the
			 * animated model must be consumed immediately and never retained.
			 *
			 * Apply the boss animation at native scale first so its large root/model
			 * translations are transformed along with the mesh. Then scale this one
			 * transient animated frame down to the 1x1 pet footprint immediately
			 * before RuneLite draws it. The user scale is applied as a multiplier on
			 * top of that calculated pet scale.
			 */
			Model renderedModel = animationController.animate(baseModel);
			int renderedHorizontalScale = Math.max(1,
				Math.round(horizontalScale * modelScalePercent / 100.0f));
			int renderedVerticalScale = Math.max(1,
				Math.round(verticalScale * modelScalePercent / 100.0f));
			renderedModel.scale(renderedHorizontalScale, renderedVerticalScale, renderedHorizontalScale);

			if (verticalOffset != 0)
			{
				renderedModel.translate(0, -verticalOffset, 0);
			}

			return renderedModel;
		}
	}
}
