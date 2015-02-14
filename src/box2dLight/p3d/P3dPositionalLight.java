
package box2dLight.p3d;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Mesh.VertexDataType;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;

/**
 * Abstract base class for all positional lights
 * 
 * <p>Extends {@link Light}
 * 
 * @author rinold
 */
public abstract class P3dPositionalLight extends P3dLight {
	
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
	public P3dPositionalLight(P3dLightManager lightManager, int rays, Color color, float distance, float x, float y, float directionDegree) {
		super(lightManager, rays, color, distance, directionDegree);
		start.x = x;
		start.y = y;
		
		setRayNum(rays);

		lightMesh = new Mesh(
				VertexDataType.VertexArray,
				false, vertexNum, 0,
				new VertexAttribute(Usage.Position, 2, "vertex_positions"),
				new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
				new VertexAttribute(Usage.Generic, 1, "s"));
		
		setMesh();
	}
	
	@Override
	public void update() {
		updateBody();
		
		if (cull()) return;
		if (staticLight && !dirty) return;
		
		dirty = false;
		
		updateMesh();
	}
	
	@Override
	public void render() {
		if (lightHandler.isCulling() && culled) return;

		lightHandler.lightsRenderedLastFrame++;
		lightMesh.render(
				shader, GL20.GL_TRIANGLE_FAN, 0, vertexNum);
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
	public void attachToBody(Body body, float offsetX, float offSetY, float degrees) {
		this.body = body;
		bodyOffsetX = offsetX;
		bodyOffsetY = offSetY;
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

	@Override
	public boolean contains(float x, float y) {
		// fast fail
		final float x_d = start.x - x;
		final float y_d = start.y - y;
		final float dst2 = x_d * x_d + y_d * y_d;
		if (distance * distance <= dst2) return false;

		// TODO: actual check
		return true;
	}
	
	protected void setRayNum(int rays) {
		rayNum = rays;
		vertexNum = rays + 1;
		segments = new float[vertexNum * 8];
		sin = new float[rays];
		cos = new float[rays];
		endX = new float[rays];
		endY = new float[rays];
	}
	
	protected boolean cull() {
		culled = lightHandler.isCulling() && !lightHandler.intersect(
					start.x, start.y, distance);
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
		setMesh();
	}
	
	protected void prepeareFixtureData() {
		affectedFixtures.clear();
		lightHandler.getWorld().QueryAABB(
				dynamicShadowCallback,
				start.x - distance, start.y - distance,
				start.x + distance, start.y + distance);
	}

	protected void setMesh() {
		// ray starting point
		int size = 0;
		float colorF = color.cpy().mul(1f / P3dLightManager.colorReduction).toFloatBits();

		segments[size++] = start.x;
		segments[size++] = start.y;
		segments[size++] = colorF;
		segments[size++] = 1f;
		// rays ending points.
		for (int i = 0; i < rayNum; i++) {
			segments[size++] = start.x + endX[i];
			segments[size++] = start.y + endY[i];
			segments[size++] = colorF;
			segments[size++] = 0f;
		}
		lightMesh.setVertices(segments, 0, size);
	}
	
	@Override
	protected boolean onDynamicCallback(Fixture fixture) {
		return fixture.getBody() != body;
	}
	
}
