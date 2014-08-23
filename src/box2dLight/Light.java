package box2dLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.RayCastCallback;
import com.badlogic.gdx.utils.Disposable;

/**
 * Light is data container for all the light parameters. When created lights
 * are automatically added to rayHandler and could be removed by calling
 * {@link #remove()} and added manually by calling {@link #add(RayHandler)}.
 * 
 * <p>Implements {@link Disposable}
 * @author kalle_h
 */
public abstract class Light implements Disposable {

	static final Color DefaultColor = new Color(0.75f, 0.75f, 0.5f, 0.75f);
	static final float zeroColorBits = Color.toFloatBits(0f, 0f, 0f, 0f);
	static final int MIN_RAYS = 3;
	
	protected final Color color = new Color();
	protected final Vector2 tmpPosition = new Vector2();
	
	protected RayHandler rayHandler;
	
	protected boolean active = true;
	protected boolean soft = true;
	protected boolean xray = false;
	protected boolean staticLight = false;
	protected boolean culled = false;

	protected int rayNum;
	protected int vertexNum;
	
	protected float distance;
	protected float direction;
	protected float colorF;
	protected float softShadowLength = 2.5f;
	
	protected Mesh lightMesh;
	protected Mesh softShadowMesh;

	protected float segments[];
	protected float[] mx;
	protected float[] my;
	protected float[] f;
	protected int m_index = 0;

	/** Creates new active light and automatically adds it to the specified
	 * {@link RayHandler} instance.
	 * 
	 * @param rayHandler
	 *            not null instance of RayHandler
	 * @param rays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 * @param color
	 *            light color
	 * @param directionDegree
	 *            direction in degrees (if applicable) 
	 * @param distance
	 *            light distance (if applicable)
	 */
	public Light(RayHandler rayHandler, int rays, Color color,
			float directionDegree, float distance) {

		rayHandler.lightList.add(this);
		this.rayHandler = rayHandler;
		setRayNum(rays);
		setDirection(directionDegree);
		setDistance(distance);
		setColor(color);
	}


	abstract void update();

	abstract void render();
	
	/**
	 * Sets light distance if applicable
	 * 
	 * <p>NOTE: MIN value should be capped to 0.1f meter
	 */
	public abstract void setDistance(float dist);

	/**
	 * Sets light direction if applicable
	 */
	public abstract void setDirection(float directionDegree);
	
	/**
	 * Sets light color
	 * 
	 * <p>NOTE: you can also use colorless light with shadows (EG 0,0,0,1)
	 * 
	 * @param newColor
	 *            RGB set the color and Alpha set intensity
	 * 
	 * @see #setColor(float, float, float, float)
	 */
	public void setColor(Color newColor) {
		if (newColor != null) {
			color.set(newColor);
		} else {
			color.set(DefaultColor);
		}
		colorF = color.toFloatBits();
		if (staticLight)
			staticUpdate();
	}

	/**
	 * Sets light color
	 * 
	 * <p>NOTE: you can also use colorless light with shadows (EG 0,0,0,1)
	 * 
	 * @param r
	 *            lights color red component
	 * @param g
	 *            lights color green component
	 * @param b
	 *            lights color blue component
	 * @param a
	 *            lights shadow intensity
	 * 
	 * @see #setColor(Color)
	 */
	public void setColor(float r, float g, float b, float a) {
		color.set(r, g, b, a);
		colorF = color.toFloatBits();
		if (staticLight)
			staticUpdate();
	}
	
	/**
	 * Adds light to specified RayHandler
	 */
	public void add(RayHandler rayHandler) {
		this.rayHandler = rayHandler;
		if (active) {
			rayHandler.lightList.add(this);
		} else {
			rayHandler.disabledLights.add(this);
		}
	}

	/**
	 * Removes light from specified RayHandler
	 */
	public void remove() {
		if (active) {
			rayHandler.lightList.removeValue(this, false);
		} else {
			rayHandler.disabledLights.removeValue(this, false);
		}
		rayHandler = null;
	}

	/**
	 * Disposes all light resources
	 */
	public void dispose() {
		lightMesh.dispose();
		softShadowMesh.dispose();
	}

	/**
	 * Attaches light to specified body
	 * 
	 * <p>NOTE: not applicable for {@link DirectionalLight}
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
	public abstract void attachToBody(Body body, float offsetX, float offsetY);

	/**
	 * @return attached body or <code>null</code> if not set
	 * 
	 *         <p>NOTE: {@link DirectionalLight} always return
	 *         <code>null</code>
	 * 
	 * @see #attachToBody(Body, float, float)
	 */
	public abstract Body getBody();

	/**
	 * Sets light starting position
	 * 
	 * <p>NOTE: not applicable for {@link DirectionalLight}
	 * 
	 * @see #setPosition(Vector2)
	 */
	public abstract void setPosition(float x, float y);

	/**
	 * Sets light starting position
	 * 
	 * <p>NOTE: not applicable for {@link DirectionalLight}
	 * 
	 * @see #setPosition(float, float)
	 */
	public abstract void setPosition(Vector2 position);

	/**
	 * @return starting position of light in world coordinates
	 *         or zero vector for {@link DirectionalLight}
	 * 
	 *         <p>NOTE: changing this vector does nothing
	 */
	public Vector2 getPosition() {
		return tmpPosition;
	}

	/**
	 * @return horizontal starting position of light in world coordinates
	 *         or 0 for {@link DirectionalLight}
	 */
	public abstract float getX();

	/**
	 * @return vertical starting position of light in world coordinates
	 *         or 0 for {@link DirectionalLight}
	 */
	public abstract float getY();

	/**
	 * Updates static light
	 * 
	 * <p><b>NOTE!!: Currently is called after each change, should be removed
	 * and 'dirty' flag used instead to provide one update operation for all
	 * the changes done to the light for better performance</b>
	 */
	@Deprecated
	void staticUpdate() {
		boolean tmp = rayHandler.culling;
		staticLight = !staticLight;
		rayHandler.culling = false;
		update();
		rayHandler.culling = tmp;
		staticLight = !staticLight;
	}

	/** @return if this light is active **/
	public boolean isActive() {
		return active;
	}

	/**
	 * Enables/disables this light update and rendering
	 */
	public void setActive(boolean active) {
		if (active == this.active)
			return;

		this.active = active;
		if (rayHandler == null)
			return;
		
		if (active) {
			rayHandler.lightList.add(this);
			rayHandler.disabledLights.removeValue(this, true);
		} else {
			rayHandler.disabledLights.add(this);
			rayHandler.lightList.removeValue(this, true);
		}
	}

	/**
	 * @return if this light beams go through obstacles
	 */
	public boolean isXray() {
		return xray;
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
		if (staticLight)
			staticUpdate();
	}

	// TODO: Fix this JavaDoc when staticUpdate() will change
	/**
	 * @return if this light is static
	 *         <p>Static light do not get any automatic updates but setting
	 *         any parameters will update it. Static lights are useful for
	 *         lights that you want to collide with static geometry but ignore
	 *         all the dynamic objects.
	 */
	public final boolean isStaticLight() {
		return staticLight;
	}

	// TODO: Fix this JavaDoc when staticUpdate() will change
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
		if (staticLight)
			staticUpdate();
	}

	/**
	 * @return if tips of this light beams are soft
	 */
	public boolean isSoft() {
		return soft;
	}

	/**
	 * Enables/disables softness on tips of this light beams
	 */
	public final void setSoft(boolean soft) {
		this.soft = soft;
		if (staticLight)
			staticUpdate();
	}

	/**
	 * @return softness value for beams tips
	 * 
	 *         <p>Default: <code>2.5f</code>
	 */
	public float getSoftShadowLength() {
		return softShadowLength;
	}

	/**
	 * Sets softness value for beams tips
	 * 
	 * <p>Default: <code>2.5f</code>
	 */
	public void setSoftnessLength(float softShadowLength) {
		this.softShadowLength = softShadowLength;
		if (staticLight)
			staticUpdate();
	}

	
	protected void setRayNum(int rays) {

		if (rays < MIN_RAYS)
			rays = MIN_RAYS;

		rayNum = rays;
		vertexNum = rays + 1;

		segments = new float[vertexNum * 8];
		mx = new float[vertexNum];
		my = new float[vertexNum];
		f = new float[vertexNum];

	}

	/**
	 * @return current color of this light
	 */
	public Color getColor() {
		return color;
	}

	/**
	 * @return rays distance of this light (without gamma correction)
	 */
	public float getDistance() {
		return distance / RayHandler.gammaCorrectionParameter;
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


	/** light filter **/
	static private Filter filterA = null;

	final RayCastCallback ray = new RayCastCallback() {
		@Override
		final public float reportRayFixture(Fixture fixture, Vector2 point,
				Vector2 normal, float fraction) {

			if ((filterA != null) && !contactFilter(fixture))
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
