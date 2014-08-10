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
 * Light is data container for all the light parameters. Lights are
 * automatically added to rayHandler and could be removed by calling
 * {@link Light#remove()} method. 
 * 
 * <p>Implements {@link Disposable}
 * @author kalle_h
 */
public abstract class Light implements Disposable {

	static final Color DefaultColor = new Color(0.75f, 0.75f, 0.5f, 0.75f);
	static final int MIN_RAYS = 3;
	
	protected final RayHandler rayHandler;
	protected final Color color = new Color();
	
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
	}

	/**
	 * Disposes all light resources 
	 */
	public void dispose() {
		lightMesh.dispose();
		softShadowMesh.dispose();
	}

	/** TODO: Stopped here :)
	 * attach positional light to automatically follow body. Position is fixed
	 * to given offset
	 * 
	 * NOTE: does absolute nothing if directional light
	 */
	public abstract void attachToBody(Body body, float offsetX, float offSetY);

	/**
	 * @return attached body or null if not set.
	 * 
	 *         NOTE: directional light allways return null
	 */
	public abstract Body getBody();

	/**
	 * set light starting position
	 * 
	 * NOTE: does absolute nothing if directional light
	 */
	public abstract void setPosition(float x, float y);

	/**
	 * set light starting position
	 * 
	 * NOTE: does absolute nothing if directional light
	 */
	public abstract void setPosition(Vector2 position);

	final Vector2 tmpPosition = new Vector2();

	/**
	 * starting position of light in world coordinates. directional light return
	 * zero vector.
	 * 
	 * NOTE: changing this vector does nothing
	 * 
	 * @return posX
	 */
	public Vector2 getPosition() {
		return tmpPosition;
	}

	/**
	 * horizontal starting position of light in world coordinates. directional
	 * light return 0
	 */
	/**
	 * @return posX
	 */
	public abstract float getX();

	/**
	 * vertical starting position of light in world coordinates. directional
	 * light return 0
	 */
	/**
	 * @return posY
	 */
	public abstract float getY();

	void staticUpdate() {
		boolean tmp = rayHandler.culling;
		staticLight = !staticLight;
		rayHandler.culling = false;
		update();
		rayHandler.culling = tmp;
		staticLight = !staticLight;
	}

	public final boolean isActive() {
		return active;
	}

	/**
	 * disable/enables this light updates and rendering.
	 * 
	 * @param active
	 */
	public final void setActive(boolean active) {
		if (active == this.active)
			return;

		if (active) {
			rayHandler.lightList.add(this);
			rayHandler.disabledLights.removeValue(this, true);
		} else {
			rayHandler.disabledLights.add(this);
			rayHandler.lightList.removeValue(this, true);

		}

		this.active = active;

	}

	/**
	 * do light beams go through obstacles
	 * 
	 * @return
	 */
	public final boolean isXray() {
		return xray;
	}

	/**
	 * disable/enables xray beams. enabling this will allow beams go through
	 * obstacles this reduce cpu burden of light about 70%. Use combination of
	 * xray and non xray lights wisely
	 * 
	 * @param xray
	 */
	public final void setXray(boolean xray) {
		this.xray = xray;
		if (staticLight)
			staticUpdate();
	}

	/**
	 * return is this light static. Static light do not get any automatic
	 * updates but setting any parameters will update it. Static lights are
	 * usefull for lights that you want to collide with static geometry but
	 * ignore all the dynamic objects.
	 * 
	 * @return
	 */
	public final boolean isStaticLight() {
		return staticLight;
	}

	/**
	 * disables/enables staticness for light. Static light do not get any
	 * automatic updates but setting any parameters will update it. Static
	 * lights are usefull for lights that you want to collide with static
	 * geometry but ignore all the dynamic objects. Reduce cpu burden of light
	 * about 90%.
	 * 
	 * @param staticLight
	 */
	public final void setStaticLight(boolean staticLight) {
		this.staticLight = staticLight;
		if (staticLight)
			staticUpdate();
	}

	/**
	 * is tips of light beams soft
	 * 
	 * @return
	 */
	public final boolean isSoft() {
		return soft;
	}

	/**
	 * disable/enables softness on tips of lights beams.
	 * 
	 * @param soft
	 */
	public final void setSoft(boolean soft) {
		this.soft = soft;
		if (staticLight)
			staticUpdate();
	}

	/**
	 * return how much is softness used in tip of the beams. default 2.5
	 * 
	 * @return
	 */
	public final float getSoftShadowLength() {
		return softShadowLength;
	}

	/**
	 * set how much is softness used in tip of the beams. default 2.5
	 * 
	 * @param softShadowLength
	 */
	public final void setSoftnessLength(float softShadowLength) {
		this.softShadowLength = softShadowLength;
		if (staticLight)
			staticUpdate();
	}

	private final void setRayNum(int rays) {

		if (rays < MIN_RAYS)
			rays = MIN_RAYS;

		rayNum = rays;
		vertexNum = rays + 1;

		segments = new float[vertexNum * 8];
		mx = new float[vertexNum];
		my = new float[vertexNum];
		f = new float[vertexNum];

	}

	static final float zero = Color.toFloatBits(0f, 0f, 0f, 0f);

	/**
	 * Color getColor
	 * 
	 * @return current lights color
	 */
	public Color getColor() {
		return color;
	}

	/**
	 * float getDistance()
	 * 
	 * @return light rays distance.
	 */
	public float getDistance() {
		float dist = distance / RayHandler.gammaCorrectionParameter;
		return dist;
	}

	/** method for checking is given point inside of this light */
	public boolean contains(float x, float y) {
		return false;
	}

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

	final boolean contactFilter(Fixture fixtureB) {
		Filter filterB = fixtureB.getFilterData();

		if (filterA.groupIndex == filterB.groupIndex && filterA.groupIndex != 0)
			return filterA.groupIndex > 0;

		return (filterA.maskBits & filterB.categoryBits) != 0
				&& (filterA.categoryBits & filterB.maskBits) != 0;

	}

	/** light filter **/
	static private Filter filterA = null;

	/**
	 * set given contact filter for ALL LIGHTS
	 * 
	 * @param filter
	 */
	static public void setContactFilter(Filter filter) {
		filterA = filter;
	}

	/**
	 * create new contact filter for ALL LIGHTS with give parameters
	 * 
	 * @param categoryBits
	 * @param groupIndex
	 * @param maskBits
	 */
	static public void setContactFilter(short categoryBits, short groupIndex,
			short maskBits) {
		filterA = new Filter();
		filterA.categoryBits = categoryBits;
		filterA.groupIndex = groupIndex;
		filterA.maskBits = maskBits;
	}

}
