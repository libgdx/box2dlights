package box2dLight.base;

import shaders.LightShader;
import box2dLight.BlendFunc;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Base class for light handlers, used to reduce code duplication between
 * the common and pseudo3d box2dlight implementations
 * 
 * @author kalle_h
 * @author rinold
 */
public abstract class BaseLightHandler implements Disposable {
	
	/** Gamma correction value used if enabled **/
	protected static final float GAMMA_COR = 0.625f;
	protected static boolean gammaCorrection = false;
	
	protected static boolean diffuse = false;
	
	/**
	 * Blend function for lights rendering with both shadows and diffusion
	 * 
	 * <p>Default: (GL20.GL_DST_COLOR, GL20.GL_ZERO) 
	 */
	public final BlendFunc diffuseBlendFunc =
			new BlendFunc(GL20.GL_DST_COLOR, GL20.GL_ZERO);

	/**
	 * Blend function for lights rendering with shadows but without diffusion
	 * 
	 * <p>Default: (GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
	 */
	public final BlendFunc shadowBlendFunc =
			new BlendFunc(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);

	/**
	 * Blend function for lights rendering without shadows and diffusion
	 *  
	 * <p>Default: (GL20.GL_SRC_ALPHA, GL20.GL_ONE)
	 */
	public final BlendFunc simpleBlendFunc =
			new BlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

	/**
	 * This Array contain all the lights.
	 * 
	 * <p>NOTE: DO NOT MODIFY THIS LIST
	 */
	public final Array<BaseLight> lightList = new Array<BaseLight>(false, 16);
	
	/**
	 * This Array contain all the disabled lights.
	 * 
	 * <p>NOTE: DO NOT MODIFY THIS LIST
	 */
	public final Array<BaseLight> disabledLights = new Array<BaseLight>(false, 16);

	protected final Matrix4 combined = new Matrix4();
	protected final Color ambientLight = new Color();
	protected final Color tmpColor = new Color();
	protected final ShaderProgram lightShader;
	
	protected BaseLightMap lightMap;
	
	protected boolean culling = true;
	protected boolean shadows = true;
	
	protected boolean blur = true;
	protected int lightBlurPasses = 1;
	
	protected boolean customViewport = false;
	protected int viewportX = 0;
	protected int viewportY = 0;
	protected int viewportWidth = 0;
	protected int viewportHeight = 0;
	
	/** How many lights passed culling and rendered to scene last time **/
	public int lightsRenderedLastFrame = 0;

	/** Cached camera matrix corners **/
	public float x1, x2, y1, y2;

	protected World world;
	
	/**
	 * Enables/disables usage of diffuse algorithm
	 * 
	 * <p>If set to true lights are blended using the diffuse shader. This is
	 * more realistic model than normally used as it preserve colors but might
	 * look bit darker and also it might improve performance slightly.
	 */
	public static void setDiffuseLight(boolean flag) {
		diffuse = flag;
	}
	
	/**
	 * @return if the usage of diffuse algorithm is enabled 
	 * 
	 * <p>If set to true lights are blended using the diffuse shader. This is
	 * more realistic model than normally used as it preserve colors but might
	 * look bit darker and also it might improve performance slightly.
	 */
	public static boolean isDiffuseLight() {
		return diffuse;
	}

	/**
	 * Enables/disables gamma correction.
	 * 
	 * <p><b>This need to be done before creating instance of rayHandler.</b>
	 * 
	 * <p>NOTE: To match the visuals with gamma uncorrected lights the light
	 * distance parameters is modified implicitly.
	 */
	public static void setGammaCorrection(boolean flag) {
		gammaCorrection = flag;
		BaseLight.gammaCorrectionValue = gammaCorrection ? GAMMA_COR : 1f;
	}
	
	/** @return if gamma correction is enabled or not **/
	public static boolean isGammaCorrection() {
		return gammaCorrection;
	}
	
	/**
	 * Class constructor specifying the physics world from where collision
	 * geometry is taken, and size of FBO used for intermediate rendering.
	 * 
	 * @see #RayHandlerBase(World)
	 */
	public BaseLightHandler(World world) {
		this.world = world;
		this.lightShader = LightShader.createLightShader(gammaCorrection);
		BaseLight.shader = lightShader;
	}
	
	/**
	 * Sets combined matrix basing on camera position, rotation and zoom
	 * 
	 * <p> Same as calling:
	 * {@code setCombinedMatrix(
	 *                camera.combined,
	 *                camera.position.x,
	 *                camera.position.y,
	 *                camera.viewportWidth * camera.zoom,
	 *                camera.viewportHeight * camera.zoom );}
	 * 
	 * @see #setCombinedMatrix(Matrix4)
	 * @see #setCombinedMatrix(Matrix4, float, float, float, float)
	 */
	public void setCombinedMatrix(OrthographicCamera camera) {
		this.setCombinedMatrix(
				camera.combined,
				camera.position.x,
				camera.position.y,
				camera.viewportWidth * camera.zoom,
				camera.viewportHeight * camera.zoom);
	}
	
	/**
	 * Sets combined camera matrix.
	 * 
	 * <p>Matrix must be set to work in box2d coordinates, it will be copied
	 * and used for culling and rendering. Remember to update it if camera
	 * changes. This will work with rotated cameras.
	 * 
	 * <p>NOTE: Matrix4 is assumed to be orthogonal for culling
	 * and directional lights.
	 * 
	 * <p>If any problems detected use the
	 * {@link #setCombinedMatrix(Matrix4, float, float, float, float)} instead
	 * 
	 * @param combined
	 *            matrix that include projection and translation matrices
	 * 
	 * @see #setCombinedMatrix(OrthographicCamera)
	 * @see #setCombinedMatrix(Matrix4, float, float, float, float)
	 */
	public void setCombinedMatrix(Matrix4 combined) {
		System.arraycopy(combined.val, 0, this.combined.val, 0, 16);

		// updateCameraCorners
		float invWidth = combined.val[Matrix4.M00];

		final float halfViewPortWidth = 1f / invWidth;
		final float x = -halfViewPortWidth * combined.val[Matrix4.M03];
		x1 = x - halfViewPortWidth;
		x2 = x + halfViewPortWidth;

		float invHeight = combined.val[Matrix4.M11];

		final float halfViewPortHeight = 1f / invHeight;
		final float y = -halfViewPortHeight * combined.val[Matrix4.M13];
		y1 = y - halfViewPortHeight;
		y2 = y + halfViewPortHeight;

	}

	/**
	 * Sets combined camera matrix.
	 * 
	 * <p>Matrix must be set to work in box2d coordinates, it will be copied
	 * and used for culling and rendering. Remember to update it if camera
	 * changes. This will work with rotated cameras.
	 * 
	 * @param combined
	 *            matrix that include projection and translation matrices
	 * @param x
	 *            combined matrix position
	 * @param y
	 *            combined matrix position
	 * @param viewPortWidth
	 *            NOTE!! use actual size, remember to multiple with zoom value
	 *            if pulled from OrthoCamera
	 * @param viewPortHeight
	 *            NOTE!! use actual size, remember to multiple with zoom value
	 *            if pulled from OrthoCamera
	 * 
	 * @see #setCombinedMatrix(OrthographicCamera)
	 * @see #setCombinedMatrix(Matrix4)
	 */
	public void setCombinedMatrix(Matrix4 combined, float x, float y,
			float viewPortWidth, float viewPortHeight) {
		
		System.arraycopy(combined.val, 0, this.combined.val, 0, 16);
		// updateCameraCorners
		final float halfViewPortWidth = viewPortWidth * 0.5f;
		x1 = x - halfViewPortWidth;
		x2 = x + halfViewPortWidth;

		final float halfViewPortHeight = viewPortHeight * 0.5f;
		y1 = y - halfViewPortHeight;
		y2 = y + halfViewPortHeight;
	}

	/**
	 * Utility method to check if light is on the screen
	 * @param x      - light center x-coord 
	 * @param y      - light center y-coord 
	 * @param radius - maximal light distance
	 * 
	 * @return true if camera screen intersects or contains provided
	 * light, represented by circle/box area
	 */
	public boolean intersect(float x, float y, float radius) {
		return (x1 < (x + radius) && x2 > (x - radius) &&
				y1 < (y + radius) && y2 > (y - radius));
	}

	/**
	 * Updates and renders all active lights.
	 * 
	 * <p><b>NOTE!</b> Remember to set combined matrix before this method.
	 * 
	 * <p>Don't call this inside of any begin/end statements.
	 * Call this method after you have rendered background but before UI.
	 * Box2d bodies can be rendered before or after depending how you want
	 * the x-ray lights to interact with them.
	 * 
	 * @see #update()
	 * @see #render()
	 */
	public void updateAndRender() {
		update();
		render();
	}

	/**
	 * Manual update method for all active lights.
	 * 
	 * <p>Use this if you have less physics steps than rendering steps.
	 * 
	 * @see #updateAndRender()
	 * @see #render()
	 */
	public void update() {
		for (BaseLight light : lightList) {
			light.update();
		}
	}

	public abstract void render();

	/**
	 * Checks whether the given point is inside of any light volume
	 * 
	 * @return true if point is inside of any light volume
	 */
	public boolean pointAtLight(float x, float y) {
		for (BaseLight light : lightList) {
			if (light.contains(x, y)) return true;
		}
		return false;
	}

	/**
	 * Checks whether the given point is outside of all light volumes
	 * 
	 * @return true if point is NOT inside of any light volume
	 */
	public boolean pointAtShadow(float x, float y) {
		for (BaseLight light : lightList) {
			if (light.contains(x, y)) return false;
		}
		return true;
	}

	/**
	 * Disposes all this rayHandler lights and resources
	 */
	public void dispose() {
		removeAll();
		if (lightShader != null) lightShader.dispose();
	}
	
	void add(BaseLight light) {
		if (light.active) {
			lightList.add(light);
		} else {
			disabledLights.add(light);
		}
	}
	
	void remove(BaseLight light) {
		if (light.active) {
			lightList.removeValue(light, false);
		} else {
			disabledLights.removeValue(light, false);
		}
	}

	/**
	 * Removes and disposes both all active and disabled lights
	 */
	public void removeAll() {
		for (BaseLight light : lightList) {
			light.dispose();
		}
		lightList.clear();

		for (BaseLight light : disabledLights) {
			light.dispose();
		}
		disabledLights.clear();
	}	
 
	/**
	 * Enables/disables culling.
	 * 
	 * <p>This save CPU and GPU time when the world is bigger than the screen.
	 * 
	 * <p>Default = true
	 */
	public void setCulling(boolean culling) {
		this.culling = culling;
	}
	
	public boolean isCulling() {
		return culling;
	}

	/**
	 * Enables/disables Gaussian blur.
	 * 
	 * <p>This make lights much more softer and realistic look but cost some
	 * precious shader time. With default FBO size on android cost around 1ms.
	 * 
	 * <p>Default = true
	 * 
	 * @see #setBlurNum(int)
	 */
	public void setBlur(boolean blur) {
		this.blur = blur;
	}
	
	public boolean isBlur() {
		return blur;
	}
	
	/**
	 * Sets number of Gaussian blur passes.
	 * 
	 * <p>Blurring can be pretty heavy weight operation, 1-3 should be safe.
	 * Setting this to 0 is the same as disabling it.
	 * 
	 * <p>Default = 1
	 * 
	 * @see #setBlur(boolean)
	 */
	public void setBlurNum(int blurNum) {
		this.lightBlurPasses = blurNum;
	}
	
	public int getBlurNum() {
		return lightBlurPasses;
	}

	/**
	 * Enables/disables shadows
	 */
	public void setShadows(boolean shadows) {
		this.shadows = shadows;
	}
	
	public boolean isShadows() {
		return shadows;
	}

	/**
	 * Sets ambient light brightness. Specifies shadows brightness.
	 * <p>Default = 0
	 * 
	 * @param ambientLight
	 *            shadows brightness value, clamped to [0f; 1f]
	 * 
	 * @see #setAmbientLight(Color)
	 * @see #setAmbientLight(float, float, float, float)
	 */
	public void setAmbientLight(float ambientLight) {
		this.ambientLight.a = MathUtils.clamp(ambientLight, 0f, 1f);
	}

	/**
	 * Sets ambient light color.
	 * Specifies how shadows colored and their brightness.
	 * 
	 * <p>Default = Color(0, 0, 0, 0)
	 * 
	 * @param r
	 *            shadows color red component
	 * @param g
	 *            shadows color green component
	 * @param b
	 *            shadows color blue component
	 * @param a
	 *            shadows brightness component
	 * 
	 * @see #setAmbientLight(float)
	 * @see #setAmbientLight(Color)
	 */
	public void setAmbientLight(float r, float g, float b, float a) {
		this.ambientLight.set(r, g, b, a);
	}

	/**
	 * Sets ambient light color.
	 * Specifies how shadows colored and their brightness.
	 * 
	 * <p>Default = Color(0, 0, 0, 0)
	 * 
	 * @param ambientLightColor
	 * 	          color whose RGB components specify the shadows coloring and
	 *            alpha specify shadows brightness 
	 * 
	 * @see #setAmbientLight(float)
	 * @see #setAmbientLight(float, float, float, float)
	 */
	public void setAmbientLight(Color ambientLightColor) {
		this.ambientLight.set(ambientLightColor);
	}
	
	public Color getAmbientLight() {
		return tmpColor.set(ambientLight);
	}

	/**
	 * Sets physics world to work with for this rayHandler
	 */
	public void setWorld(World world) {
		this.world = world;
	}
	
	public World getWorld() {
		return world;
	}

	/**
	 * Sets rendering to custom viewport with specified position and size
	 * <p>Note: you will be responsible for update of viewport via this method
	 * in case of any changes (on resize)
	 */
	public void useCustomViewport(int x, int y, int width, int height) {
		customViewport = true;
		viewportX = x;
		viewportY = y;
		viewportWidth = width;
		viewportHeight = height;
	}
	
	/**
	 * Sets rendering to default viewport
	 * 
	 * <p>0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()
	 */
	public void useDefaultViewport() {
		customViewport = false;
	}
	
	public int getLigtsRenderedLastFrame() {
		return lightsRenderedLastFrame;
	}
	
	/**
	 * Enables/disables lightMap automatic rendering.
	 * 
	 * <p>If set to false user needs to use the {@link #getLightMapTexture()}
	 * and render that or use it as a light map when rendering. Example shader
	 * for spriteBatch is given. This is faster way to do if there is not that
	 * much overdrawing or if just couple object need light/shadows.
	 * 
	 * <p>Default = true
	 */
	public void setLightMapRendering(boolean isAutomatic) {
		lightMap.lightMapDrawingDisabled = !isAutomatic;
	}

	/**
	 * Expert functionality
	 * 
	 * @return Texture that contain lightmap texture that can be used as light
	 *         texture in your shaders
	 */
	public Texture getLightMapTexture() {
		return lightMap.frameBuffer.getColorBufferTexture();
	}

	/**
	 * Expert functionality, no support given
	 * 
	 * @return FrameBuffer that contains lightMap
	 */
	public FrameBuffer getLightMapBuffer() {
		return lightMap.frameBuffer;
	}

}
