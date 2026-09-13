package com.racecar;

import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Model;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RacecarObjectTest
{
	private final Client client = mock(Client.class);
	private final Model burrowed = mock(Model.class);
	private final Model transition = mock(Model.class);
	private final Model animated = mock(Model.class);
	private final Animation animation = mock(Animation.class);
	private final RacecarObject object = new RacecarObject(client, burrowed, transition, 26, 26);

	@Test
	public void scalesEveryFormRelativeToItsNormalRacecarSize()
	{
		when(client.applyTransformations(any(), any(), anyInt(), any(), anyInt())).thenReturn(animated);
		object.setSizePercentage(200);
		for (RacecarObject.Form form : RacecarObject.Form.values())
		{
			object.setAnimation(animation, form);
			assertNotNull(object.getModel());
		}
		verify(animated, times(3)).scale(104, 104, 104);
		verify(animated, times(3)).translate(0, -40, 0);
		assertEquals(120, object.getRadius());
		object.setSizePercentage(100);
		object.getModel();
		verify(animated).scale(52, 52, 52);
	}

	@Test
	public void zeroHidesVisualWhileTheTransitionStillFinishes()
	{
		when(animation.isMayaAnim()).thenReturn(true);
		when(animation.getDuration()).thenReturn(2);
		object.setAnimation(animation, RacecarObject.Form.TRANSITION);
		object.setSizePercentage(0);
		assertNull(object.getModel());
		object.tick(2);
		assertEquals(true, object.isAnimationFinished());
		verify(client, never()).applyTransformations(any(), any(), anyInt(), any(), anyInt());
	}

	@Test
	public void clampsScaleAndDoesNotCompoundItBetweenFrames()
	{
		when(client.applyTransformations(any(), any(), anyInt(), any(), anyInt())).thenReturn(animated);
		object.setAnimation(animation, RacecarObject.Form.BURROWED);
		object.setSizePercentage(900);
		object.getModel();
		object.getModel();
		verify(animated, times(2)).scale(260, 260, 260);
		object.setSizePercentage(-1);
		assertNull(object.getModel());
	}
}
