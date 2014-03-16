package box2dLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * Light is data container for all the light parameters You can create instance
 * of Light also with help of rayHandler addLight method
 */
public class ConeLight extends PositionalLight {

	float coneDegree;

	/**
	 * @param rayHandler
	 * @param rays
	 * @param directionDegree
	 * @param distance
	 * @param color
	 * @param x
	 * @param y
	 * @param coneDegree
	 */
	public ConeLight(RayHandler rayHandler, int rays, Color color,
			float distance, float x, float y, float directionDegree,
			float coneDegree) {

		super(rayHandler, rays, color, distance, x, y, directionDegree);
		setConeDegree(coneDegree);
		setDirection(direction);
		update();
	}

	public void setDirection(float direction) {

		this.direction = direction;
		for (int i = 0; i < rayNum; i++) {
			float angle = direction + coneDegree - 2f * coneDegree * i
					/ (rayNum - 1f);
			final float s = sin[i] = MathUtils.sinDeg(angle);
			final float c = cos[i] = MathUtils.cosDeg(angle);
			endX[i] = distance * c;
			endY[i] = distance * s;
		}
		if (staticLight)
			staticUpdate();
	}

	/**
	 * @return the coneDegree
	 */
	public final float getConeDegree() {
		return coneDegree;

	}

	/**
	 * How big is the arc of cone. Arc angle = coneDegree * 2
	 * 
	 * @param coneDegree
	 *            the coneDegree to set
	 */
	public final void setConeDegree(float coneDegree) {
		if (coneDegree < 0)
			coneDegree = 0;
		if (coneDegree > 180)
			coneDegree = 180;
		this.coneDegree = coneDegree;
		setDirection(direction);
	}

	/**
	 * setDistance(float dist) MIN capped to 1cm
	 * 
	 * @param dist
	 */
	public void setDistance(float dist) {
		dist *= RayHandler.gammaCorrectionParameter;
		this.distance = dist < 0.01f ? 0.01f : dist;
		setDirection(direction);
	}

}
