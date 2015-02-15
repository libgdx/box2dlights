package box2dLight.p3d;

import box2dLight.PositionalLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Mesh.VertexDataType;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.Shape.Type;

/**
 * Light shaped as a circle with given radius
 * 
 * <p>Extends {@link PositionalLight}
 * 
 * @author rinold
 */
public class P3dPointLight extends P3dPositionalLight {

	/**
	 * Creates light shaped as a circle with default radius (15f), color and
	 * position (0f, 0f)
	 * 
	 * @param rayHandler
	 *            not {@code null} instance of RayHandler
	 * @param rays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 */
	public P3dPointLight(P3dLightManager lightManager, int rays) {
		this(lightManager, rays, DefaultColor, 15f, 0f, 0f);
	}
	
	/**
	 * Creates light shaped as a circle with given radius
	 * 
	 * @param rayHandler
	 *            not {@code null} instance of RayHandler
	 * @param rays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 * @param color
	 *            color, set to {@code null} to use the default color
	 * @param distance
	 *            distance of light
	 * @param x
	 *            horizontal position in world coordinates
	 * @param y
	 *            vertical position in world coordinates
	 */
	public P3dPointLight(P3dLightManager lightManager, int rays, Color color,
			float distance, float x, float y) {
		super(lightManager, rays, color, distance, x, y, 0f);
	}
	
	@Override
	public void update () {
		updateBody();
		if (dirty) {
			setEndPoints();
		}
		
		if (cull()) return;
		if (staticLight && !dirty) return;
		
		dirty = false;
		updateMesh();
		prepeareFixtureData();
		updateDynamicShadowMeshes();
	}
	
	@Override
	public void dynamicShadowRender () {
		for (int i = 0; i < activeShadows; i++) {
			Mesh m = dynamicShadowMeshes.get(i);
			m.render(shader, GL20.GL_TRIANGLE_STRIP);
		}
	}
	
	protected void updateDynamicShadowMeshes() {
		activeShadows = 0;
		for (Fixture fixture : affectedFixtures) {
			P3dData data = (P3dData)fixture.getUserData();
			if (data == null || fixture.isSensor()) continue;
			
			int size = 0;
			float l = 0f;
			
			Shape fixtureShape = fixture.getShape();
			Type type = fixtureShape.getType();
			Body body = fixture.getBody();
			center.set(body.getWorldCenter());
			
			if (type == Type.Polygon || type == Type.Chain) {
				boolean isPolygon = (type == Type.Polygon);
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
				
				for (int n : ind.toArray()) {
					tmpVec.set(tmpVerts.get(n));
					
					float dst = tmpVec.dst(start);
					l = data.getLimit(dst, height, distance);
					tmpEnd.set(tmpVec).sub(start).setLength(l).add(tmpVec);
					float f1 = 1f - dst / distance;
					float f2 = 1f - (dst + l) / distance;
					
					segments[size++] = tmpVec.x;
					segments[size++] = tmpVec.y;
					segments[size++] = colorF;
					segments[size++] = f1;
					
					segments[size++] = tmpEnd.x;
					segments[size++] = tmpEnd.y;
					segments[size++] = colorF;
					segments[size++] = f2;
				}
			} else if (type == Type.Circle) {
				CircleShape shape = (CircleShape)fixtureShape;
				float r = shape.getRadius();
				float dst = tmpVec.set(center).dst(start);
				float a = (float) Math.acos(r/dst);
				l = data.getLimit(dst, height, distance);
				float f1 = 1f - dst / distance;
				float f2 = 1f - (dst + l) / distance;
				
				tmpVec.set(start).sub(center).clamp(r, r).rotateRad(a);
				tmpStart.set(center).add(tmpVec);
				
				float angle = (MathUtils.PI2 - 2f * a) /
						P3dLightManager.CIRCLE_APPROX_POINTS;
				for (int k = 0; k < P3dLightManager.CIRCLE_APPROX_POINTS; k++) {
					tmpStart.set(center).add(tmpVec);
					segments[size++] = tmpStart.x;
					segments[size++] = tmpStart.y;
					segments[size++] = colorF;
					segments[size++] = f1;
					
					tmpEnd.set(tmpStart).sub(start).setLength(l).add(tmpStart);
					segments[size++] = tmpEnd.x;
					segments[size++] = tmpEnd.y;
					segments[size++] = colorF;
					segments[size++] = f2;
					
					tmpVec.rotateRad(angle);
				}
			} else if (type == Type.Edge) {
				EdgeShape shape = (EdgeShape)fixtureShape;
				
				shape.getVertex1(tmpVec);
				tmpVec.set(body.getWorldPoint(tmpVec));
				float dst = tmpVec.dst(start);
				l = data.getLimit(dst, height, distance);
				float f1 = 1f - dst / distance;
				float f2 = 1f - (dst + l) / distance;
				
				segments[size++] = tmpVec.x;
				segments[size++] = tmpVec.y;
				segments[size++] = colorF;
				segments[size++] = f1;
				
				tmpEnd.set(tmpVec).sub(start).setLength(l).add(tmpVec);
				segments[size++] = tmpEnd.x;
				segments[size++] = tmpEnd.y;
				segments[size++] = colorF;
				segments[size++] = f2;
				
				shape.getVertex2(tmpVec);
				tmpVec.set(body.getWorldPoint(tmpVec));
				dst = tmpVec.dst(start);
				l = data.getLimit(dst, height, distance);
				f1 = 1f - dst / distance;
				f2 = 1f - (dst + l) / distance;
				
				segments[size++] = tmpVec.x;
				segments[size++] = tmpVec.y;
				segments[size++] = colorF;
				segments[size++] = f1;
				
				tmpEnd.set(tmpVec).sub(start).setLength(l).add(tmpVec);
				segments[size++] = tmpEnd.x;
				segments[size++] = tmpEnd.y;
				segments[size++] = colorF;
				segments[size++] = f2;
			}
			
			Mesh mesh = null;
			if (activeShadows >= dynamicShadowMeshes.size) {
				mesh = new Mesh(
						VertexDataType.VertexArray, false,
						P3dLightManager.MAX_SHADOW_VERTICES, 0,
						new VertexAttribute(Usage.Position, 2, "vertex_positions"),
						new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
						new VertexAttribute(Usage.Generic, 1, "s"));
				dynamicShadowMeshes.add(mesh);
			} else {
				mesh = dynamicShadowMeshes.get(activeShadows);
			}
			mesh.setVertices(segments, 0, size);
			activeShadows++;
		}
	}
	
	/**
	 * Sets light distance
	 * 
	 * <p>MIN value capped to 0.1f meter
	 * <p>Actual recalculations will be done only on {@link #update()} call
	 */
	@Override
	public void setDistance(float dist) {
		dist *= gammaCorrectionValue;
		this.distance = dist < 0.01f ? 0.01f : dist;
		dirty = true;
	}
	
	/** Updates light basing on it's distance and rayNum **/
	void setEndPoints() {
		float angleNum = 360f / (rayNum - 1);
		for (int i = 0; i < rayNum; i++) {
			final float angle = angleNum * i;
			sin[i] = MathUtils.sinDeg(angle);
			cos[i] = MathUtils.cosDeg(angle);
			endX[i] = distance * cos[i];
			endY[i] = distance * sin[i];
		}
	}
	
	/** Not applicable for this light type **/
	@Deprecated
	@Override
	public void setDirection(float directionDegree) {
	}

}
