package com.racecar;

import java.util.Arrays;
import java.util.Objects;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObject;
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
	 * Temporary test source. Change this to false once the plugin is ready to
	 * target the real Dom pet.
	 */
	private static final boolean TEST_WITH_PUG = true;
	private static final String TEST_FOLLOWER_NAME = "Pug";

	private static final int TARGET_NPC_ID = NpcID.DOM_BOSS_BURROWED;
	private static final int IDLE_ANIMATION_ID = AnimationID.DOM_BURROW_IDLE;
	private static final int MOVEMENT_ANIMATION_ID = AnimationID.DOM_BURROWED_MOVEMENT;

	@Inject
	private Client client;

	@Inject
	private Hooks hooks;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	private NPC sourceFollower;
	private RuneLiteObject transmogObject;
	private int currentAnimationId = -1;
	private boolean modelCreationFailed;

	@Override
	protected void startUp()
	{
		log.debug("Racecar started");
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
			if (sourceFollower != null || transmogObject != null)
			{
				clearTransmog();
			}
			return;
		}

		if (follower != sourceFollower)
		{
			clearTransmog();
			sourceFollower = follower;
		}

		if (transmogObject == null && !modelCreationFailed)
		{
			initializeTransmog(follower);
		}

		if (transmogObject != null)
		{
			updateTransmog(follower);
		}
	}

	private boolean isSourceFollower(NPC follower)
	{
		if (follower == null)
		{
			return false;
		}

		if (TEST_WITH_PUG)
		{
			return TEST_FOLLOWER_NAME.equalsIgnoreCase(follower.getName());
		}

		return follower.getId() == NpcID.DOM_PET || follower.getId() == NpcID.POH_DOM_PET;
	}

	private void initializeTransmog(NPC follower)
	{
		Model model = buildBurrowedModel();
		if (model == null)
		{
			modelCreationFailed = true;
			log.debug("Unable to build Doom of Mokhaiotl burrowed model");
			return;
		}

		RuneLiteObject object = client.createRuneLiteObject();
		object.setModel(model);
		object.setRadius(getTargetRadius());
		object.setLocation(follower.getLocalLocation(), follower.getWorldLocation().getPlane());
		object.setOrientation(follower.getCurrentOrientation());
		object.setAnimationController(new AnimationController(client, IDLE_ANIMATION_ID));
		object.setActive(true);

		transmogObject = object;
		currentAnimationId = IDLE_ANIMATION_ID;
		log.debug("Racecar transmog initialized for follower {}", follower.getName());
	}

	private Model buildBurrowedModel()
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

		ModelData[] parts = Arrays.stream(modelIds)
			.mapToObj(client::loadModelData)
			.toArray(ModelData[]::new);

		if (Arrays.stream(parts).anyMatch(Objects::isNull))
		{
			return null;
		}

		ModelData modelData = client.mergeModels(parts);
		if (modelData == null)
		{
			return null;
		}

		short[] colorsToReplace = composition.getColorToReplace();
		short[] replacementColors = composition.getColorToReplaceWith();
		if (colorsToReplace != null && replacementColors != null)
		{
			modelData = modelData.cloneColors();
			for (int i = 0; i < Math.min(colorsToReplace.length, replacementColors.length); i++)
			{
				modelData.recolor(colorsToReplace[i], replacementColors[i]);
			}
		}

		int widthScale = composition.getWidthScale();
		int heightScale = composition.getHeightScale();
		if (widthScale != 128 || heightScale != 128)
		{
			modelData = modelData.cloneVertices();
			modelData.scale(widthScale, heightScale, widthScale);
		}

		return modelData.light();
	}

	private int getTargetRadius()
	{
		NPCComposition composition = client.getNpcDefinition(TARGET_NPC_ID);
		if (composition == null)
		{
			return 60;
		}

		return Math.max(60, composition.getSize() * 60);
	}

	private void updateTransmog(NPC follower)
	{
		transmogObject.setLocation(follower.getLocalLocation(), follower.getWorldLocation().getPlane());
		transmogObject.setOrientation(follower.getCurrentOrientation());

		int animationId = isMoving(follower) ? MOVEMENT_ANIMATION_ID : IDLE_ANIMATION_ID;
		if (animationId != currentAnimationId)
		{
			transmogObject.setAnimationController(new AnimationController(client, animationId));
			currentAnimationId = animationId;
		}
	}

	private boolean isMoving(NPC follower)
	{
		int poseAnimation = follower.getPoseAnimation();
		return poseAnimation == follower.getWalkAnimation()
			|| poseAnimation == follower.getWalkRotateLeft()
			|| poseAnimation == follower.getWalkRotateRight()
			|| poseAnimation == follower.getWalkRotate180()
			|| poseAnimation == follower.getRunAnimation();
	}

	private boolean shouldDraw(Renderable renderable, boolean drawingUi)
	{
		if (!(renderable instanceof NPC) || transmogObject == null || !transmogObject.isActive())
		{
			return true;
		}

		return renderable != sourceFollower;
	}

	private void clearTransmog()
	{
		if (transmogObject != null)
		{
			transmogObject.setActive(false);
			transmogObject = null;
		}

		sourceFollower = null;
		currentAnimationId = -1;
		modelCreationFailed = false;
	}
}
