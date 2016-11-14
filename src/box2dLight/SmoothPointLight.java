package box2dLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Sort;

/**
 * Created by PiotrJ on 06/11/2015.
 */
@SuppressWarnings("Duplicates")
public class SmoothPointLight extends SmoothPositionalLight {
	public SmoothPointLight (RayHandler rayHandler, int rayNum, Color color,
			float distance, float x, float y) {
		this(rayHandler, rayNum, color, distance, x, y, rayNum * 2);
	}

	public SmoothPointLight (RayHandler rayHandler, int rayNum, Color color,
			float distance, float x, float y, int extraRays) {
		super(rayHandler, rayNum, color, distance, x, y, 0.0f, extraRays);
	}

	protected void updateMesh() {
		if (rayHandler.world != null && !xray) {
			updateRays();
			// shoot check each ray
			for (int i = 0; i < currentRayNum; i++) {
				// rayCast is not async, so we can do that
				rayHandler.world.rayCast(perfectRay, start, current = rays[i]);
			}

			// we need to sort if stuff was added to set the mesh properly
			if (currentRayNum >= baseRayNum) {
				// sort only added rays
				Sort.instance().sort(rays, sorter, 0, currentRayNum);
			}
		}
		// add missing last point to finish the loop
		rays[currentRayNum++].set(rays[0]);
		onePastEndRayId = currentRayNum;
	}

	protected void setEndPoints() {
		float angleNum = 360f / (baseRayNum - 1);
		for (int i = 0; i < baseRayNum; i++) {
			final float angle = angleNum * i;
			sin[i] = MathUtils.sinDeg(angle);
			cos[i] = MathUtils.cosDeg(angle);
			endX[i] = distance * cos[i];
			endY[i] = distance * sin[i];
		}
	}

	@Deprecated
	@Override
	public void setDirection(float directionDegree) {
	}
}
