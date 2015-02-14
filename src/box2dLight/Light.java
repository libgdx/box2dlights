package box2dLight;

import box2dLight.base.BaseLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.RayCastCallback;

/**
 * Light is data container for all the light parameters. When created lights
 * are automatically added to rayHandler and could be removed by calling
 * {@link #remove()} and added manually by calling {@link #add(RayHandler)}.
 * 
 * <p>Implements {@link BaseLight}
 * 
 * @author kalle_h
 */
public abstract class Light extends BaseLight {

	protected boolean soft = true;
	protected boolean xray = false;
	protected boolean staticLight = false;
	protected boolean culled = false;
	protected boolean dirty = true;
	protected boolean ignoreBody = false;

	protected int rayNum;
	protected int vertexNum;
	
	protected float softShadowLength = 2.5f;
	
	protected Mesh softShadowMesh;

	protected float[] mx;
	protected float[] my;
	protected float[] f;
	protected int m_index = 0;

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
	public Light(RayHandler rayHandler, int rays, Color color,
			float distance, float directionDegree) {
		rayHandler.lightList.add(this);
		lightHandler = rayHandler;
		setRayNum(rays);
		setColor(color);
		setDistance(distance);
		setDirection(directionDegree);
	}

	/**
	 * Disposes all light resources
	 */
	public void dispose() {
		super.dispose();
		softShadowMesh.dispose();
	}

	/**
	 * Enables/disables softness on tips of this light beams
	 */
	public void setSoft(boolean soft) {
		this.soft = soft;
		if (staticLight) dirty = true;
	}

	/**
	 * @return if tips of this light beams are soft
	 */
	public boolean isSoft() {
		return soft;
	}
	
	/**
	 * Sets softness value for beams tips
	 * 
	 * <p>Default: {@code 2.5f}
	 */
	public void setSoftnessLength(float softShadowLength) {
		this.softShadowLength = softShadowLength;
		if (staticLight) dirty = true;
	}

	/**
	 * @return softness value for beams tips
	 *         <p>Default: {@code 2.5f}
	 */
	public float getSoftShadowLength() {
		return softShadowLength;
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
	
	/**
	 * Sets if the attached body fixtures should be ignored during raycasting
	 * 
	 * @param flag - if {@code true} all the fixtures of attached body
	 *               will be ignored and will not create any shadows for this
	 *               light. By default is set to {@code false}. 
	 */
	public void setIgnoreAttachedBody(boolean flag) {
		ignoreBody = flag;
	}
	
	/**
	 * @return if the attached body fixtures will be ignored during raycasting
	 */
	public boolean getIgnoreAttachedBody() {
		return ignoreBody;
	}
	
	/**
	 * Internal method for mesh update depending on ray number
	 */
	void setRayNum(int rays) {
		if (rays < MIN_RAYS)
			rays = MIN_RAYS;

		rayNum = rays;
		vertexNum = rays + 1;

		segments = new float[vertexNum * 8];
		mx = new float[vertexNum];
		my = new float[vertexNum];
		f = new float[vertexNum];
	}

	/** Global lights filter **/
	static private Filter filterA = null;

	final RayCastCallback ray = new RayCastCallback() {
		@Override
		final public float reportRayFixture(Fixture fixture, Vector2 point,
				Vector2 normal, float fraction) {
			
			if ((filterA != null) && !contactFilter(fixture))
				return -1;

			if (ignoreBody && fixture.getBody() == getBody())
				return -1;

			// if (fixture.isSensor())
			// return -1;
			
			mx[m_index] = point.x;
			my[m_index] = point.y;
			f[m_index] = fraction;
			return fraction;
		}
	};

	boolean contactFilter(Fixture fixtureB) {
		Filter filterB = fixtureB.getFilterData();

		if (filterA.groupIndex != 0 &&
			filterA.groupIndex == filterB.groupIndex)
			return filterA.groupIndex > 0;

		return  (filterA.maskBits & filterB.categoryBits) != 0 &&
				(filterA.categoryBits & filterB.maskBits) != 0;
	}

	/**
	 * Sets given contact filter for ALL LIGHTS
	 */
	static public void setContactFilter(Filter filter) {
		filterA = filter;
	}

	/**
	 * Creates new contact filter for ALL LIGHTS with give parameters
	 * 
	 * @param categoryBits - see {@link Filter#categoryBits}
	 * @param groupIndex   - see {@link Filter#groupIndex}
	 * @param maskBits     - see {@link Filter#maskBits}
	 */
	static public void setContactFilter(short categoryBits, short groupIndex,
			short maskBits) {
		filterA = new Filter();
		filterA.categoryBits = categoryBits;
		filterA.groupIndex = groupIndex;
		filterA.maskBits = maskBits;
	}

}
