package box2dLight.base;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Disposable;

/**
 * Base class for Light sources, used to reduce code duplication between
 * the common and pseudo3d box2dlight implementations 
 * 
 * @author kalle_h
 * @author rinold
 */
public abstract class BaseLight implements Disposable {

	protected static final Color DefaultColor = new Color(0.75f, 0.75f, 0.5f, 0.75f);
	protected static final float zeroColorBits = Color.toFloatBits(0f, 0f, 0f, 0f);
	protected static final int MIN_RAYS = 3;
	
	protected static float gammaCorrectionValue = 1f;
	protected static ShaderProgram shader;

	protected final Color color = new Color();
	protected final Color tmpColor = new Color();
	protected final Vector2 tmpPosition = new Vector2();

	protected BaseLightHandler lightHandler;

	protected boolean active = true;
	protected boolean xray = false;
	protected boolean culled = false;
	protected boolean ignoreBody = false;
	
	protected boolean staticLight = false;
	protected boolean dirty = true;

	protected float distance;
	protected float direction;
	protected float colorF;

	protected Mesh lightMesh;

	protected float segments[];

	public abstract void update();
	public abstract void render();
	public void dynamicShadowRender() {};
	
	public abstract boolean contains(float x, float y);


	/**
	 * Sets light distance
	 * 
	 * <p>NOTE: MIN value should be capped to 0.1f meter
	 */
	public abstract void setDistance(float dist);
	
	/**
	 * @return rays distance of this light (without gamma correction)
	 */
	public float getDistance() {
		return distance / gammaCorrectionValue;
	}

	/**
	 * Sets light direction
	 */
	public abstract void setDirection(float directionDegree);

	/**
	 * Attaches light to specified body
	 * 
	 * @param body
	 *            that will be automatically followed, note that the body
	 *            rotation angle is taken into account for the light offset
	 *            and direction calculations
	 */
	public abstract void attachToBody(Body body);

	/**
	 * @return attached body or {@code null}
	 * 
	 * @see #attachToBody(Body, float, float)
	 */
	public abstract Body getBody();

	/**
	 * Sets light starting position
	 * 
	 * @see #setPosition(Vector2)
	 */
	public abstract void setPosition(float x, float y);

	/**
	 * Sets light starting position
	 * 
	 * @see #setPosition(float, float)
	 */
	public abstract void setPosition(Vector2 position);

	/**
	 * @return starting position of light in world coordinates
	 *         <p>NOTE: changing this vector does nothing
	 */
	public Vector2 getPosition() {
		return tmpPosition;
	}

	/**
	 * @return horizontal starting position of light in world coordinates
	 */
	public abstract float getX();

	/**
	 * @return vertical starting position of light in world coordinates
	 */
	public abstract float getY();

	/**
	 * Adds light to specified RayHandler
	 */
	public void add(BaseLightHandler lightHandler) {
		this.lightHandler = lightHandler;
		lightHandler.add(this);
	}

	/**
	 * Removes light from specified RayHandler
	 */
	public void remove() {
		lightHandler.remove(this);
		lightHandler = null;
	}

	/**
	 * Disposes all light resources
	 */
	public void dispose() {
		lightMesh.dispose();
	}

	/**
	 * Sets light color
	 * 
	 * <p>NOTE: you can also use colorless light with shadows, e.g. (0,0,0,1)
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
		if (staticLight) dirty = true;
	}

	/**
	 * Sets light color
	 * 
	 * <p>NOTE: you can also use colorless light with shadows, e.g. (0,0,0,1)
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
		if (staticLight) dirty = true;
	}

	/**
	 * @return current color of this light
	 */
	public Color getColor() {
		return tmpColor.set(color);
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

}
