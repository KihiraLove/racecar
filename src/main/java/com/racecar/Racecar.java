package com.racecar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.ClientTick;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.callback.Hooks;
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
	 * Test mode intentionally transmogs whichever follower the local player has
	 * out. This avoids depending on the newly-added puppy NPC IDs/names while
	 * testing with the Pug. Set to false for the final Dom-only version.
	 */
	private static final boolean TEST_MODE = true;

	private static final int TARGET_NPC_ID = NpcID.DOM_BOSS_BURROWED;
	private static final int IDLE_ANIMATION_ID = AnimationID.DOM_BURROW_IDLE;
	private static final int MOVEMENT_ANIMATION_ID = AnimationID.DOM_BURROWED_MOVEMENT;

	@Inject
	private Client client;

	@Inject
	private Hooks hooks;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;
	private final List<RuneLiteObject> transmogObjects = new ArrayList<>();

	private boolean transmogInitialized;
	private NPC sourceFollower;
	private MovementState movementState;

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
			return true;
		}

		return follower.getId() == NpcID.DOM_PET || follower.getId() == NpcID.POH_DOM_PET;
	}

	private RuneLiteObject initializeTransmogObject(NPC follower)
	{
		Model model = createBurrowedDoomModel();
		if (model == null)
		{
			log.debug("Unable to create burrowed Doom model for follower {} ({})",
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
		transmogObject.setRadius(getTargetRadius());
		transmogObject.setLocation(follower.getLocalLocation(), worldView.getPlane());
		transmogObject.setOrientation(follower.getCurrentOrientation());
		transmogObject.setActive(true);

		transmogObjects.add(transmogObject);
		movementState = null;

		log.debug("Racecar transmog initialized for follower {} ({}) using target NPC {}",
			follower.getName(), follower.getId(), TARGET_NPC_ID);
		return transmogObject;
	}

	private void updateTransmogObject(NPC follower)
	{
		WorldView worldView = client.getTopLevelWorldView();
		Model model = createBurrowedDoomModel();

		for (RuneLiteObject transmogObject : transmogObjects)
		{
			if (transmogObject == null)
			{
				continue;
			}

			transmogObject.setLocation(follower.getLocalLocation(), worldView.getPlane());
			transmogObject.setOrientation(follower.getCurrentOrientation());
			transmogObject.setRadius(getTargetRadius());

			/*
			 * The established pet-to-npc-transmog plugin reapplies the model while
			 * updating the follower. Keep the same behaviour here for the test
			 * implementation rather than relying on a one-time model assignment.
			 */
			if (model != null)
			{
				transmogObject.setModel(model);
			}
		}
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

			/*
			 * Match pet-to-npc-transmog's working animation path: activate the
			 * RuneLiteObject, assign the loaded animation, and explicitly loop it.
			 */
			transmogObject.setActive(true);
			transmogObject.setAnimation(animation);
			transmogObject.setShouldLoop(true);
		}
	}

	private Model createBurrowedDoomModel()
	{
		NPCComposition composition = client.getNpcDefinition(TARGET_NPC_ID);
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

		/*
		 * Deliberately mirror pet-to-npc-transmog here: use the NPC's raw model
		 * parts, merge them, then light the result. Do not add a second custom
		 * model/transformation pipeline until the baseline transmog is proven.
		 */
		return mergedModelData.light();
	}

	private int getTargetRadius()
	{
		NPCComposition composition = client.getNpcDefinition(TARGET_NPC_ID);
		if (composition == null)
		{
			return 300;
		}

		return Math.max(60, composition.getSize() * 60);
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

	private void clearTransmog()
	{
		for (RuneLiteObject transmogObject : transmogObjects)
		{
			if (transmogObject != null)
			{
				transmogObject.setActive(false);
			}
		}

		transmogObjects.clear();
		resetState();
	}

	private void resetState()
	{
		transmogInitialized = false;
		sourceFollower = null;
		movementState = null;
	}

	private enum MovementState
	{
		STANDING,
		MOVING
	}
}
