
package box2dLight;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Mesh.VertexDataType;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

/**
 * Abstract base class for all positional lights
 * 
 * <p>Extends {@link Light}
 * 
 * @author kalle_h
 */
public abstract class PositionalLight extends Light {

	Color tmpColor = new Color();

	protected final Vector2 tmpEnd = new Vector2();
	protected final Vector2 start = new Vector2();
	
	protected Body body;
	protected float bodyOffsetX;
	protected float bodyOffsetY;
	protected float bodyAngleOffset;
	
	protected float sin[];
	protected float cos[];

	protected float endX[];
	protected float endY[];
	
	/** 
	 * Creates new positional light and automatically adds it to the specified
	 * {@link RayHandler} instance.
	 * 
	 * @param rayHandler
	 *            not null instance of RayHandler
	 * @param rays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 * @param color
	 *            light color
	 * @param distance
	 *            light distance (if applicable)
	 * @param x
	 *            horizontal position in world coordinates
	 * @param y
	 *            vertical position in world coordinates
	 * @param directionDegree
	 *            direction in degrees (if applicable) 
	 */
	public PositionalLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y, float directionDegree) {
		super(rayHandler, rays, color, distance, directionDegree);
		start.x = x;
		start.y = y;

		Mesh.VertexDataType vertexDataType = Mesh.VertexDataType.VertexArray;
		if (Gdx.gl30 != null) {
			vertexDataType = VertexDataType.VertexBufferObjectWithVAO;
		}
		lightMesh = new Mesh(vertexDataType, false, vertexNum, 0, new VertexAttribute(Usage.Position, 2,
			"vertex_positions"), new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
			new VertexAttribute(Usage.Generic, 1, "s"));
		softShadowMesh = new Mesh(vertexDataType, false, vertexNum * 2, 0, new VertexAttribute(Usage.Position, 2,
			"vertex_positions"), new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
			new VertexAttribute(Usage.Generic, 1, "s"));
		setMesh();
	}
	
	@Override
	void update() {
		updateBody();
		
		if (cull()) return;
		if (staticLight && !dirty) return;
		
		dirty = false;
		updateMesh();
	}
	
	@Override
	void render() {
		if (rayHandler.culling && culled) return;

		rayHandler.lightRenderedLastFrame++;
		lightMesh.render(rayHandler.lightShader, GL20.GL_TRIANGLE_FAN, 0, vertexNum);

		if (soft && !xray && !rayHandler.pseudo3d) {
			softShadowMesh.render(
				rayHandler.lightShader,
				GL20.GL_TRIANGLE_STRIP,
				0,
				(vertexNum - 1) * 2);
		}
	}
	
	@Override
	public void attachToBody(Body body) {
		attachToBody(body, 0f, 0f, 0f);
	}
	
	/**
	 * Attaches light to specified body with relative offset
	 * 
	 * @param body
	 *            that will be automatically followed, note that the body
	 *            rotation angle is taken into account for the light offset
	 *            and direction calculations
	 * @param offsetX
	 *            horizontal relative offset in world coordinates
	 * @param offsetY
	 *            vertical relative offset in world coordinates
	 * 
	 */
	public void attachToBody(Body body, float offsetX, float offsetY) {
		attachToBody(body, offsetX, offsetY, 0f);
	}
	
	/**
	 * Attaches light to specified body with relative offset and direction
	 * 
	 * @param body
	 *            that will be automatically followed, note that the body
	 *            rotation angle is taken into account for the light offset
	 *            and direction calculations
	 * @param offsetX
	 *            horizontal relative offset in world coordinates
	 * @param offsetY
	 *            vertical relative offset in world coordinates
	 * @param degrees
	 *            directional relative offset in degrees 
	 */
	public void attachToBody(Body body, float offsetX, float offsetY, float degrees) {
		this.body = body;
		bodyOffsetX = offsetX;
		bodyOffsetY = offsetY;
		bodyAngleOffset = degrees;
		if (staticLight) dirty = true;
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

	/** @return horizontal starting position of light in world coordinates **/
	@Override
	public float getX() {
		return start.x;
	}

	/** @return vertical starting position of light in world coordinates **/
	@Override
	public float getY() {
		return start.y;
	}

	@Override
	public void setPosition(float x, float y) {
		start.x = x;
		start.y = y;
		if (staticLight) dirty = true;
	}

	@Override
	public void setPosition(Vector2 position) {
		start.x = position.x;
		start.y = position.y;
		if (staticLight) dirty = true;
	}

	public boolean contains(Vector2 pos) {
		return contains(pos.x, pos.y);
	}

	@Override
	public boolean contains(float x, float y) {
		// fast fail
		final float x_d = start.x - x;
		final float y_d = start.y - y;
		final float dst2 = x_d * x_d + y_d * y_d;
		if (distance * distance <= dst2) return false;

		// actual check
		boolean oddNodes = false;
		float x2 = mx[rayNum] = start.x;
		float y2 = my[rayNum] = start.y;
		float x1, y1;
		for (int i = 0; i <= rayNum; x2 = x1, y2 = y1, ++i) {
			x1 = mx[i];
			y1 = my[i];
			if (((y1 < y) && (y2 >= y)) || (y1 >= y) && (y2 < y)) {
				if ((y - y1) / (y2 - y1) * (x2 - x1) < (x - x1)) oddNodes = !oddNodes;
			}
		}
		return oddNodes;
	}
	
	@Override
	protected void setRayNum(int rays) {
		super.setRayNum(rays);
		
		sin = new float[rays];
		cos = new float[rays];
		endX = new float[rays];
		endY = new float[rays];
	}
	
	protected boolean cull() {
		culled = rayHandler.culling && !rayHandler.intersect(
					start.x, start.y, distance + softShadowLength);
		return culled;
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
		setDirection(bodyAngleOffset + angle * MathUtils.radiansToDegrees);
	}
	
	protected void updateMesh() {
		for (int i = 0; i < rayNum; i++) {
			m_index = i;
			f[i] = 1f;
			tmpEnd.x = endX[i] + start.x;
			mx[i] = tmpEnd.x;
			tmpEnd.y = endY[i] + start.y;
			my[i] = tmpEnd.y;
			if (rayHandler.world != null && !xray && !rayHandler.pseudo3d) {
				rayHandler.world.rayCast(ray, start, tmpEnd);
			}
		}
		setMesh();
	}

	protected void prepareFixtureData() {
		rayHandler.world.QueryAABB(
				dynamicShadowCallback,
				start.x - distance, start.y - distance,
				start.x + distance, start.y + distance);
	}

	protected void setMesh() {
		// ray starting point
		int size = 0;

		segments[size++] = start.x;
		segments[size++] = start.y;
		segments[size++] = colorF;
		segments[size++] = 1;
		// rays ending points.
		for (int i = 0; i < rayNum; i++) {
			segments[size++] = mx[i];
			segments[size++] = my[i];
			segments[size++] = colorF;
			segments[size++] = 1 - f[i];
		}
		lightMesh.setVertices(segments, 0, size);

		if (!soft || xray || rayHandler.pseudo3d) return;

		size = 0;
		// rays ending points.
		for (int i = 0; i < rayNum; i++) {
			segments[size++] = mx[i];
			segments[size++] = my[i];
			segments[size++] = colorF;
			final float s = (1 - f[i]);
			segments[size++] = s;
			segments[size++] = mx[i] + s * softShadowLength * cos[i];
			segments[size++] = my[i] + s * softShadowLength * sin[i];
			segments[size++] = zeroColorBits;
			segments[size++] = 0f;
		}
		softShadowMesh.setVertices(segments, 0, size);
	}

	protected void updateDynamicShadowMeshes() {
		int meshInd = 0;
		float colBits = rayHandler.ambientLight.toFloatBits();
		for (Fixture fixture : affectedFixtures) {
			LightData data = (LightData)fixture.getUserData();
			if (data == null || fixture.isSensor()) continue;

			int size = 0;
			float l;

			Shape fixtureShape = fixture.getShape();
			Shape.Type type = fixtureShape.getType();
			Body body = fixture.getBody();
			center.set(body.getWorldCenter());

			if (type == Shape.Type.Polygon || type == Shape.Type.Chain) {
				boolean isPolygon = (type == Shape.Type.Polygon);
				ChainShape cShape = isPolygon ?
						null : (ChainShape)fixtureShape;
				PolygonShape pShape = isPolygon ?
						(PolygonShape)fixtureShape : null;
				int vertexCount = isPolygon ?
						pShape.getVertexCount() : cShape.getVertexCount();
				int minN = -1;
				int maxN = -1;
				int minDstN = -1;
				float minDst = Float.POSITIVE_INFINITY;
				boolean hasGasp = false;
				tmpVerts.clear();
				for (int n = 0; n < vertexCount; n++) {
					if (isPolygon) {
						pShape.getVertex(n, tmpVec);
					} else {
						cShape.getVertex(n, tmpVec);
					}
					tmpVec.set(body.getWorldPoint(tmpVec));
					tmpVerts.add(tmpVec.cpy());
					tmpEnd.set(tmpVec).sub(start).limit2(0.0001f).add(tmpVec);
					if (fixture.testPoint(tmpEnd)) {
						if (minN == -1) minN = n;
						maxN = n;
						hasGasp = true;
						continue;
					}

					float currDist = tmpVec.dst2(start);
					if (currDist < minDst) {
						minDst = currDist;
						minDstN = n;
					}
				}

				ind.clear();
				if (!hasGasp) {
					tmpVec.set(tmpVerts.get(minDstN));
					for (int n = minDstN; n < vertexCount; n++) {
						ind.add(n);
					}
					for (int n = 0; n < minDstN; n++) {
						ind.add(n);
					}
					if (Intersector.pointLineSide(start, center, tmpVec) > 0) {
						ind.reverse();
						ind.insert(0, ind.pop());
					}
				} else if (minN == 0 && maxN == vertexCount - 1) {
					for (int n = maxN - 1; n > minN; n--) {
						ind.add(n);
					}
				} else {
					for (int n = minN - 1; n > -1; n--) {
						ind.add(n);
					}
					for (int n = vertexCount - 1; n > maxN ; n--) {
						ind.add(n);
					}
				}

				boolean contained = false;
				for (int n : ind.toArray()) {
					tmpVec.set(tmpVerts.get(n));
					if (contains(tmpVec.x, tmpVec.y)){
						contained = true;
						break;
					}
				}

				if (!contained)
					continue;

				for (int n : ind.toArray()) {
					tmpVec.set(tmpVerts.get(n));

					float dst = tmpVec.dst(start);
					l = data.getLimit(dst, pseudo3dHeight, distance);
					tmpEnd.set(tmpVec).sub(start).setLength(l).add(tmpVec);

					float f1 = 1f - dst / distance;
					float f2 = 1f - (dst + l) / distance;

					tmpColor.set(Color.BLACK);
					float startColBits = rayHandler.shadowColorInterpolation ?
							tmpColor.lerp(rayHandler.ambientLight, f1).toFloatBits() :
							oneColorBits;
					tmpColor.set(Color.WHITE);
					float endColBits = rayHandler.shadowColorInterpolation ?
							tmpColor.lerp(rayHandler.ambientLight, f2).toFloatBits() :
							colBits;

					segments[size++] = tmpVec.x;
					segments[size++] = tmpVec.y;
					segments[size++] = startColBits;
					segments[size++] = f1;

					segments[size++] = tmpEnd.x;
					segments[size++] = tmpEnd.y;
					segments[size++] = endColBits;
					segments[size++] = f2;
				}
			} else if (type == Shape.Type.Circle) {
				CircleShape shape = (CircleShape)fixtureShape;
				float r = shape.getRadius();
				if (!contains(tmpVec.set(center).add(r, r)) && !contains(tmpVec.set(center).add(-r, -r))
						&& !contains(tmpVec.set(center).add(r, -r)) && !contains(tmpVec.set(center).add(-r, r))) {
					continue;
				}

				float dst = tmpVec.set(center).dst(start);
				float a = (float) Math.acos(r/dst);
				l = data.getLimit(dst, pseudo3dHeight, distance);
				float f1 = 1f - dst / distance;
				float f2 = 1f - (dst + l) / distance;
				tmpColor.set(Color.BLACK);
				float startColBits = rayHandler.shadowColorInterpolation ?
						tmpColor.lerp(rayHandler.ambientLight, f1).toFloatBits() :
						oneColorBits;
				tmpColor.set(Color.WHITE);
				float endColBits = rayHandler.shadowColorInterpolation ?
						tmpColor.lerp(rayHandler.ambientLight, f2).toFloatBits() :
						colBits;

				tmpVec.set(start).sub(center).clamp(r, r).rotateRad(a);
				tmpStart.set(center).add(tmpVec);

				float angle = (MathUtils.PI2 - 2f * a) /
						RayHandler.CIRCLE_APPROX_POINTS;
				for (int k = 0; k < RayHandler.CIRCLE_APPROX_POINTS; k++) {
					tmpStart.set(center).add(tmpVec);
					segments[size++] = tmpStart.x;
					segments[size++] = tmpStart.y;
					segments[size++] = startColBits;
					segments[size++] = f1;

					tmpEnd.set(tmpStart).sub(start).setLength(l).add(tmpStart);
					segments[size++] = tmpEnd.x;
					segments[size++] = tmpEnd.y;
					segments[size++] = endColBits;
					segments[size++] = f2;

					tmpVec.rotateRad(angle);
				}
			} else if (type == Shape.Type.Edge) {
				EdgeShape shape = (EdgeShape)fixtureShape;

				shape.getVertex1(tmpVec);
				tmpVec.set(body.getWorldPoint(tmpVec));
				if (!contains(tmpVec)) {
					continue;
				}
				float dst = tmpVec.dst(start);
				l = data.getLimit(dst, pseudo3dHeight, distance);
				float f1 = 1f - dst / distance;
				float f2 = 1f - (dst + l) / distance;
				tmpColor.set(Color.BLACK);
				float startColBits = rayHandler.shadowColorInterpolation ?
						tmpColor.lerp(rayHandler.ambientLight, f1).toFloatBits() :
						oneColorBits;
				tmpColor.set(Color.WHITE);
				float endColBits = rayHandler.shadowColorInterpolation ?
						tmpColor.lerp(rayHandler.ambientLight, f2).toFloatBits() :
						colBits;

				segments[size++] = tmpVec.x;
				segments[size++] = tmpVec.y;
				segments[size++] = startColBits;
				segments[size++] = f1;

				tmpEnd.set(tmpVec).sub(start).setLength(l).add(tmpVec);
				segments[size++] = tmpEnd.x;
				segments[size++] = tmpEnd.y;
				segments[size++] = endColBits;
				segments[size++] = f2;

				shape.getVertex2(tmpVec);
				tmpVec.set(body.getWorldPoint(tmpVec));
				if (!contains(tmpVec)) {
					continue;
				}
				dst = tmpVec.dst(start);
				l = data.getLimit(dst, pseudo3dHeight, distance);
				f1 = 1f - dst / distance;
				f2 = 1f - (dst + l) / distance;
				tmpColor.set(Color.BLACK);
				startColBits = rayHandler.shadowColorInterpolation ?
						tmpColor.lerp(rayHandler.ambientLight, f1).toFloatBits() :
						oneColorBits;
				tmpColor.set(Color.WHITE);
				endColBits = rayHandler.shadowColorInterpolation ?
						tmpColor.lerp(rayHandler.ambientLight, f2).toFloatBits() :
						colBits;

				segments[size++] = tmpVec.x;
				segments[size++] = tmpVec.y;
				segments[size++] = startColBits;
				segments[size++] = f1;

				tmpEnd.set(tmpVec).sub(start).setLength(l).add(tmpVec);
				segments[size++] = tmpEnd.x;
				segments[size++] = tmpEnd.y;
				segments[size++] = endColBits;
				segments[size++] = f2;
			}

			Mesh mesh = null;
			if (meshInd >= dynamicShadowMeshes.size) {
				mesh = new Mesh(
						Mesh.VertexDataType.VertexArray, false, RayHandler.MAX_SHADOW_VERTICES, 0,
						new VertexAttribute(VertexAttributes.Usage.Position, 2, "vertex_positions"),
						new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "quad_colors"),
						new VertexAttribute(VertexAttributes.Usage.Generic, 1, "s"));
				dynamicShadowMeshes.add(mesh);
			} else {
				mesh = dynamicShadowMeshes.get(meshInd);
			}
			mesh.setVertices(segments, 0, size);
			meshInd++;
		}
		dynamicShadowMeshes.truncate(meshInd);
	}

	public float getBodyOffsetX() {
		return bodyOffsetX;
	}

	public float getBodyOffsetY() {
		return bodyOffsetY;
	}

	public float getBodyAngleOffset() {
		return bodyAngleOffset;
	}

	public void setBodyOffsetX(float bodyOffsetX) {
		this.bodyOffsetX = bodyOffsetX;
	}

	public void setBodyOffsetY(float bodyOffsetY) {
		this.bodyOffsetY = bodyOffsetY;
	}

	public void setBodyAngleOffset(float bodyAngleOffset) {
		this.bodyAngleOffset = bodyAngleOffset;
	}
}
