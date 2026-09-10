package com.racecar;

import javax.annotation.Nullable;
import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.RuneLiteObjectController;

final class RacecarObject extends RuneLiteObjectController
{
	private static final int MODEL_SCALE_PERCENT = 200;
	private static final int VERTICAL_OFFSET = 20;

	private final Client client;
	private final Model burrowedModel;
	private final Model transitionModel;
	private final int horizontalScale;
	private final int verticalScale;

	@Nullable
	private AnimationController animationController;
	private volatile boolean active = true;
	private Form form;
	private boolean animationFinished;

	RacecarObject(Client client, Model burrowedModel, Model transitionModel, int horizontalScale, int verticalScale)
	{
		this.client = client;
		this.burrowedModel = burrowedModel;
		this.transitionModel = transitionModel;
		this.horizontalScale = Math.max(1, Math.round(horizontalScale * MODEL_SCALE_PERCENT / 100.0f));
		this.verticalScale = Math.max(1, Math.round(verticalScale * MODEL_SCALE_PERCENT / 100.0f));
	}

	void setAnimation(Animation animation, Form form)
	{
		if (!active)
		{
			return;
		}

		this.form = form;
		animationFinished = false;
		animationController = new AnimationController(client, animation);
		if (isPlayingAction())
		{
			animationController.setOnFinished(controller ->
			{
				animationFinished = true;
				controller.setFrame(animation.getDuration() - 1);
			});
		}
	}

	boolean isTransitioning()
	{
		return form == Form.TRANSITION;
	}

	boolean isPlayingAction()
	{
		return form == Form.TRANSITION || form == Form.EMOTE;
	}

	boolean isAnimationFinished()
	{
		return animationFinished;
	}

	void clear()
	{
		active = false;
		animationController = null;
	}

	@Override
	public void tick(int ticksSinceLastFrame)
	{
		AnimationController controller = animationController;
		if (active && controller != null && !animationFinished)
		{
			controller.tick(ticksSinceLastFrame);
		}
	}

	@Override
	public Model getModel()
	{
		AnimationController controller = animationController;
		if (!active || controller == null)
		{
			return null;
		}

		Model renderedModel = controller.animate(form == Form.TRANSITION ? transitionModel : burrowedModel);
		renderedModel.scale(horizontalScale, verticalScale, horizontalScale);
		renderedModel.translate(0, -VERTICAL_OFFSET, 0);
		return renderedModel;
	}

	enum Form
	{
		BURROWED,
		TRANSITION,
		EMOTE
	}
}
