package box2dLight.p3d;

import box2dLight.RayHandler;
import box2dLight.base.BaseLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.QueryCallback;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntArray;

/**
 * Light is data container for all the light parameters. When created lights
 * are automatically added to rayHandler and could be removed by calling
 * {@link #remove()} and added manually by calling {@link #add(RayHandler)}.
 * 
 * <p>Implements {@link Disposable}
 * 
 * @author rinold
 */
public abstract class P3dLight extends BaseLight {

	protected int rayNum;
	protected int vertexNum;
	
	protected float height = 0f;
	
	protected int activeShadows;
	protected final Array<Mesh> dynamicShadowMeshes = new Array<Mesh>();
	protected final Array<Fixture> affectedFixtures = new Array<Fixture>();
	protected final Array<Vector2> tmpVerts = new Array<Vector2>();
	
	protected final IntArray ind = new IntArray();
	
	protected final Vector2 tmpStart = new Vector2();
	protected final Vector2 tmpEnd = new Vector2();
	protected final Vector2 tmpVec = new Vector2();
	protected final Vector2 center = new Vector2(); 
	
	/** 
	 * Creates new active light and automatically adds it to the specified
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
	 * @param directionDegree
	 *            direction in degrees (if applicable) 
	 */
	public P3dLight(P3dLightManager lightManager, int rays, Color color,
			float distance, float directionDegree) {
		lightManager.lightList.add(this);
		this.lightHandler = lightManager;
		setColor(color);
		setDistance(distance);
		setDirection(directionDegree);
	}

	/**
	 * Disposes all light resources
	 */
	public void dispose() {
		super.dispose();
		
		for (Mesh mesh : dynamicShadowMeshes) {
			mesh.dispose();
		}
		dynamicShadowMeshes.clear();
	}

	/**
	 * Enables/disables this light update and rendering
	 */
	public void setActive(boolean active) {
		if (active == this.active)
			return;

		this.active = active;
		if (lightHandler == null)
			return;
		
		if (active) {
			lightHandler.lightList.add(this);
			lightHandler.disabledLights.removeValue(this, true);
		} else {
			lightHandler.disabledLights.add(this);
			lightHandler.lightList.removeValue(this, true);
		}
	}

	/**
	 * @return if this light is active
	 */
	public boolean isActive() {
		return active;
	}

	/**
	 * Enables/disables x-ray beams for this light
	 * 
	 * <p>Enabling this will allow beams go through obstacles that reduce CPU
	 * burden of light about 70%.
	 * 
	 * <p>Use the combination of x-ray and non x-ray lights wisely
	 */
	public void setXray(boolean xray) {
		this.xray = xray;
		if (staticLight) dirty = true;
	}

	/**
	 * @return if this light beams go through obstacles
	 */
	public boolean isXray() {
		return xray;
	}

	/**
	 * Enables/disables this light static behavior
	 * 
	 * <p>Static light do not get any automatic updates but setting any
	 * parameters will update it. Static lights are useful for lights that you
	 * want to collide with static geometry but ignore all the dynamic objects
	 * 
	 * <p>Reduce CPU burden of light about 90%
	 */
	public void setStaticLight(boolean staticLight) {
		this.staticLight = staticLight;
		if (staticLight) dirty = true;
	}

	/**
	 * @return if this light is static
	 *         <p>Static light do not get any automatic updates but setting
	 *         any parameters will update it. Static lights are useful for
	 *         lights that you want to collide with static geometry but ignore
	 *         all the dynamic objects.
	 */
	public boolean isStaticLight() {
		return staticLight;
	}

	/**
	 * @return current color of this light
	 */
	public Color getColor() {
		return color;
	}

	/**
	 * Checks if given point is inside of this light area
	 * 
	 * @param x - horizontal position of point in world coordinates
	 * @param y - vertical position of point in world coordinates
	 */
	public boolean contains(float x, float y) {
		return false;
	}

	public void setHeight(float height) {
		this.height = height;
	}

	protected boolean onDynamicCallback(Fixture fixture) {
		return true;
	}
	
	final QueryCallback dynamicShadowCallback = new QueryCallback() {

		@Override
		public boolean reportFixture(Fixture fixture) {
			if (!onDynamicCallback(fixture)) return true;

			if (fixture.getUserData() instanceof P3dData) {
				affectedFixtures.add(fixture);
			}

			return true;
		}
		
	};

}
