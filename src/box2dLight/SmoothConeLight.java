package box2dLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Sort;

/**
 * Created by PiotrJ on 06/11/2015.
 */
@SuppressWarnings("Duplicates")
public class SmoothConeLight extends SmoothPositionalLight {
	protected float coneDegree;

	public SmoothConeLight (RayHandler rayHandler, int rayNum, Color color, float distance,
			float x, float y, float directionDegree, float coneDegree) {
		this(rayHandler, rayNum, color, distance, x, y, directionDegree, coneDegree, rayNum * 2);
	}

	public SmoothConeLight (RayHandler rayHandler, int rayNum, Color color, float distance,
			float x, float y, float directionDegree, float coneDegree, int extraRays) {
		super(rayHandler, rayNum, color, distance, x, y, directionDegree, extraRays);
		setConeDegree(coneDegree);
	}

	protected void updateMesh() {
		startRayId = 0;
		onePastEndRayId = currentRayNum;
		if (rayHandler.world != null && !xray) {
			// get first and last valid rays
			final Ray first = rays[0];
			final Ray last = rays[baseRayNum - 1];
			updateRays();
			// shoot check each ray
			for (int i = 0; i < currentRayNum; i++) {
				// rayCast is not async, so we can do that
				rayHandler.world.rayCast(perfectRay, start, current = rays[i]);
			}
			if (currentRayNum >= baseRayNum) {
				// rotate all the rays so we always can be sorter properly
				// perhaps we can bake this in in the comparator?
				// we sub half pi so 0deg is at the top, not right
				// not full .5 so we never move first one beyond 0deg
				float angle = first.angle - MathUtils.PI * 0.495f;
				sorterSin = MathUtils.sin(-angle);
				sorterCos = MathUtils.cos(-angle);
				// sort so mesh vertices will be in correct order
				Sort.instance().sort(rays, sorter, 0, currentRayNum);
				// rotated back so the endpoints are in correct place
				for (int i = 0; i < currentRayNum; i++) {
					Ray ray = rays[i];
					// we mark start and end, sometimes a ray at the edge sneaks past simple bounds rejection
					if (ray == first) {
						startRayId = i;
					}
					if (ray == last) {
						onePastEndRayId = i + 1;
						break;
					}
				}
				currentRayNum = onePastEndRayId - startRayId;
			}
		}
		if (coneDegree > 179.5f) {
			currentRayNum++;
			// copy first one to last, so we finish the circle
			rays[onePastEndRayId++].set(rays[startRayId]);
		}
	}

	protected float rayAngleStep;
	protected void setEndPoints() {
		rayAngleStep = actualConeDeg / (baseRayNum - 1);
		for (int i = 0; i < baseRayNum; i++) {
			float angle = direction + actualConeDeg - 2 * actualConeDeg * i / (baseRayNum - 1);
			final float s = sin[i] = MathUtils.sinDeg(angle);
			final float c = cos[i] = MathUtils.cosDeg(angle);
			endX[i] = distance * c;
			endY[i] = distance * s;
		}
	}

	protected boolean accept(Ray candidate) {
		// if it is outside of light bounds, we dont care
		if (!aabb.contains(candidate.x, candidate.y)) {
			return false;
		}
		return super.accept(candidate);
	}

	@Override
	public void setDirection(float newDir) {
		newDir = newDir < 0 ? 360 - (-newDir % 360) : newDir % 360;
		if (newDir > 180) newDir -= 360;
		if (MathUtils.isEqual(direction, newDir)) return;
		direction = newDir;
		dirty = true;
	}

	private float actualConeDeg;
	public void setConeDegree(float coneDegree) {
		this.coneDegree = actualConeDeg = MathUtils.clamp(coneDegree, 0f, 180f);
		// sorting breaks above this value, so we cheat a bit
		if (actualConeDeg > 179.5f) {
			actualConeDeg = 179.5f;
		}
		dirty = true;
	}

	public float getConeDegree() {
		return coneDegree;
	}
}
