package com.racecar;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.WorldView;
import net.runelite.api.events.ClientTick;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.callback.ClientThread;
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
	private static final int IDLE_ANIMATION_ID = AnimationID.DOM_BURROW_IDLE;
	private static final int MOVEMENT_ANIMATION_ID = AnimationID.DOM_BURROWED_MOVEMENT;
	private static final int PET_RENDER_RADIUS = 60;

	/*
	 * Controllers can outlive the currently referenced transmog object until the
	 * client thread removes them. A concurrent set lets startup/shutdown snapshot
	 * them safely even though those methods may run on the Swing event thread.
	 */
	private static final Set<RacecarObject> ACTIVE_OBJECTS = ConcurrentHashMap.newKeySet();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

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
		cleanupTrackedObjects();
		resetState();
		hooks.registerRenderableDrawListener(drawListener);
	}

	@Override
	protected void shutDown()
	{
		log.debug("Racecar stopped");
		try
		{
			clearTransmog();
		}
		finally
		{
			hooks.unregisterRenderableDrawListener(drawListener);
		}
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
			transmogObject.clear();
			transmogObject = null;
			movementState = null;
			return false;
		}

		client.registerRuneLiteObject(transmogObject);
		ACTIVE_OBJECTS.add(transmogObject);

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
		cleanupTrackedObjects();
		resetState();
	}

	private void cleanupTrackedObjects()
	{
		List<RacecarObject> objects = new ArrayList<>(ACTIVE_OBJECTS);
		if (transmogObject != null && !objects.contains(transmogObject))
		{
			objects.add(transmogObject);
		}

		if (objects.isEmpty())
		{
			return;
		}

		/*
		 * Blanking only changes Racecar-owned state, so it is safe to do
		 * immediately even when startup/shutdown is running on Swing's AWT thread.
		 * This makes the model disappear before the client-thread registry removal.
		 */
		for (RacecarObject object : objects)
		{
			if (object != null)
			{
				object.clear();
			}
		}

		/*
		 * The RuneLite object registry is client-thread-only. ClientThread.invoke()
		 * executes immediately when reached from ClientTick and queues otherwise.
		 */
		clientThread.invoke(() ->
		{
			for (RacecarObject object : objects)
			{
				disposeRacecarObject(object);
			}
		});
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

	private static final class RacecarObject extends RuneLiteObjectController
	{
		private final Client client;
		private final Model baseModel;
		private final int horizontalScale;
		private final int verticalScale;

		@Nullable
		private AnimationController animationController;
		private volatile boolean active = true;
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
			if (active)
			{
				animationController = new AnimationController(client, animation);
			}
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
			active = false;
		}

		@Override
		public void tick(int ticksSinceLastFrame)
		{
			if (active && animationController != null)
			{
				animationController.tick(ticksSinceLastFrame);
			}
		}

		@Override
		public Model getModel()
		{
			if (!active || animationController == null)
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
