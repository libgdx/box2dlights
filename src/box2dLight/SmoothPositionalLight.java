package box2dLight;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;

import java.util.Comparator;

/**
 * Created by PiotrJ on 06/11/2015.
 */
@SuppressWarnings("Duplicates")
public abstract class SmoothPositionalLight extends Light implements DebugLight {
	protected final Vector2 tmpEnd = new Vector2();
	protected final Vector2 start = new Vector2();
	protected final Rectangle aabb = new Rectangle();

	protected Body body;
	protected float bodyOffsetX;
	protected float bodyOffsetY;
	protected float bodyAngleOffset;

	protected float sin[];
	protected float cos[];

	protected float endX[];
	protected float endY[];

	protected Ray[] rays;
	protected int baseRayNum;
	protected int currentRayNum;
	public int peakRayNum;
	protected boolean isSleeping;
	protected boolean allowSleeping = true;
	protected boolean ignoreStaticBodies = false;

	public SmoothPositionalLight (RayHandler rayHandler, int rayNum, Color color,
			float distance, float x, float y, float directionDegree) {
		this(rayHandler, rayNum, color, distance, x, y, directionDegree, rayNum * 2);
	}

	public SmoothPositionalLight (RayHandler rayHandler, int rayNum, Color color,
			float distance, float x, float y, float directionDegree, int extraRays) {
		super(rayHandler, rayNum + extraRays, color, distance, directionDegree);

		baseRayNum = rayNum;
		start.set(x, y);
		rays = new Ray[this.rayNum];
		for (int i = 0; i < this.rayNum; i++) {
			rays[i] = new Ray(start);
		}
		lightMesh = new Mesh(
				Mesh.VertexDataType.VertexArray, false, vertexNum, 0, new VertexAttribute(VertexAttributes.Usage.Position, 2,
				"vertex_positions"), new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "quad_colors"),
				new VertexAttribute(VertexAttributes.Usage.Generic, 1, "s"));
		softShadowMesh = new Mesh(
				Mesh.VertexDataType.VertexArray, false, vertexNum * 2, 0, new VertexAttribute(VertexAttributes.Usage.Position, 2,
				"vertex_positions"), new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "quad_colors"),
				new VertexAttribute(VertexAttributes.Usage.Generic, 1, "s"));

		setEndPoints();
		updateAABB();
		setRayDefaults();
		setMesh();
	}

	// default values, no rotation
	protected float sorterSin = 0;
	protected float sorterCos = 1;
	Comparator<Ray> sorter = new Comparator<Ray>() {
		@Override public int compare (Ray r1, Ray r2) {
			if (r1.x == r2.x && r1.y == r2.y) return 0;
			// rotate points so first one is at the top
			float r1x = (r1.x - start.x) * sorterCos - (r1.y - start.y) * sorterSin;
			float r1y = (r1.x - start.x) * sorterSin + (r1.y - start.y) * sorterCos;
			float r2x = (r2.x - start.x) * sorterCos - (r2.y - start.y) * sorterSin;
			float r2y = (r2.x - start.x) * sorterSin + (r2.y - start.y) * sorterCos;

			if (r1x == r2x && r2x == r2y) return 0;
			if (r1x >= 0 && r2x < 0) return -1;
			if (r1x < 0 && r2x >= 0) return 1;
			if (MathUtils.isZero(r1x) && MathUtils.isZero(r2x)) {
				if (r1y >= 0 || r2y >= 0)
					return (r1.y > r2.y)?-1:1;
				return (r2.y > r1.y)?-1:1;
			}

			// compute the cross product of vectors (center -> a) x (center -> b)
			float det = r1x * r2y - r2x * r1y;
			if (det < 0) return -1;
			if (det > 0) return 1;

			// points a and b are on the same line from the center
			// check which point is closer to the center
			float d1 = r1x * r1x + r1y * r1y;
			float d2 = r2x * r2x + r2y * r2y;
			return (d1 > d2)?-1:1;
		}
	};

	protected Ray current;
	protected RayCastCallback perfectRay = new RayCastCallback() {
		@Override
		final public float reportRayFixture(Fixture fixture, Vector2 point,
				Vector2 normal, float fraction) {
			if ((globalFilterA != null) && !globalContactFilter(fixture)) return -1;
			if ((filterA != null) && !contactFilter(fixture)) return -1;
			if (ignoreBody && fixture.getBody() == getBody()) return -1;

			current.set(point.x, point.y, fraction);
			return fraction;
		}
	};


	private static Vector2 vert1 = new Vector2();
	private static Vector2 vert2 = new Vector2();
	private static Vector2 tmp3 = new Vector2();
	private static Vector2 tmp4 = new Vector2();
	private static Vector2 tmp5 = new Vector2();

	protected Array<Fixture> foundFixtures = new Array<Fixture>();

	protected int lastStaticFixtures = 0;
	protected int staticFixtures = 0;
	protected int otherFixtures = 0;
	protected QueryCallback smoothCallback = new QueryCallback() {
		@Override public boolean reportFixture (Fixture fixture) {
			// NOTE we do this because chain shape is reported for each segment in aabb, we test entire shape, so we dont care
			if (!foundFixtures.contains(fixture, true)) {
				if (fixture.getBody().getType() == BodyDef.BodyType.StaticBody) {
					if (!ignoreStaticBodies) {
						staticFixtures++;
						foundFixtures.add(fixture);
					}
				} else {
					otherFixtures++;
					foundFixtures.add(fixture);
				}
			}
			return true;
		}
	};

	// small enough value used to translate ends of the rays to the side when targeting shape points.
	// smaller values may produce precision errors
	protected float offsetSize = 0.02f;
	protected void updateRays () {
		for (Fixture fixture : foundFixtures) {
			Shape shape = fixture.getShape();
			Body body = fixture.getBody();
			float dst2 = distance * distance;
			switch (shape.getType()) {
			case Circle:
				CircleShape circle = (CircleShape)shape;
				Vector2 cp = body.getWorldPoint(circle.getPosition());
				float r = circle.getRadius();
				if (cp.dst2(start) <= (distance + r) * (distance + r)) {
					// we return true, not false cus most things need 2 rays
					if (currentRayNum + 5 >= rayNum) continue;
					addRay(cp, dst2);
					if (CircleUtils.findTangents(cp, r, start, tmp3, tmp4)) {
						// TODO do we want more points between center and tangents?
						// can look bad when light has few base points and circle is big
						// could calculate angle between tangent and center ray and use that to add some extras
						addRay(tmp3, dst2, offsetSize);
						addRay(tmp4, dst2, offsetSize);
					}
				}
				break;
			case Polygon: // fallthrough to Chain
			case Chain:
				int vc = getVertexCount(shape);
				getVertex(fixture, vc - 1, vert1);
				// add first corner if needed
				if (vert1.dst2(start) <= dst2) {
					if (currentRayNum + 2 >= rayNum) break;
					// we dont shoot directly at the corner, as sometimes the ray goes straight through it
					addRay(vert1, dst2, offsetSize);
				}
				for (int i = 0; i < vc; i++) {
					getVertex(fixture, i, vert2);
					int found = CircleUtils.findIntersections(start, distance, vert1, vert2, tmp3, tmp4);
					// NOTE a bit of margin, cus floats
					// NOTE checking for dest saves us a bunch of rays for points that are not on the line
					if (found == 1) {
						if (currentRayNum + 1 >= rayNum) break;
						if (tmp3.dst2(start) <= dst2 + 0.001f
								&& MathUtils.isZero(Intersector.nearestSegmentPoint(vert1, vert2, tmp3, tmp5).dst2(tmp3), 0.01f)) {
							addRay(tmp3, dst2);
						}
					} else if (found == 2) {
						if (currentRayNum + 2 >= rayNum) break;
						if (tmp3.dst2(start) <= dst2 + 0.001f
								&& MathUtils.isZero(Intersector.nearestSegmentPoint(vert1, vert2, tmp3, tmp5).dst2(tmp3), 0.01f)) {
							addRay(tmp3, dst2);
						}
						if (tmp4.dst2(start) <= dst2 + 0.001f
								&& MathUtils.isZero(Intersector.nearestSegmentPoint(vert1, vert2, tmp4, tmp5).dst2(tmp4), 0.01f)) {
							addRay(tmp4, dst2);
						}
					}
					// also add corner
					if (vert2.dst2(start) <= dst2) {
						if (currentRayNum + 2 >= rayNum) break;
						// we dont shoot directly at the corner, as sometimes the ray goes straight through it
						addRay(vert2, dst2, offsetSize);
					}
					vert1.set(vert2);
				}
				break;
			// edge is used for ghost vertices we don't care about it
			case Edge: break;
			default:
				Gdx.app.log("SmoothPositionalLight", "Not handled shape type: " + shape.getType().name());
				break;
			}
		}
	}

	protected static Vector2 getVertex(Fixture fixture, int id, Vector2 out) {
		Shape shape = fixture.getShape();
		if (shape instanceof ChainShape) {
			((ChainShape)shape).getVertex(id, out);
		} else {
			((PolygonShape)shape).getVertex(id, out);
		}
		return out.set(fixture.getBody().getWorldPoint(out));
	}

	protected static int getVertexCount(Shape shape) {
		if (shape instanceof ChainShape) {
			return ((ChainShape)shape).getVertexCount();
		} else {
			return ((PolygonShape)shape).getVertexCount();
		}
	}

	protected int startRayId;
	protected int onePastEndRayId;
	protected abstract void updateMesh();

	// lets hope this gets inlined
	private void addRay(Vector2 src, float len2) {
		candidate.set(src).sub(start).setLength2(len2).add(start);
		if (accept(candidate)) {
			rays[currentRayNum++].set(candidate);
		}
	}

	private static Vector2 perp = new Vector2();
	private void addRay(Vector2 src, float len2, float off) {
		addRay(src, len2, off, 1);
		addRay(src, len2, off, -1);
	}

	private void addRay(Vector2 src, float len2, float off, int side) {
		candidate.set(src).sub(start);
		perp.set(-candidate.y * side, candidate.x * side).setLength2(off * off);
		candidate.add(perp);
		candidate.setLength2(len2).add(start);
		if (accept(candidate)) {
			rays[currentRayNum++].set(candidate);
		}
	}

	protected Ray candidate = new Ray(start);
	protected boolean accept(Ray candidate) {
		// if it is too close to start, ignore. b2d doesnt like 0 len rays
		if (start.epsilonEquals(candidate, 0.01f)) {
			return false;
		}
		// if it is a duplicate, we dont want it
		for (int i = 0; i < currentRayNum; i++) {
			Ray ray = rays[i];
			if (ray.x == candidate.x && ray.y == candidate.y) {
				return false;
			}
		}
		return true;
	}

	@Override
	void render() {
		if (rayHandler.culling && culled) return;
		rayHandler.lightRenderedLastFrame++;
		lightMesh.render(
				rayHandler.lightShader, GL20.GL_TRIANGLE_FAN, 0, currentRayNum + 1);

		if (soft && !xray) {
			softShadowMesh.render(
					rayHandler.lightShader,
					GL20.GL_TRIANGLE_STRIP,
					0,
					(currentRayNum) * 2);
		}
	}

	@Override protected void update () {
		updateBody();
		if (dirty) {
			setEndPoints();
			updateAABB();
		}
		if (cull()) return;
		if (staticLight && !dirty) return;
		queryWorld();
		if (!isSleeping) {
			setRayDefaults();
			updateMesh();
		}
		setMesh();
		if (currentRayNum > peakRayNum) peakRayNum = currentRayNum;
		dirty = false;
	}

	private void setRayDefaults () {
		// we want these first, so when we limit max number of points we get all of these
		for (int i = 0; i < baseRayNum; i++) {
			rays[i].set(mx[i], my[i]);
		}
		currentRayNum = baseRayNum;
		startRayId = 0;
		onePastEndRayId = baseRayNum;
	}

	protected void queryWorld () {
		if (rayHandler.world != null && !xray) {
			// we need to clear this so we can retest stuff
			foundFixtures.clear();
			staticFixtures = 0;
			otherFixtures = 0;
			// this will add a bunch of rays for each body in aabb
			rayHandler.world.QueryAABB(smoothCallback, aabb.x, aabb.y, aabb.x + aabb.width, aabb.y + aabb.height);
			if (allowSleeping && lastStaticFixtures == staticFixtures && otherFixtures == 0 && !dirty) {
				isSleeping = true;
				return;
			}
			isSleeping = false;
			lastStaticFixtures = staticFixtures;
		}
	}

	private void updateAABB () {
		// can probably skip this if dir/cone/pos doesnt change, maybe set endpoints?
		aabb.set(start.x, start.y, 0, 0);
		for (int i = 0; i < baseRayNum; i++) {
			f[i] = 1f;
			tmpEnd.x = endX[i] + start.x;
			mx[i] = tmpEnd.x;
			tmpEnd.y = endY[i] + start.y;
			my[i] = tmpEnd.y;
			extendBounds(aabb, tmpEnd.x, tmpEnd.y);
		}
		// extend slightly cus floats
		addBounds(aabb, distance * 0.01f);
	}

	protected abstract void setEndPoints();

	@Override
	protected void setRayNum(int rays) {
		super.setRayNum(rays);

		sin = new float[rays];
		cos = new float[rays];
		endX = new float[rays];
		endY = new float[rays];
	}

	protected void setMesh() {
		// ray starting point
		int size = 0;

		segments[size++] = start.x;
		segments[size++] = start.y;
		segments[size++] = colorF;
		segments[size++] = 1;
		// rays ending points.
		for (int i = startRayId; i < onePastEndRayId; i++) {
			Ray ray = rays[i];
			segments[size++] = ray.x;
			segments[size++] = ray.y;
			segments[size++] = colorF;
			segments[size++] = 1 - ray.fraction;
		}
		lightMesh.setVertices(segments, 0, size);

		if (!soft || xray) return;

		size = 0;
		// rays ending points.
		for (int i = startRayId; i < onePastEndRayId; i++) {
			Ray ray = rays[i];
			segments[size++] = ray.x;
			segments[size++] = ray.y;
			segments[size++] = colorF;
			final float s = (1 - ray.fraction);
			segments[size++] = s;
			segments[size++] = ray.x + s * softShadowLength * ray.cos;
			segments[size++] = ray.y + s * softShadowLength * ray.sin;
			segments[size++] = zeroColorBits;
			segments[size++] = 0f;
		}
		softShadowMesh.setVertices(segments, 0, size);
	}

	public static class Ray extends Vector2 {
		public Vector2 center;
		public float fraction;
		public float angle;
		public float sin, cos;

		public Ray (Vector2 center) {
			this.center = center;
			fraction = 1;
		}

		public Ray set (float x, float y) {
			return set(x, y, 1);
		}

		public Ray set (Vector2 v) {
			return set(v.x, v.y, 1);
		}

		public Ray set (Ray r) {
			return set(r.x, r.y, r.fraction);
		}

		public Ray set (float x, float y, float fraction) {
			this.x = x;
			this.y = y;
			this.fraction = fraction;
			angle = MathUtils.atan2(y - center.y, x - center.x);
			sin = MathUtils.sin(angle);
			cos = MathUtils.cos(angle);
			return this;
		}

		public void reset () {
			x = y = 0;
			angle = sin = cos = 0;
			fraction = 1;
		}

		@Override public String toString () {
			return String.format("Ray([%.2f, %.2f] a=%.2f, f=%.2f)", x, y, angle * MathUtils.radDeg, fraction);
		}
	}

	protected Color rayColor;
	protected Color hardEdgeColor;
	protected Color softEdgeColor;
	@Override public void setDebugColors (Color ray, Color hardEdge,  Color softEdge) {
		rayColor = ray;
		hardEdgeColor = hardEdge;
		softEdgeColor = softEdge;
	}

	public void debugDraw (ShapeRenderer renderer) {
		drawRays(renderer);
		drawEdge(renderer);
	}

	public void drawRays(ShapeRenderer renderer) {
		float sx = getX();
		float sy = getY();
		if (rayColor != null) {
			renderer.setColor(rayColor);
		} else {
			// semi-transparent Cyan
			renderer.setColor(0, 1, 1, .1f);
		}
		if (isSoft()) {
			int numVertices = softShadowMesh.getNumVertices();
			for (int i = 0; i < numVertices * 4 - 8; i += 8) {
				renderer.line(sx, sy, segments[i + 4], segments[i + 5]);
			}
		} else {
			// rays
			int numVertices = lightMesh.getNumVertices();
			for (int i = 4; i < numVertices * 4; i += 4) {
				renderer.line(sx, sy, segments[i], segments[i + 1]);
			}
		}
	}

	public void drawEdge(ShapeRenderer renderer) {
		float x1, y1, x2, y2;
		if (isSoft()) {
			if (softEdgeColor != null) {
				renderer.setColor(softEdgeColor);
			} else {
				// semi-transparent Yellow
				renderer.setColor(1, 1, 0, .25f);
			}
			int numVertices = softShadowMesh.getNumVertices();
			softShadowMesh.getVertices(segments);
			int count = numVertices * 4;
			x1 = segments[count - 8 + 4];
			y1 = segments[count - 8 + 5];
			renderer.line(x1, y1, start.x, start.y);
			x2 = segments[4];
			y2 = segments[5];
			renderer.line(start.x, start.y, x2, y2);
			for (int i = 0; i < count - 8; i += 8) {
				x1 = segments[i + 4];
				y1 = segments[i + 5];
				x2 = segments[(i + 8 + 4) % count];
				y2 = segments[(i + 8 + 5) % count];
				renderer.line(x1, y1, x2, y2);
			}
			lightMesh.getVertices(segments);
		}
		if (hardEdgeColor != null)
			renderer.setColor(hardEdgeColor);
		else {
			// semi-transparent Red
			renderer.setColor(1, 0, 0, .25f);
		}
		int numVertices = lightMesh.getNumVertices();
		int count = numVertices * 4;
		for (int i = 0; i < count; i += 4) {
			x1 = segments[i];
			y1 = segments[i + 1];
			x2 = segments[(i + 4) % count];
			y2 = segments[(i + 5) % count];
			renderer.line(x1, y1, x2, y2);
		}
	}

	@Override
	public boolean contains(float x, float y) {
		// fast fail
		final float x_d = start.x - x;
		final float y_d = start.y - y;
		final float dst2 = x_d * x_d + y_d * y_d;
		if (distance * distance <= dst2) return false;

		int intersects = 0;
		int numVertices = lightMesh.getNumVertices();
		lightMesh.getVertices(segments);
		float x1, y1, x2, y2;
		int count = numVertices * 4;
		for (int i = 0; i < count; i += 4) {
			x1 = segments[i];
			y1 = segments[i + 1];
			x2 = segments[(i + 4) % count];
			y2 = segments[(i + 5) % count];
			if (((y1 <= y && y < y2) || (y2 <= y && y < y1)) && x < ((x2 - x1) / (y2 - y1) * (y - y1) + x1)) intersects++;
		}
		if ((intersects & 1) == 1) return true;

		// NOTE soft is smaller than default mesh, so we test it last
		if (isSoft()) {
			intersects = 0;
			numVertices = softShadowMesh.getNumVertices();
			softShadowMesh.getVertices(segments);
			count = numVertices * 4;

			// NOTE we don't have start in the segments, so we need to test it manually
			x1 = segments[count - 8 + 4];
			y1 = segments[count - 8 + 5];
			x2 = start.x;
			y2 = start.y;
			if (((y1 <= y && y < y2) || (y2 <= y && y < y1)) && x < ((x2 - x1) / (y2 - y1) * (y - y1) + x1)) intersects++;

			x1 = start.x;
			y1 = start.y;
			x2 = segments[4];
			y2 = segments[5];
			if (((y1 <= y && y < y2) || (y2 <= y && y < y1)) && x < ((x2 - x1) / (y2 - y1) * (y - y1) + x1)) intersects++;

			// NOTE we skip last pair, cus it would close the loop we made with start point
			for (int i = 0; i < count - 8; i += 8) {
				x1 = segments[i + 4];
				y1 = segments[i + 5];
				x2 = segments[(i + 8 + 4) % count];
				y2 = segments[(i + 8 + 5) % count];
				if (((y1 <= y && y < y2) || (y2 <= y && y < y1)) && x < ((x2 - x1) / (y2 - y1) * (y - y1) + x1)) intersects++;
			}
			return (intersects & 1) == 1;
		}
		return false;
	}

	protected void updateBody() {
		if (body == null || staticLight) return;

		final Vector2 vec = body.getPosition();
		float angle = body.getAngle();
		final float cos = MathUtils.cos(angle);
		final float sin = MathUtils.sin(angle);
		final float dX = bodyOffsetX * cos - bodyOffsetY * sin;
		final float dY = bodyOffsetX * sin + bodyOffsetY * cos;
		start.x = vec.x + dX;
		start.y = vec.y + dY;
	}

	protected boolean cull() {
		culled = rayHandler.culling
				&& !(aabb.x < rayHandler.x2 && aabb.x + aabb.width > rayHandler.x1
				&& aabb.y < rayHandler.y2 && aabb.y + aabb.height > rayHandler.y1);
		return culled;
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
		if (staticLight) dirty = true;
	}

	@Override
	public void setPosition(Vector2 position) {
		setPosition(position.x, position.y);
	}

	@Override
	public void setPosition(float x, float y) {
		if (start.epsilonEquals(x, y, 0.001f)) return;
		start.x = x;
		start.y = y;
		dirty = true;
	}

	@Override
	public void setDistance(float dist) {
		dist *= RayHandler.gammaCorrectionParameter;
		dist = dist < 0.01f ? 0.01f : dist;
		if (MathUtils.isEqual(distance, dist)) return;
		distance = dist;
		dirty = true;
	}

	@Override
	public Vector2 getPosition() {
		tmpPosition.x = start.x;
		tmpPosition.y = start.y;
		return tmpPosition;
	}

	public Body getBody() {
		return body;
	}

	@Override
	public float getX() {
		return start.x;
	}

	@Override
	public float getY() {
		return start.y;
	}

	public void setDirection(float newDir) {
	}

	@Override public boolean isSleeping () {
		return isSleeping;
	}

	public void setAllowSleeping(boolean allowSleeping) {
		this.allowSleeping = allowSleeping;
	}

	public boolean isAllowSleeping () {
		return allowSleeping;
	}

	public boolean isIgnoreStaticBodies () {
		return ignoreStaticBodies;
	}

	public void setIgnoreStaticBodies (boolean ignoreStaticBodies) {
		this.ignoreStaticBodies = ignoreStaticBodies;
		dirty = true;
	}

	protected void extendBounds (Rectangle bounds, float x, float y) {
		float x2 = bounds.x + bounds.width;
		float y2 = bounds.y + bounds.height;
		if (x > x2) bounds.width += x - x2;
		if (y > y2) bounds.height += y - y2;
		if (x < bounds.x) {
			bounds.width = x2 - x;
			bounds.x = x;
		}
		if (y < bounds.y) {
			bounds.height = y2 - y;
			bounds.y = y;
		}
	}

	protected Rectangle addBounds(Rectangle bounds, float val) {
		bounds.x -= val;
		bounds.y -= val;
		bounds.width += val * 2;
		bounds.height += val * 2;
		return bounds;
	}
}
