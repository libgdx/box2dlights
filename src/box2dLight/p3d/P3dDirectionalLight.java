package box2dLight.p3d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Mesh.VertexDataType;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.Shape.Type;

/**
 * Light which source is at infinite distance
 * 
 * <p>Extends {@link P3dLight}
 * 
 * @author rinold
 */
public class P3dDirectionalLight extends P3dLight {

	protected Vector2 start[];
	protected Vector2 end[];
	protected float sin;
	protected float cos;
	
	/** Dynamic shadows variables **/
	protected final Vector2 lstart = new Vector2();
	protected float xDisp;
	protected float yDisp;
	
	protected boolean flipDirection = false;

	/**
	 * Creates directional light which source is at infinite distance,
	 * direction and intensity is same everywhere
	 * 
	 * <p>-90 direction is straight from up
	 * 
	 * @param rayHandler
	 *            not {@code null} instance of RayHandler
	 * @param rays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 * @param color
	 *            color, set to {@code null} to use the default color
	 * @param directionDegree
	 *            direction in degrees
	 */
	public P3dDirectionalLight(P3dLightManager lightManager, int rays, Color color,
			float directionDegree) {
		
		super(lightManager, rays, color, Float.POSITIVE_INFINITY, directionDegree);

		setRayNum(rays);
		
		lightMesh = new Mesh(
				VertexDataType.VertexArray, staticLight, vertexNum * 8, 0,
				new VertexAttribute(Usage.Position, 2, "vertex_positions"),
				new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
				new VertexAttribute(Usage.Generic, 1, "s"));
		
		update();
	}

	@Override
	public void setDirection(float direction) {
		if (flipDirection) direction += 180f;
		this.direction = direction;
		sin = MathUtils.sinDeg(direction);
		cos = MathUtils.cosDeg(direction);
		if (staticLight) dirty = true;
	}
	
	@Override
	public void update() {
		float width = (lightHandler.x2 - lightHandler.x1);
		float height = (lightHandler.y2 - lightHandler.y1);
		float sizeOfScreen = width > height ? width : height;
		xDisp = -sizeOfScreen * cos;
		yDisp = -sizeOfScreen * sin;
		
		prepeareFixtureData();
		updateDynamicShadowMeshes();
		
		if (staticLight && !dirty) return;
		dirty = false;
		
		int size = 0;
		segments[size++] = lightHandler.x1;
		segments[size++] = lightHandler.y1;
		segments[size++] = colorF;
		segments[size++] = 0.5f;
		
		segments[size++] = lightHandler.x2;
		segments[size++] = lightHandler.y1;
		segments[size++] = colorF;
		segments[size++] = 0.5f;
		
		segments[size++] = lightHandler.x2;
		segments[size++] = lightHandler.y2;
		segments[size++] = colorF;
		segments[size++] = 0.5f;
		
		segments[size++] = lightHandler.x1;
		segments[size++] = lightHandler.y2;
		segments[size++] = colorF;
		segments[size++] = 0.5f;

		lightMesh.setVertices(segments, 0, size);
	}

	@Override
	public void render() {
		lightHandler.lightsRenderedLastFrame++;
		lightMesh.render(shader, GL20.GL_TRIANGLE_FAN);
	}
	
	@Override
	public void dynamicShadowRender() {
		if (height == -1f) return;
		
		for (int i = 0; i < activeShadows; i++) {
			Mesh m = dynamicShadowMeshes.get(i);
			m.render(shader, GL20.GL_TRIANGLE_STRIP);
		}
	}
	
	protected void prepeareFixtureData() {
		affectedFixtures.clear();
		lightHandler.getWorld().QueryAABB(
				dynamicShadowCallback,
				lightHandler.x1, lightHandler.y1,
				lightHandler.x2, lightHandler.y2);
	}
	
	protected void updateDynamicShadowMeshes() {
		activeShadows = 0;
		for (Fixture fixture : affectedFixtures) {
			P3dData data = (P3dData)fixture.getUserData();
			if (data == null || fixture.isSensor()) continue;
			
			Shape fixtureShape = fixture.getShape();
			Type type = fixtureShape.getType();
			Body body = fixture.getBody();
			center.set(body.getWorldCenter());
			lstart.set(center).add(xDisp, yDisp);
			
			int size = 0;
			float l = data.height /
					(float) Math.tan(height * MathUtils.degRad);
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
					
					tmpEnd.set(tmpVec).sub(lstart).limit2(0.0001f).add(tmpVec);
					if (fixture.testPoint(tmpEnd)) {
						if (minN == -1) minN = n;
						maxN = n;
						hasGasp = true;
						continue;
					}
					float currDist = tmpVec.dst2(lstart);
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
					if (Intersector.pointLineSide(lstart, center, tmpVec) > 0) {
						int z = ind.get(0);
						ind.removeIndex(0);
						ind.reverse();
						ind.insert(0, z);
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
					tmpEnd.set(tmpVec).sub(lstart).setLength(l).add(tmpVec);
					
					segments[size++] = tmpVec.x;
					segments[size++] = tmpVec.y;
					segments[size++] = colorF;
					segments[size++] = 1f;
					
					segments[size++] = tmpEnd.x;
					segments[size++] = tmpEnd.y;
					segments[size++] = colorF;
					segments[size++] = 1f;
				}
			} else if (type == Type.Circle) {
				CircleShape shape = (CircleShape)fixtureShape;
				
				float r = shape.getRadius();
				float dst = tmpVec.set(center).dst(lstart);
				float a = (float) Math.acos(r/dst);
				//l = data.getLimit(dst, height, distance);
				//float f1 = 1f - dst / distance;
				//float f2 = 1f - (dst + l) / distance;
				
				tmpVec.set(lstart).sub(center).clamp(r, r).rotateRad(a);
				tmpStart.set(center).add(tmpVec);
				
				float angle = (MathUtils.PI2 - 2f * a) /
						P3dLightManager.CIRCLE_APPROX_POINTS;
				for (int k = 0; k < P3dLightManager.CIRCLE_APPROX_POINTS; k++) {
					tmpStart.set(center).add(tmpVec);
					segments[size++] = tmpStart.x;
					segments[size++] = tmpStart.y;
					segments[size++] = colorF;
					segments[size++] = 1f;
					
					tmpEnd.set(tmpStart).sub(lstart).setLength(l).add(tmpStart);
					segments[size++] = tmpEnd.x;
					segments[size++] = tmpEnd.y;
					segments[size++] = colorF;
					segments[size++] = 1f;
					
					tmpVec.rotateRad(angle);
				}
			} else if (type == Type.Edge) {
				EdgeShape shape = (EdgeShape)fixtureShape;
				
				shape.getVertex1(tmpVec);
				tmpVec.set(body.getWorldPoint(tmpVec));
				
				segments[size++] = tmpVec.x;
				segments[size++] = tmpVec.y;
				segments[size++] = colorF;
				segments[size++] = 1f;
				
				tmpEnd.set(tmpVec).sub(lstart).setLength(l).add(tmpVec);
				segments[size++] = tmpEnd.x;
				segments[size++] = tmpEnd.y;
				segments[size++] = colorF;
				segments[size++] = 1f;
				
				shape.getVertex2(tmpVec);
				tmpVec.set(body.getWorldPoint(tmpVec));
				segments[size++] = tmpVec.x;
				segments[size++] = tmpVec.y;
				segments[size++] = colorF;
				segments[size++] = 1f;
				
				tmpEnd.set(tmpVec).sub(lstart).setLength(l).add(tmpVec);
				segments[size++] = tmpEnd.x;
				segments[size++] = tmpEnd.y;
				segments[size++] = colorF;
				segments[size++] = 1f;
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
		Gdx.app.log("P3dDirectionalLight >>>", "activeShadows="+activeShadows);
	}
	
	@Override
	public boolean contains (float x, float y) {
		boolean oddNodes = false;
		// Not implemented yet
		return oddNodes;
	}
	
	/** Sets the horizontal angle for directional light in degrees
	 * 
	 * <p> This could be used to simulate sun cycles **/
	@Override
	public void setHeight(float degrees) {
		flipDirection = false;
		if (degrees < 0f) height = 0f;
		else {
			degrees = degrees % 360;
			if (degrees > 180f) {
				height = -1f;
			}
			else if (degrees > 90f) {
				height = 180f - degrees;
				flipDirection = true;
			}
			else height = degrees;
		}
	}
	
	protected void setRayNum(int rays) {
		rayNum = rays;
		vertexNum = rays;
		segments = new float[vertexNum * 8];
		start = new Vector2[rayNum];
		end = new Vector2[rayNum];
		for (int i = 0; i < rayNum; i++) {
			start[i] = new Vector2();
			end[i] = new Vector2();
		}
	}

	/** Not applicable for this light type **/
	@Deprecated
	@Override
	public void attachToBody (Body body) {
	}
	
	/** Not applicable for this light type **/
	@Deprecated
	@Override
	public void setPosition (float x, float y) {
	}

	/** Not applicable for this light type
	 * <p>Always return {@code null}
	 **/
	@Deprecated
	@Override
	public Body getBody () {
		return null;
	}

	/** Not applicable for this light type
	 * <p>Always return {@code 0}
	 **/
	@Deprecated
	@Override
	public float getX () {
		return 0;
	}

	/** Not applicable for this light type
	 * <p>Always return {@code 0}
	 **/
	@Deprecated
	@Override
	public float getY () {
		return 0;
	}

	/** Not applicable for this light type **/
	@Deprecated
	@Override
	public void setPosition (Vector2 position) {
	}

	/** Not applicable for this light type **/
	@Deprecated
	@Override
	public void setDistance(float dist) {
	}
	
	/** Not applicable for this light type **/
	@Deprecated
	@Override
	public void setIgnoreAttachedBody(boolean flag) {
	}
	
	/** Not applicable for this light type
	 * <p>Always return {@code false}
	 **/
	@Deprecated
	@Override
	public boolean getIgnoreAttachedBody() {
		return false;
	}


}
