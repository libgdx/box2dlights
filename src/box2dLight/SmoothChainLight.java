package box2dLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;

/**
 * This is a composite of multiple line and cone lights to
 * Created by PiotrJ on 16/11/2015.
 */
public class SmoothChainLight extends Light implements DebugLight {
	private int side = 1;
	private float endScale = 1;
	private Array<Light> lights = new Array<Light>();
	public SmoothChainLight (RayHandler rayHandler, int rays, Color color, float distance, float directionDegree, int side) {
		super(rayHandler, rays, color, distance, directionDegree);
		this.side = side;
		position.set(-15, 0);
	}

	private Vector2 position = new Vector2();
	private Vector2 lightDir = new Vector2();
	private Vector2 p1 = new Vector2();
	private Vector2 p2 = new Vector2();
	private Vector2 segmentCentre = new Vector2();
	private Vector2 segmentNormal = new Vector2();
	private Vector2 segmentDir = new Vector2();

//	private Vector2 p4 = new Vector2();
	private float[] chain;

	public void createChain (float[] chain) {
		if (chain.length < 4 && chain.length % 4 != 0) throw new AssertionError("Invalid chain length");
		this.chain = chain;

		lightDir.set(1, 0f).rotate(direction).nor();

		for (int i = 0; i < chain.length -3; i+=2) {
			p1.set(chain[i], chain[i + 1]);
			p2.set(chain[i + 2], chain[i + 3]);
			lights.add(new SmoothLineLight(rayHandler, rayNum, color, 1, 0, p1.dst(p2), rayNum * 2));
		}
	}

	@Override public void debugDraw (ShapeRenderer renderer) {
		drawRays(renderer);
		drawEdge(renderer);

		if (chain != null) {
			// draw centre
			renderer.setColor(Color.MAGENTA);
			renderer.circle(position.x, position.y, 0.16f, 16);

			for (int i = 0; i < chain.length -3; i+=2) {
				p1.set(chain[i], chain[i + 1]);
				p2.set(chain[i + 2], chain[i + 3]);

				p1.rotate(direction);
				p2.rotate(direction);

				// center of segment
				segmentCentre.set((p2.x + p1.x)/2, (p2.y + p1.y)/2);

				// direction of segment
				segmentDir.set(p2.x - p1.x, p2.y - p1.y).nor();

				p1.add(position);
				p2.add(position);
				segmentCentre.add(position);

				// draw segment
				renderer.setColor(Color.CYAN);
				renderer.line(p1, p2);

				// segment normal, perpendicular to direction
				segmentNormal.set(-segmentDir.y, segmentDir.x);

				// draw normal
				renderer.setColor(Color.RED);
				renderer.line(segmentCentre.x, segmentCentre.y, segmentCentre.x + segmentNormal.x, segmentCentre.y + segmentNormal.y);

				// draw light direction
				renderer.setColor(Color.MAGENTA);
				renderer.line(segmentCentre.x, segmentCentre.y, segmentCentre.x + lightDir.x, segmentCentre.y + lightDir.y);

				float dot = lightDir.dot(segmentNormal);
				float sign = Math.signum(dot);
//				float sin = MathUtils.sinDeg(normal.angle(dir));
				float sin = MathUtils.sinDeg(lightDir.angle(segmentNormal));
				float offset = sin * sign;
				// draw offset
				renderer.setColor(Color.GREEN);
				renderer.line(segmentCentre.x + lightDir.x, segmentCentre.y + lightDir.y, segmentCentre.x + lightDir.x + offset, segmentCentre.y + lightDir.y);

				// new height
				float height = lightDir.dot(segmentNormal);
				// draw new height
				renderer.setColor(Color.BLUE);
				renderer.line(
					segmentCentre.x + lightDir.x, segmentCentre.y + lightDir.y, segmentCentre.x + lightDir.x - segmentNormal.x * height, segmentCentre.y + lightDir.y - segmentNormal.y * height);
			}
		}
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

	}

	@Override void update () {
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
		// TODO current ray handler doesnt really allow easy for delegation
//		for (Light light : lights) {
//			light.render();
//		}
	}

	@Override public void setDistance (float dist) {
		distance = dist;
	}

	@Override public void setDirection (float directionDegree) {
		direction = directionDegree;
		// craps called from constructor in super :/
		if (lightDir != null)
			lightDir.set(1, 0f).rotate(direction).nor();
	}

	@Override public void attachToBody (Body body) {

	}

	@Override public Body getBody () {
		return null;
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
//			light.dispose();
		}
		lights.clear();
	}

	@Override public boolean contains (float x, float y) {
		// TODO line lights broken?
		for (Light light : lights) {
			if (light.contains(x, y)) return true;
		}
		return false;
	}

	public void setEndScale(float endScale) {
		endScale = MathUtils.clamp(endScale, 0, 10);
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
