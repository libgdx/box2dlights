package box2dLight;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Sort;

import java.util.Comparator;

/**
 * A light with evenly spread rays along a line.
 * Shape can be altered with endScale and center offset
 *
 * Created by PiotrJ on 14/11/2015.
 */
@SuppressWarnings("Duplicates")
public class SmoothLineLight extends Light implements DebugLight {
	protected Vector2 pos = new Vector2();
	protected Vector2 posEnd = new Vector2();
	protected Vector2 posEndSide = new Vector2();

	protected Body body;
	protected float bodyOffsetX;
	protected float bodyOffsetY;
	protected float bodyAngleOffset;

	protected Rectangle aabb = new Rectangle();
	protected Polygon bounds;
	protected float boundsVerts[];
	protected float halfWidth;

	protected float startX[];
	protected float startY[];
	protected float endX[];
	protected float endY[];
	protected float offsets[];

	protected LineRay[] lineRays;
	protected int currentRayNum;
	protected int baseRayNum;
	protected float centerOffset;
	protected boolean isSleeping;
	protected boolean allowSleeping = true;
	protected boolean ignoreStaticBodies = false;
	protected float endColorScale = 1;

	public SmoothLineLight (RayHandler rayHandler, int rays, Color color, float distance, float directionDegree,
		float width, int extraRays) {
		super(rayHandler, rays + extraRays, color, distance, directionDegree);
		setWidth(width);
		setBaseRayNum(rays);
		lineRays = new LineRay[rayNum];
		for (int i = 0; i < rayNum; i++) {
			lineRays[i] = new LineRay();
		}

		bounds = new Polygon(boundsVerts = new float[8]);

		lightMesh = new Mesh(
			Mesh.VertexDataType.VertexArray, false, vertexNum * 2, 0, new VertexAttribute(VertexAttributes.Usage.Position, 2,
			"vertex_positions"), new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "quad_colors"),
			new VertexAttribute(VertexAttributes.Usage.Generic, 1, "s"));
		softShadowMesh = new Mesh(
			Mesh.VertexDataType.VertexArray, false, vertexNum * 2, 0, new VertexAttribute(VertexAttributes.Usage.Position, 2,
			"vertex_positions"), new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "quad_colors"),
			new VertexAttribute(VertexAttributes.Usage.Generic, 1, "s"));

		setPoints();
		updateAABB();
		setRayDefaults();
		setMesh();
	}

	@Override void update () {
		updateBody();
		if (dirty) {
			setPoints();
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
		dirty = false;
	}

	private void setRayDefaults () {
		for (int i = 0; i < baseRayNum; i++) {
			LineRay ray = lineRays[i];
			ray.start.set(pos.x + startX[i], pos.y + startY[i]);
			ray.end.set(pos.x + endX[i], pos.y + endY[i]);
			ray.fraction = 1;
			ray.calcOffset(pos, posEnd);
		}
		currentRayNum = baseRayNum;
	}

	private void queryWorld () {
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
		aabb.set(pos.x, pos.y, 0, 0);
		extendAABB(aabb, pos.x + startX[0], pos.y + startY[0]);
		extendAABB(aabb, pos.x + endX[0], pos.y + endY[0]);
		extendAABB(aabb, pos.x + startX[baseRayNum - 1], pos.y + startY[baseRayNum - 1]);
		extendAABB(aabb, pos.x + endX[baseRayNum - 1], pos.y + endY[baseRayNum - 1]);
		addAABB(aabb, 0.1f);

		float deg = direction + 90;
		if ( deg >= 360) deg -= 360;
		float offX = MathUtils.cosDeg(deg) * distance;
		float offY = MathUtils.sinDeg(deg) * distance;
		bounds.setPosition(pos.x + offX / 2, pos.y + offY / 2);
		bounds.setRotation(direction);
		posEnd.set(pos).add(offX + cOffX, offY + cOffY);
		posEndSide.set(pos.x + startX[0], pos.y + startY[0]).add(offX + cOffX, offY + cOffY);
	}

	private float cOffX;
	private float cOffY;
	private void setPoints () {
		float offX = MathUtils.cosDeg(direction);
		float offY = MathUtils.sinDeg(direction);

		float deg = direction + 90;
		if ( deg >= 360) deg -= 360;

		float sin = MathUtils.sinDeg(deg);
		float cos = MathUtils.cosDeg(deg);
		float step = halfWidth *2 / (baseRayNum-1);
		int rayId = 0;
		cOffX = offX * centerOffset;
		cOffY = offY * centerOffset;
		for (int i = 0; i < baseRayNum; i++) {
			float offset = i * step - halfWidth;
			startX[rayId] = offX * offset;
			startY[rayId] = offY * offset;
			offsets[rayId] = offset;
			endX[rayId] = offX * offset * endScale + cOffX + distance * cos;
			endY[rayId] = offY * offset * endScale + cOffY + distance * sin;
			rayId++;
		}
		// offset so we go a little beyond the line, cus floats
		float height = distance/2 + 0.005f;
		boundsVerts[0] = -halfWidth;
		boundsVerts[1] = -height;
		boundsVerts[2] = -halfWidth * endScale + centerOffset;
		boundsVerts[3] = height;
		boundsVerts[4] = halfWidth * endScale + centerOffset;
		boundsVerts[5] = height;
		boundsVerts[6] = halfWidth;
		boundsVerts[7] = -height;
	}

	private float endScale = 1;

	protected Vector2 tmpPerp = new Vector2();
	private void setMesh () {
		int size = 0;
		for (int i = 0; i < currentRayNum; i++) {
			LineRay ray = lineRays[i];
			segments[size++] = ray.start.x;
			segments[size++] = ray.start.y;
			segments[size++] = colorF;
			segments[size++] = 1;
			segments[size++] = ray.end.x;
			segments[size++] = ray.end.y;
			segments[size++] = colorF;
			segments[size++] = 1 - ray.fraction * endColorScale;
		}
		lightMesh.setVertices(segments, 0, size);

		if (!soft || xray) return;

		size = 0;
		// rays ending points.
		for (int i = 0; i < currentRayNum; i++) {
			LineRay ray = lineRays[i];
			segments[size++] = ray.end.x;
			segments[size++] = ray.end.y;
			segments[size++] = colorF;
			final float s = (1 - ray.fraction * endColorScale);
			segments[size++] = s;
			tmpPerp.set(ray.end).sub(ray.start).nor().scl(softShadowLength * s).add(ray.end);
			segments[size++] = tmpPerp.x;
			segments[size++] = tmpPerp.y;
			segments[size++] = zeroColorBits;
			segments[size++] = 0f;
		}
		softShadowMesh.setVertices(segments, 0, size);
	}

	protected RayCastCallback rayCB = new RayCastCallback() {
		@Override
		final public float reportRayFixture(Fixture fixture, Vector2 point,
			Vector2 normal, float fraction) {
			if ((globalFilterA != null) && !globalContactFilter(fixture)) return -1;
			if ((filterA != null) && !contactFilter(fixture)) return -1;
			if (ignoreBody && fixture.getBody() == getBody()) return -1;

			lineRays[currentId].setEnd(point).setFraction(fraction);
			return fraction;
		}
	};

	// small enough value used to translate ends of the rays to the side when targeting shape points.
	// smaller values may produce precision errors
	protected float offsetSize = 0.02f;
	protected float offsetX;
	protected float offsetY;

	protected static Vector2 polyV1 = new Vector2();
	protected static Vector2 polyV2 = new Vector2();
	protected static Vector2 vert1 = new Vector2();
	protected static Vector2 vert2 = new Vector2();
	protected static Vector2 tmp3 = new Vector2();
	protected static Vector2 tmp4 = new Vector2();
	protected static Vector2 tmp5 = new Vector2();
	protected static Vector2 tmp6 = new Vector2();
	protected static Vector2 virtualStart = new Vector2();

	protected int lastStaticFixtures = 0;
	protected int staticFixtures = 0;
	protected int otherFixtures = 0;

	protected QueryCallback smoothCallback = new QueryCallback() {
		@Override public boolean reportFixture (Fixture fixture) {
			// NOTE we do this because chain shape is reported for each segment in aabb, we test entire shape, so we dont care
			if (!foundFixtures.contains(fixture)) {
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

	protected void updateAngleRays () {
		for (Fixture fixture : foundFixtures) {
			Shape shape = fixture.getShape();
			float offX;
			float offY;
			Body body = fixture.getBody();
			switch (shape.getType()) {
			case Circle:
				CircleShape circle = (CircleShape)shape;
				Vector2 cp = body.getWorldPoint(circle.getPosition());
				float r = circle.getRadius();
				vert1.set(cp);
				Intersector.intersectLines(vert1, virtualStart, pos, firstRay.start, tmp4);
				Intersector.intersectLines(vert1, virtualStart, posEnd, posEndSide, tmp5);
				addRay(tmp4, tmp5);

				// we need to find out angle to offset the ray targets so they end up in correct places around the circle
				float angle = vert1.set(tmp5).sub(tmp4).angle();
				float deg = angle + 90;
				if ( deg >= 360) deg -= 360;
				offX = MathUtils.cosDeg(deg);
				offY = MathUtils.sinDeg(deg);

				vert1.set(cp).add(offX * r * .5f, offY * r * .5f);
				// is the check faster then casting extra rays? who knows!
				if (inBounds(vert1.x, vert1.y)) {
					Intersector.intersectLines(vert1, virtualStart, pos, firstRay.start, tmp4);
					Intersector.intersectLines(vert1, virtualStart, posEnd, posEndSide, tmp5);
					fastAddRay(tmp4, tmp5);
				}

				vert1.set(cp).add(-offX * r * .5f, offY * -r * .5f);
				if (inBounds(vert1.x, vert1.y)) {
					Intersector.intersectLines(vert1, virtualStart, pos, firstRay.start, tmp4);
					Intersector.intersectLines(vert1, virtualStart, posEnd, posEndSide, tmp5);
					fastAddRay(tmp4, tmp5);
				}
				vert1.set(cp).add(offX * (r + offsetSize), offY * (r + offsetSize));
				if (inBounds(vert1.x, vert1.y)) {
					Intersector.intersectLines(vert1, virtualStart, pos, firstRay.start, tmp4);
					Intersector.intersectLines(vert1, virtualStart, posEnd, posEndSide, tmp5);
					fastAddRay(tmp4, tmp5);
				}
				vert1.set(cp).add(offX * (r - offsetSize), offY * (r - offsetSize));
				if (inBounds(vert1.x, vert1.y)) {
					Intersector.intersectLines(vert1, virtualStart, pos, firstRay.start, tmp4);
					Intersector.intersectLines(vert1, virtualStart, posEnd, posEndSide, tmp5);
					fastAddRay(tmp4, tmp5);
				}
				vert1.set(cp).add(offX * (-r + offsetSize), offY * (-r + offsetSize));
				if (inBounds(vert1.x, vert1.y)) {
					Intersector.intersectLines(vert1, virtualStart, pos, firstRay.start, tmp4);
					Intersector.intersectLines(vert1, virtualStart, posEnd, posEndSide, tmp5);
					fastAddRay(tmp4, tmp5);
				}
				vert1.set(cp).add(offX * -(r + offsetSize), offY * -(r + offsetSize));
				if (inBounds(vert1.x, vert1.y)) {
					Intersector.intersectLines(vert1, virtualStart, pos, firstRay.start, tmp4);
					Intersector.intersectLines(vert1, virtualStart, posEnd, posEndSide, tmp5);
					fastAddRay(tmp4, tmp5);
				}
				break;
			case Polygon: // fallthrough to Chain
			case Chain:
				int vc = getVertexCount(shape);
				getVertex(fixture, vc - 1, vert1);

				offX = offsetX * offsetSize;
				offY = offsetY * offsetSize;
				if (inBounds(vert1.x, vert1.y)) {
					// clip our line with top and bottom edges of the light
					Intersector.intersectLines(vert1, virtualStart, pos, firstRay.start, tmp4);
					if (endScale == 0) {
						addRay(tmp4.add(-offX, -offY), firstRay.end);
						addRay(tmp4.add(offX * 2, offY * 2), firstRay.end);
					} else {
						// if endScale is 0, we get a triangle so we have to use extra point to form a line, not a ray
						Intersector.intersectLines(vert1, virtualStart, posEnd, posEndSide, tmp5);
						addRay(tmp4.add(-offX, -offY), tmp5.add(-offX, -offY));
						addRay(tmp4.add(offX * 2, offY * 2), tmp5.add(offX * 2, offY * 2));
					}
				}

				for (int i = 0; i < vc; i++) {
					getVertex(fixture, i, vert2);
					if (inBounds(vert2.x, vert2.y)) {
						// clip our line with top and bottom edges of the light
						Intersector.intersectLines(vert2, virtualStart, pos, firstRay.start, tmp4);
						if (endScale == 0) {
							addRay(tmp4.add(-offX, -offY), firstRay.end);
							addRay(tmp4.add(offX * 2, offY * 2), firstRay.end);
						} else {
							// if endScale is 0, we get a triangle so we have to use extra point to form a line, not a ray
							Intersector.intersectLines(vert2, virtualStart, posEnd, posEndSide, tmp5);
							addRay(tmp4.add(-offX, -offY), tmp5.add(-offX, -offY));
							addRay(tmp4.add(offX * 2, offY * 2), tmp5.add(offX * 2, offY * 2));
						}
					}
					// if scale is 0, we already shoot a bunch of rays there
					if (endScale != 0 && Intersector.intersectSegments(firstRay.end, lastRay.end, vert1, vert2, tmp3)) {
						Intersector.intersectLines(tmp3, virtualStart, pos, firstRay.start, tmp4);
						addRay(tmp4, tmp3);
					}
					vert1.set(vert2);
				}
				break;
			// edge is used for ghost vertices we don't care about it
			case Edge: break;
			default:
				Gdx.app.log("SmoothLineLight", "Not handled shape type: " + shape.getType().name());
			}
		}
	}

	protected void updateStraightRays () {
		for (Fixture fixture : foundFixtures) {
			Shape shape = fixture.getShape();
			Body body = fixture.getBody();
			switch (shape.getType()) {
			case Circle:
				CircleShape circle = (CircleShape)shape;
				Vector2 cp = body.getWorldPoint(circle.getPosition());
				float r = circle.getRadius();

				tmp4.set(cp).sub(posEnd).add(pos);
				float angle = tmp5.set(cp).sub(tmp4).angle();
				float deg = angle + 90;
				if ( deg >= 360) deg -= 360;
				float offX = MathUtils.cosDeg(deg);
				float offY = MathUtils.sinDeg(deg);

				vert1.set(cp);
				vert2.set(cp).sub(posEnd).add(pos);
				if (inBounds(vert1.x, vert1.y)) {
					Intersector.intersectLines(vert1, vert2, pos, firstRay.start, tmp5);
					Intersector.intersectLines(vert1, vert2, posEnd, posEndSide, tmp6);
					addRay(tmp5, tmp6);
				}
				tmp3.set(vert1).add(offX * (r + offsetSize), offY * (r + offsetSize));
				tmp4.set(vert2).add(offX * (r + offsetSize), offY * (r + offsetSize));
				if (inBounds(tmp3.x, tmp3.y)) {
					Intersector.intersectLines(tmp3, tmp4, pos, firstRay.start, tmp5);
					Intersector.intersectLines(tmp3, tmp4, posEnd, posEndSide, tmp6);
					addRay(tmp5, tmp6);
				}
				tmp3.set(vert1).add(offX * (r - offsetSize), offY * (r - offsetSize));
				tmp4.set(vert2).add(offX * (r - offsetSize), offY * (r - offsetSize));
				if (inBounds(tmp3.x, tmp3.y)) {
					Intersector.intersectLines(tmp3, tmp4, pos, firstRay.start, tmp5);
					Intersector.intersectLines(tmp3, tmp4, posEnd, posEndSide, tmp6);
					addRay(tmp5, tmp6);
				}
				tmp3.set(vert1).add(-offX * (r + offsetSize), -offY * (r + offsetSize));
				tmp4.set(vert2).add(-offX * (r + offsetSize), -offY * (r + offsetSize));
				if (inBounds(tmp3.x, tmp3.y)) {
					Intersector.intersectLines(tmp3, tmp4, pos, firstRay.start, tmp5);
					Intersector.intersectLines(tmp3, tmp4, posEnd, posEndSide, tmp6);
					addRay(tmp5, tmp6);
				}
				tmp3.set(vert1).add(-offX * (r - offsetSize), -offY * (r - offsetSize));
				tmp4.set(vert2).add(-offX * (r - offsetSize), -offY * (r - offsetSize));
				if (inBounds(tmp3.x, tmp3.y)) {
					Intersector.intersectLines(tmp3, tmp4, pos, firstRay.start, tmp5);
					Intersector.intersectLines(tmp3, tmp4, posEnd, posEndSide, tmp6);
					addRay(tmp5, tmp6);
				}
				break;
			case Polygon: // fallthrough to Chain
			case Chain:
				int vc = getVertexCount(shape);
				getVertex(fixture, vc - 1, polyV1);
				vert1.set(polyV1);

				vert2.set(vert1).sub(posEnd).add(pos);
				if (inBounds(vert1.x, vert1.y)) {
					Intersector.intersectLines(vert1, vert2, pos, firstRay.start, tmp5);
					Intersector.intersectLines(vert1, vert2, posEnd, posEndSide, tmp6);
					addRay(tmp3.set(tmp5).add(offsetX * offsetSize, offsetY * offsetSize),
						tmp4.set(tmp6).add(offsetX * offsetSize, offsetY * offsetSize));
					addRay(tmp3.set(tmp5).add(-offsetX * offsetSize, -offsetY * offsetSize),
						tmp4.set(tmp6).add(-offsetX * offsetSize, -offsetY * offsetSize));
				}
				for (int i = 0; i < vc; i++) {
					getVertex(fixture, i, polyV2);
					vert2.set(polyV2);
					vert1.set(vert2).sub(posEnd).add(pos);
					if (inBounds(vert2.x, vert2.y)) {
						Intersector.intersectLines(vert1, vert2, pos, firstRay.start, tmp5);
						Intersector.intersectLines(vert1, vert2, posEnd, posEndSide, tmp6);
						addRay(tmp3.set(tmp5).add(offsetX * offsetSize, offsetY * offsetSize),
							tmp4.set(tmp6).add(offsetX * offsetSize, offsetY * offsetSize));
						addRay(tmp3.set(tmp5).add(-offsetX * offsetSize, -offsetY * offsetSize),
							tmp4.set(tmp6).add(-offsetX * offsetSize, -offsetY * offsetSize));
					}
					if (Intersector.intersectSegments(firstRay.end, lastRay.end, polyV1, polyV2, vert1)) {
						vert2.set(vert1).sub(posEnd).add(pos);
						Intersector.intersectLines(vert1, vert2, pos, firstRay.start, tmp5);
						Intersector.intersectLines(vert1, vert2, posEnd, posEndSide, tmp6);
						addRay(tmp5, tmp6);
					}

					polyV1.set(polyV2);
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

	private static int getVertexCount(Shape shape) {
		if (shape instanceof ChainShape) {
			return ((ChainShape)shape).getVertexCount();
		} else {
			return ((PolygonShape)shape).getVertexCount();
		}
	}

	private float epsilon = 0.01f;
	/**
	 * @param start must be on the line
	 * @param end
	 */
	private void addRay(Vector2 start, Vector2 end) {
		if (currentRayNum >= rayNum) return;
		// we dont care if target is outside of the bounds
		if (!inBounds(end.x, end.y)) return;
		if (!inBounds(start.x, start.y)) return;
		fastAddRay(start, end);
	}

	private void fastAddRay(Vector2 start, Vector2 end) {
		if (currentRayNum >= rayNum) return;
		// if they are too close we dont care, box2d doesnt like very short rays
		if (MathUtils.isEqual(start.x, end.x, epsilon) && MathUtils.isEqual(start.y, end.y, epsilon)) return;
		// we dont want duplicate rays
		for (int i = 0; i < currentRayNum; i++) {
			if (endScale >= 0 && lineRays[i].start.epsilonEquals(start, epsilon)) return;
			if (endScale < 0 && lineRays[i].end.epsilonEquals(end, epsilon)) return;
		}
		lineRays[currentRayNum++].set(start, end).calcOffset(pos, posEnd).setFraction(1);
	}

	private Comparator<LineRay> sorter = new Comparator<LineRay>() {
		@Override public int compare (LineRay o1, LineRay o2) {
			// this should never happen, as we reject rays that at the same spot
			if (o1.offset == o2.offset) return 0;
			return (o1.offset > o2.offset)?1:-1;
		}
	};

	protected ObjectSet<Fixture> foundFixtures = new ObjectSet<Fixture>();
	protected int currentId;
	protected LineRay firstRay;
	protected LineRay lastRay;
	private void updateMesh () {
		firstRay = lineRays[0];
		lastRay = lineRays[baseRayNum - 1];
		if (rayHandler.world != null && !xray) {
			offsetX = MathUtils.cosDeg(direction);
			offsetY = MathUtils.sinDeg(direction);
			if (MathUtils.isEqual(endScale, 1f)) {
				updateStraightRays();
			} else {
				Intersector.intersectLines(pos, posEnd, firstRay.start, firstRay.end, virtualStart);
				updateAngleRays();
			}

			// shoot check each ray
			for (int i = 0; i < currentRayNum; i++) {
				currentId = i;
				LineRay ray = lineRays[i];
				rayHandler.world.rayCast(rayCB, ray.start, ray.end);
			}

			// we need to sort if stuff was added to set the mesh properly
			if (currentRayNum >= baseRayNum) {
				// sort only if we added rays
				Sort.instance().sort(lineRays, sorter, 0, currentRayNum);
			}
		}
	}

	@Override void render () {
		if (rayHandler.culling && culled) return;
		rayHandler.lightRenderedLastFrame++;
		lightMesh.render(
			rayHandler.lightShader, GL20.GL_TRIANGLE_STRIP, 0, currentRayNum * 2);

		if (soft && !xray) {
			softShadowMesh.render(
				rayHandler.lightShader, GL20.GL_TRIANGLE_STRIP, 0, currentRayNum * 2);
		}
	}

	private static class LineRay {
		protected float fraction;
		protected Vector2 start = new Vector2();
		protected Vector2 end = new Vector2();
		protected float offset;

		public LineRay set (Vector2 start, Vector2 end) {
			return set(start.x, start.y, end.x, end.y);
		}

		public LineRay set(float sx, float sy, float ex, float ey) {
			start.set(sx, sy);
			end.set(ex, ey);
			return this;
		}

		public LineRay calcOffset(Vector2 lStart, Vector2 lEnd) {
			offset = -lStart.dst2(start) * Intersector.pointLineSide(lStart, lEnd, start);
			return this;
		}

		public LineRay setFraction (float f) {
			fraction = f;
			return this;
		}

		public LineRay setEnd (Vector2 end) {
			this.end.set(end);
			return this;
		}

		@Override public String toString () {
			return "LineRay{" +
				"start=" + start.x + ", " + start.y +
				", end=" + end.x + ", " + end.y +
				'}';
		}
	}

	protected boolean inBounds(float x, float y) {
		if (!aabb.contains(x, y)) return false;
		return bounds.contains(x, y);
	}

	@Override public boolean contains (float x, float y) {
		if (!aabb.contains(x, y)) return false;
		// check regular mesh first, as it is bigger
		int intersects = 0;
		int numVertices = lightMesh.getNumVertices();
		lightMesh.getVertices(segments);
		float x1, y1, x2, y2;
		int count = numVertices * 4;
		// left edge
		x1 = segments[0];
		y1 = segments[1];
		x2 = segments[4];
		y2 = segments[5];
		if (((y1 <= y && y < y2) || (y2 <= y && y < y1)) && x < ((x2 - x1) / (y2 - y1) * (y - y1) + x1)) intersects++;
		// top and bottom edges
		for (int i = 0; i < count - 4; i += 4) {
			x1 = segments[i];
			y1 = segments[i + 1];
			x2 = segments[(i + 8) % count];
			y2 = segments[(i + 8 + 1) % count];
			if (((y1 <= y && y < y2) || (y2 <= y && y < y1)) && x < ((x2 - x1) / (y2 - y1) * (y - y1) + x1)) intersects++;
		}
		// right edge
		x1 = segments[count - 8];
		y1 = segments[count - 8 + 1];
		x2 = segments[count - 8 + 4];
		y2 = segments[count - 8 + 5];
		if (((y1 <= y && y < y2) || (y2 <= y && y < y1)) && x < ((x2 - x1) / (y2 - y1) * (y - y1) + x1)) intersects++;
		if((intersects & 1) == 1) return true;

		// check soft mesh if there is one
		if (isSoft()) {
			intersects = 0;
			numVertices = softShadowMesh.getNumVertices();
			softShadowMesh.getVertices(segments);
			count = numVertices * 4;
			// left edge
			x1 = segments[0];
			y1 = segments[1];
			x2 = segments[4];
			y2 = segments[5];
			if (((y1 <= y && y < y2) || (y2 <= y && y < y1)) && x < ((x2 - x1) / (y2 - y1) * (y - y1) + x1)) intersects++;
			// top and bottom edges
			for (int i = 0; i < count - 8; i += 8) {
				// bottom
				x1 = segments[i];
				y1 = segments[i + 1];
				x2 = segments[(i + 8) % count];
				y2 = segments[(i + 8 + 1) % count];
				if (((y1 <= y && y < y2) || (y2 <= y && y < y1)) && x < ((x2 - x1) / (y2 - y1) * (y - y1) + x1)) intersects++;
				// top
				x1 = segments[i + 4];
				y1 = segments[i + 5];
				x2 = segments[(i + 8 + 4) % count];
				y2 = segments[(i + 8 + 5) % count];
				if (((y1 <= y && y < y2) || (y2 <= y && y < y1)) && x < ((x2 - x1) / (y2 - y1) * (y - y1) + x1)) intersects++;
			}
			// right edge
			x1 = segments[count - 8];
			y1 = segments[count - 8 + 1];
			x2 = segments[count - 8 + 4];
			y2 = segments[count - 8 + 5];
			if (((y1 <= y && y < y2) || (y2 <= y && y < y1)) && x < ((x2 - x1) / (y2 - y1) * (y - y1) + x1)) intersects++;
			return (intersects & 1) == 1;
		}
		return false;
	}

	@Override
	protected void setRayNum(int rays) {
		if (rays < MIN_RAYS)
			rays = MIN_RAYS;

		rayNum = rays;
		vertexNum = rays + 1;

		segments = new float[vertexNum * 8];
	}

	protected void setBaseRayNum(int rays) {
		if (rays < MIN_RAYS)
			rays = MIN_RAYS;
		baseRayNum = rays;
		startX = new float[rays];
		startY = new float[rays];
		endX = new float[rays];
		endY = new float[rays];
		offsets = new float[rays];
	}

	protected void updateBody() {
		if (body == null || staticLight) return;

		final Vector2 vec = body.getPosition();
		float angle = body.getAngle();
		final float cos = MathUtils.cos(angle);
		final float sin = MathUtils.sin(angle);
		final float dX = bodyOffsetX * cos - bodyOffsetY * sin;
		final float dY = bodyOffsetX * sin + bodyOffsetY * cos;
		pos.x = vec.x + dX;
		pos.y = vec.y + dY;
	}

	protected boolean cull() {
		culled = rayHandler.culling
				&& !(aabb.x < rayHandler.x2 && aabb.x + aabb.width > rayHandler.x1
				&& aabb.y < rayHandler.y2 && aabb.y + aabb.height > rayHandler.y1);
		return culled;
	}

	protected Color rayColor;
	protected Color hardEdgeColor;
	protected Color softEdgeColor;
	@Override public void setDebugColors (Color ray, Color hardEdge,  Color softEdge) {
		rayColor = ray;
		hardEdgeColor = hardEdge;
		softEdgeColor = softEdge;
	}

	@Override public void debugDraw (ShapeRenderer renderer) {
		drawRays(renderer);
		drawEdge(renderer);
	}

	public void drawRays(ShapeRenderer renderer) {
		if (rayColor != null) {
			renderer.setColor(rayColor);
		} else {
			// semi-transparent Cyan
			renderer.setColor(0, 1, 1, .1f);
		}
		if (isSoft()) {
			int numVertices = softShadowMesh.getNumVertices();
			softShadowMesh.getVertices(segments);
			for (int i = 0; i < numVertices * 4 - 8; i += 8) {
				renderer.line(segments[i], segments[i + 1], segments[i + 4], segments[i + 5]);
			}
			numVertices = lightMesh.getNumVertices();
			lightMesh.getVertices(segments);
			for (int i = 0; i < numVertices * 4; i += 8) {
				renderer.line(segments[i], segments[i + 1], segments[i + 4], segments[i + 5]);
			}
		} else {
			// rays
			int numVertices = lightMesh.getNumVertices();
			for (int i = 0; i < numVertices * 4; i += 8) {
				renderer.line(segments[i], segments[i + 1], segments[i + 4], segments[i + 5]);
			}
		}
	}

	public void drawEdge(ShapeRenderer renderer) {
		float x1, y1, x2, y2;
		if (isSoft()) {
			int numVertices = softShadowMesh.getNumVertices();
			if (softEdgeColor != null) {
				renderer.setColor(softEdgeColor);
			} else {
				// semi-transparent Yellow
				renderer.setColor(1, 1, 0, .25f);
			}
			// soft mesh edge
			softShadowMesh.getVertices(segments);
			// left edge
			x1 = segments[0];
			y1 = segments[1];
			x2 = segments[4];
			y2 = segments[5];
			renderer.line(x1, y1, x2, y2);
			int count = numVertices * 4;
			// top and bottom edges
			for (int i = 0; i < count - 8; i += 8) {
				// bottom
				x1 = segments[i];
				y1 = segments[i + 1];
				x2 = segments[(i + 8) % count];
				y2 = segments[(i + 8 + 1) % count];
				renderer.line(x1, y1, x2, y2);
				// top
				x1 = segments[i + 4];
				y1 = segments[i + 5];
				x2 = segments[(i + 8 + 4) % count];
				y2 = segments[(i + 8 + 5) % count];
				renderer.line(x1, y1, x2, y2);
			}
			// right edge
			x1 = segments[count - 8];
			y1 = segments[count - 8 + 1];
			x2 = segments[count - 8 + 4];
			y2 = segments[count - 8 + 5];
			renderer.line(x1, y1, x2, y2);
			lightMesh.getVertices(segments);
		}
		if (hardEdgeColor != null) {
			renderer.setColor(hardEdgeColor);
		} else {
			// semi-transparent Red
			renderer.setColor(1, 0, 0, .25f);
		}
		int numVertices = lightMesh.getNumVertices();
		// left edge
		x1 = segments[0];
		y1 = segments[1];
		x2 = segments[4];
		y2 = segments[5];
		renderer.line(x1, y1, x2, y2);
		int count = numVertices * 4;
		// top and bottom edges
		for (int i = 0; i < count - 4; i += 4) {
			x1 = segments[i];
			y1 = segments[i + 1];
			x2 = segments[(i + 8) % count];
			y2 = segments[(i + 8 + 1) % count];
			renderer.line(x1, y1, x2, y2);
		}
		// right edge
		x1 = segments[count - 8];
		y1 = segments[count - 8 + 1];
		x2 = segments[count - 8 + 4];
		y2 = segments[count - 8 + 5];
		renderer.line(x1, y1, x2, y2);
	}

	@Override public void setPosition (Vector2 position) {
		setPosition(position.x, position.y);
	}

	@Override public void setPosition (float x, float y) {
		if (pos.epsilonEquals(x, y, 0.001f)) return;
		pos.set(x, y);
		dirty = true;
	}

	@Override public Vector2 getPosition () {
		tmpPosition.set(pos);
		return tmpPosition;
	}

	@Override public float getX () {
		return pos.x;
	}

	@Override public float getY () {
		return pos.y;
	}

	@Override
	public void setDistance(float dist) {
		dist *= RayHandler.gammaCorrectionParameter;
		dist = dist < 0.01f ? 0.01f : dist;
		if (MathUtils.isEqual(distance, dist)) return;
		distance = dist;
		dirty = true;
	}

	@Override public float getDistance () {
		return distance;
	}

	@Override public void setDirection (float newDir) {
		newDir = newDir < 0 ? 360 - (-newDir % 360) : newDir % 360;
		if (newDir > 180) newDir -= 360;
		if (MathUtils.isEqual(direction, newDir)) return;
		direction = newDir;
		dirty = true;
	}

	@Override public float getDirection () {
		return direction;
	}

	public float getWidth () {
		return halfWidth * 2;
	}

	public void setWidth (float width) {
		float halfWidth = width/2;
		if (halfWidth < 0.1f) halfWidth = 0.1f;
		if (MathUtils.isEqual(this.halfWidth, halfWidth)) return;
		this.halfWidth = halfWidth;
		dirty = true;
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

	public float getCenterOffset () {
		return centerOffset;
	}

	/**
	 * Move the point considered the centre of the top line of the light, changing the angle of rays
	 *
	 * @param centerOffset to set
	 */
	public void setCenterOffset (float centerOffset) {
		if (MathUtils.isEqual(this.centerOffset, centerOffset)) return;
		this.centerOffset = centerOffset;
		dirty = true;
	}

	/**
	 * Changes the scale of the end line of the light
	 * default is 1
	 * lower value will result in a "pointy" light
	 * larger valye will result in a "cone" light
	 *
	 * @param endScale to set
	 */
	public void setEndScale(float endScale) {
		if (MathUtils.isEqual(this.endScale, endScale)) return;
		this.endScale = endScale;
		dirty = true;
	}

	public float getEndScale () {
		return endScale;
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

	private void extendAABB (Rectangle bounds, float x, float y) {
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

	private Rectangle addAABB(Rectangle bounds, float val) {
		bounds.x -= val;
		bounds.y -= val;
		bounds.width += val * 2;
		bounds.height += val * 2;
		return bounds;
	}
}
