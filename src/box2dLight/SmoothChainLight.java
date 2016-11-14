package box2dLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;

/**
 * Composite of multiple line lights forming a chain.
 * Created by PiotrJ on 16/11/2015.
 */
public class SmoothChainLight extends Light implements DebugLight {
	private int side = 1;
	private int extraRayNum = MIN_RAYS;
	private float endScale = 1;
	private Array<Light> lights = new Array<Light>();

	protected Body body;
	protected float bodyOffsetX;
	protected float bodyOffsetY;
	protected float bodyAngleOffset;

	/**
	 * Creates chain light without vertices, they can be added any time later
	 *
	 * @param rayHandler
	 *            not {@code null} instance of RayHandler
	 * @param baseRays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 * @param color
	 *            color, set to {@code null} to use the default color
	 * @param distance
	 *            distance of light, soft shadow length is set to distance * 0.1f
	 * @param rayDirection
	 *            direction of rays
	 *            <ul>
	 *            <li>1 = left</li>
	 *            <li>-1 = right</li>
	 *            </ul>
	 * @param chain
	 *            float array of (x, y) vertices from which rays will be
	 *            evenly distributed
	 */
	public SmoothChainLight (RayHandler rayHandler, int baseRays, Color color, float distance, float directionDegree, int rayDirection, float[] chain) {
		this(rayHandler, baseRays, color, distance, directionDegree, rayDirection, chain, baseRays * 2);
	}

	/**
	 * Creates chain light without vertices, they can be added any time later
	 *
	 * @param rayHandler
	 *            not {@code null} instance of RayHandler
	 * @param baseRays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 * @param color
	 *            color, set to {@code null} to use the default color
	 * @param distance
	 *            distance of light, soft shadow length is set to distance * 0.1f
	 * @param rayDirection
	 *            direction of rays
	 *            <ul>
	 *            <li>1 = left</li>
	 *            <li>-1 = right</li>
	 *            </ul>
	 * @param chain
	 *            float array of (x, y) vertices from which rays will be
	 *            evenly distributed
	 * @param extraRays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 */
	public SmoothChainLight (RayHandler rayHandler, int baseRays, Color color, float distance, float directionDegree, int rayDirection, float[] chain, int extraRays) {
		super(rayHandler, baseRays, color, distance, directionDegree);
		if (rayDirection == 1 || rayDirection == -1) {
			this.side = rayDirection;
		} else {
			throw new AssertionError("Invalid rayDirection, 1 or -1 expected, got " + rayDirection);
		}
		if (extraRays >= MIN_RAYS) this.extraRayNum = extraRays;
		createChain(chain);
	}

	private Vector2 position = new Vector2();
	private Vector2 lightDir = new Vector2();
	private Vector2 p1 = new Vector2();
	private Vector2 p2 = new Vector2();
	private Vector2 segmentCentre = new Vector2();
	private Vector2 segmentNormal = new Vector2();
	private Vector2 segmentDir = new Vector2();

	private float[] chain;

	/**
	 * @param chain
	 *            float array of (x, y) vertices from which rays will be
	 *            evenly distributed
	 */
	private void createChain (float[] chain) {
		if (chain.length < 4 && chain.length % 4 != 0) throw new AssertionError("Chain length has to be a multiple of 4, but is: " + chain.length);
		this.chain = chain;

		lightDir.set(1, 0f).rotate(direction).nor();
		float totalDst = 0;
		for (int i = 0; i < chain.length -3; i+=2) {
			p1.set(chain[i], chain[i + 1]);
			p2.set(chain[i + 2], chain[i + 3]);
			totalDst += p1.dst(p2);
		}

		float raysPerUnit = Math.max(rayNum/totalDst, MIN_RAYS);
		float extraRaysPerUnit = Math.max(extraRayNum/totalDst, MIN_RAYS);

		for (int i = 0; i < chain.length -3; i+=2) {
			p1.set(chain[i], chain[i + 1]);
			p2.set(chain[i + 2], chain[i + 3]);
			float dst = p1.dst(p2);
			int rn = Math.round(raysPerUnit * dst);
			int ern = Math.round(extraRaysPerUnit * dst);
			lights.add(new SmoothLineLight(rayHandler, rn, color, 1, 0, p1.dst(p2), ern));
		}
	}

	@Override public void debugDraw (ShapeRenderer renderer) {
		drawRays(renderer);
		drawEdge(renderer);
	}

	@Override public void drawRays (ShapeRenderer renderer) {
		for (Light light : lights) {
			if (light instanceof DebugLight) {
				DebugLight dl = (DebugLight)light;
				dl.drawRays(renderer);
			}
		}
	}

	@Override public void drawEdge (ShapeRenderer renderer) {
		for (Light light : lights) {
			if (light instanceof DebugLight) {
				DebugLight dl = (DebugLight)light;
				dl.drawEdge(renderer);
			}
		}
	}

	@Override public void setDebugColors (Color ray, Color hardEdge, Color softEdge) {
		for (Light light : lights) {
			if (light instanceof DebugLight) {
				DebugLight dl = (DebugLight)light;
				dl.setDebugColors(ray, hardEdge, softEdge);
			}
		}
	}

	@Override void update () {
		updateBody();

		for (int i = 0; i < lights.size; i++) {
			Light light = lights.get(i);
			int ci = i * 2;
			p1.set(chain[ci], chain[ci + 1]);
			p2.set(chain[ci + 2], chain[ci + 3]);
			// this could be optimized by moving the sin/cos calculation to outside of the loop
			p1.rotate(direction);
			p2.rotate(direction);

			segmentCentre.set((p2.x + p1.x)/2, (p2.y + p1.y)/2);
			segmentDir.set(p2.x - p1.x, p2.y - p1.y).nor();

			light.setPosition(position.x + segmentCentre.x, position.y + segmentCentre.y);
			light.setDirection(direction + lightDir.angle(segmentDir));

			if (light instanceof SmoothLineLight) {
				SmoothLineLight sll = (SmoothLineLight)light;

				segmentNormal.set(-segmentDir.y, segmentDir.x);

				if (endScale != 1 && i == 0) {
					sll.setCenterOffset(
						MathUtils.sinDeg(lightDir.angle(segmentNormal)) * distance - sll.halfWidth * (endScale - 1));
					sll.setDistance(lightDir.dot(segmentNormal) * distance);
				} else if (endScale != 1 && i == lights.size -1) {
					sll.setCenterOffset(
						MathUtils.sinDeg(lightDir.angle(segmentNormal)) * distance + sll.halfWidth * (endScale - 1));
					sll.setDistance(lightDir.dot(segmentNormal) * distance);
				} else {
					// calculate offset with sin(alpha) = opposite / hypotenuse
					sll.setCenterOffset(MathUtils.sinDeg(lightDir.angle(segmentNormal)) * distance);
					// we project the light dir on segment to set uniform distance on all parts
					sll.setDistance(lightDir.dot(segmentNormal) * distance);
				}
			}
		}
	}

	@Override void render () {
		// NOTE ray handler doesnt really allow for easy delegation, thus lights are rendered automatically by it
	}

	@Override public void setDistance (float dist) {
		distance = dist;
	}

	@Override public void setDirection (float directionDegree) {
		direction = directionDegree * side;
		// NOTE this will be null when super constructor calls setDirection and we are not fully initialized
		if (lightDir != null) {
			lightDir.set(1, 0f).rotate(direction).nor();
		}
	}

	@Override
	public void attachToBody(Body body) {
		attachToBody(body, 0f, 0f, 0f);
	}

	public void attachToBody(Body body, float offsetX, float offsetY) {
		attachToBody(body, offsetX, offsetY, 0f);
	}

	public void attachToBody(Body body, float offsetX, float offsetY, float degrees) {
		this.body = body;
		bodyOffsetX = offsetX;
		bodyOffsetY = offsetY;
		bodyAngleOffset = degrees;
		dirty = true;
	}

	@Override public Body getBody () {
		return body;
	}

	protected void updateBody() {
		if (body == null || staticLight) return;

		final Vector2 vec = body.getPosition();
		float angle = body.getAngle();
		final float cos = MathUtils.cos(angle);
		final float sin = MathUtils.sin(angle);
		final float dX = bodyOffsetX * cos - bodyOffsetY * sin;
		final float dY = bodyOffsetX * sin + bodyOffsetY * cos;
		position.x = vec.x + dX;
		position.y = vec.y + dY;
	}

	@Override public void setPosition (float x, float y) {
		position.set(x, y);
	}

	@Override public void setPosition (Vector2 position) {
		this.position.set(position);
	}

	@Override public float getX () {
		return position.x;
	}

	@Override public float getY () {
		return position.y;
	}

	@Override public boolean isSleeping () {
		return false;
	}

	public void add(RayHandler rayHandler) {
		this.rayHandler = rayHandler;
		if (active) {
			rayHandler.lightList.add(this);
		} else {
			rayHandler.disabledLights.add(this);
		}
	}

	@Override public void remove (boolean doDispose) {
		for (Light light : lights) {
			light.remove(doDispose);
		}
		rayHandler = null;
		if (doDispose) dispose();
	}

	@Override public void dispose () {
		for (Light light : lights) {
			light.dispose();
		}
		lights.clear();
	}

	@Override public boolean contains (float x, float y) {
		for (Light light : lights) {
			if (light.contains(x, y)) return true;
		}
		return false;
	}

	/**
	 * Sets end scale of first and last lights in the chain {@see SmoothLineLight#setEndScale}
	 * @param endScale end scale to set, negative values may produce strange results
	 */
	public void setEndScale(float endScale) {
		if (MathUtils.isEqual(this.endScale, endScale)) return;
		this.endScale = endScale;

		if (lights.size >= 2) {
			{
				Light light = lights.get(0);
				if (light instanceof SmoothLineLight) {
					SmoothLineLight sll = (SmoothLineLight)light;
					sll.setEndScale(endScale);
				}
			}
			{
				Light light = lights.get(lights.size - 1);
				if (light instanceof SmoothLineLight) {
					SmoothLineLight sll = (SmoothLineLight)light;
					sll.setEndScale(endScale);
				}
			}
		}
		dirty = true;
	}

	public float getEndScale () {
		return endScale;
	}

}
