package box2dLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * A light designed to cover entire screen
 *
 * Created by PiotrJ on 18/08/2016.
 */
public class SmoothDirectionalLight extends SmoothLineLight implements DebugLight {
	private static final String TAG = SmoothDirectionalLight.class.getSimpleName();
	protected float sin;
	protected float cos;

	public SmoothDirectionalLight (RayHandler rayHandler, int rays, Color color, float directionDegree, int extraRays) {
		super(rayHandler, rays, color, 10, directionDegree, 10, extraRays);
		endColorScale = 0;
	}

	protected float lastSizeOfScreen;
	protected float lastRayHandlerX;
	protected float lastRayHandlerY;
	// large enough to never show edges of the light
	protected float widthScale = (float)Math.sqrt(2);
	// double the screen size seems enough to never allow the end of the light to be visible
	protected float distanceScale = 2;
	@Override void update () {
		final float width = (rayHandler.x2 - rayHandler.x1);
		final float height = (rayHandler.y2 - rayHandler.y1);
		final float sizeOfScreen = width > height ? width : height;
		if (dirty || !MathUtils.isEqual(sizeOfScreen, lastSizeOfScreen)
			|| !MathUtils.isEqual(rayHandler.x1, lastRayHandlerX)
			|| !MathUtils.isEqual(rayHandler.y1, lastRayHandlerY)) {
			lastSizeOfScreen = sizeOfScreen;
			lastRayHandlerX = rayHandler.x1;
			lastRayHandlerY = rayHandler.y1;

			final float widthOffSet = sizeOfScreen * -sin;
			final float heightOffSet = sizeOfScreen * cos;

			float x = (rayHandler.x1 + rayHandler.x2) * 0.5f - widthOffSet;
			float y = (rayHandler.y1 + rayHandler.y2) * 0.5f - heightOffSet;

			super.setPosition(x, y);
			super.setWidth(sizeOfScreen * widthScale);
			super.setDistance(sizeOfScreen * distanceScale);
		}
		super.update();
	}

	protected boolean inBounds(float x, float y) {
		// we don't need accurate test for this type
		return aabb.contains(x, y);
	}

	@Override public void setDirection (float directionDegree) {
		super.setDirection(directionDegree - 90);
		sin = MathUtils.sinDeg(direction);
		cos = MathUtils.cosDeg(direction);
	}

	@Override public void setPosition (float x, float y) {
		// this light type has no position
	}

	@Override public void setPosition (Vector2 position) {
		// this light type has no position
	}

	@Override public void setWidth (float width) {
		// this light doesnt not allow for manual width setting
	}

	@Override public void setDistance (float dist) {
		// this light doesnt not allow for manual distance setting
	}

	@Override public float getX () {
		return 0;
	}

	@Override public float getY () {
		return 0;
	}
}
