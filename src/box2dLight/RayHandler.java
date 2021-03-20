package box2dLight;

import shaders.LightShader;

import com.badlogic.gdx.Gdx;
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
 * Handler that manages everything related to lights updating and rendering
 * <p>Implements {@link Disposable}
 * @author kalle_h
 */
public class RayHandler implements Disposable {

	/** Gamma correction value used if enabled
	 * TODO: remove final modifier and provide method to change
	 * this default value if needed to anyone? */
	static final float GAMMA_COR = 0.625f;

	static boolean gammaCorrection = false;
	static float gammaCorrectionParameter = 1f;

	/**
	 * TODO: This could be made adaptive to ratio of camera sizes * zoom vs the
	 * CircleShape radius - thus will provide smooth radial shadows while
	 * resizing and zooming in and out
	 */
	static int CIRCLE_APPROX_POINTS = 32;

	static float dynamicShadowColorReduction = 1;

	static int MAX_SHADOW_VERTICES = 64;

	static boolean isDiffuse = false;
	/**
	 * Blend function for lights rendering with both shadows and diffusion
	 * <p>Default: (GL20.GL_DST_COLOR, GL20.GL_ZERO)
	 */
	public final BlendFunc diffuseBlendFunc =
			new BlendFunc(GL20.GL_DST_COLOR, GL20.GL_ZERO);

	/**
	 * Blend function for lights rendering with shadows but without diffusion
	 * <p>Default: (GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
	 */
	public final BlendFunc shadowBlendFunc =
			new BlendFunc(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);

	/**
	 * Blend function for lights rendering without shadows and diffusion 
	 * <p>Default: (GL20.GL_SRC_ALPHA, GL20.GL_ONE)
	 */
	public final BlendFunc simpleBlendFunc =
			new BlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

	final Matrix4 combined = new Matrix4();
	final Color ambientLight = new Color();

	/**
	 * This Array contain all the lights.
	 * 
	 * <p>NOTE: DO NOT MODIFY THIS LIST
	 */
	final Array<Light> lightList = new Array<Light>(false, 16);
	
	/**
	 * This Array contain all the disabled lights.
	 * 
	 * <p>NOTE: DO NOT MODIFY THIS LIST
	 */
	final Array<Light> disabledLights = new Array<Light>(false, 16);

	LightMap lightMap;
	final ShaderProgram lightShader;
	ShaderProgram customLightShader = null;

	boolean culling = true;
	boolean shadows = true;
	boolean blur = true;

	/** Experimental mode */
	boolean pseudo3d = false;
	boolean shadowColorInterpolation = false;

	int blurNum = 1;
	
	boolean customViewport = false;
	int viewportX = 0;
	int viewportY = 0;
	int viewportWidth = Gdx.graphics.getWidth();
	int viewportHeight = Gdx.graphics.getHeight();
	
	/** How many lights passed culling and rendered to scene last time */
	int lightRenderedLastFrame = 0;

	/** camera matrix corners */
	float x1, x2, y1, y2;

	World world;
	
	/**
	 * Class constructor specifying the physics world from where collision
	 * geometry is taken.
	 * 
	 * <p>NOTE: FBO size is 1/4 * screen size and used by default.
	 *
	 * Default setting are:
	 * <ul>
	 *     <li>culling = true
	 *     <li>shadows = true
	 *     <li>diffuse = false
	 *     <li>blur = true
	 *     <li>blurNum = 1
	 *     <li>ambientLight = 0f
	 * </ul>
	 * 
	 * @see #RayHandler(World, int, int, RayHandlerOptions)
	 */
	public RayHandler(World world) {
		this(world, Gdx.graphics.getWidth() / 4, Gdx.graphics
				.getHeight() / 4, null);
	}

	public RayHandler(World world, RayHandlerOptions options) {
		this(world, Gdx.graphics.getWidth() / 4, Gdx.graphics
				.getHeight() / 4, options);
	}

	/**
	 * Class constructor specifying the physics world from where collision
	 * geometry is taken, and size of FBO used for intermediate rendering.
	 * 
	 * @see #RayHandler(World)
	 */
	public RayHandler(World world, int fboWidth, int fboHeight) {
		this(world, fboWidth, fboHeight, null);
	}

	public RayHandler(World world, int fboWidth, int fboHeight, RayHandlerOptions options) {
		this.world = world;

		if (options != null) {
			isDiffuse = options.isDiffuse;
			gammaCorrection = options.gammaCorrection;
			pseudo3d = options.pseudo3d;
			shadowColorInterpolation = options.shadowColorInterpolation;
		}

		resizeFBO(fboWidth, fboHeight);
		lightShader = LightShader.createLightShader();
	}

	/**
	 * Resize the FBO used for intermediate rendering.
	 */
	public void resizeFBO(int fboWidth, int fboHeight) {
		if (lightMap != null) {
			lightMap.dispose();
		}
		lightMap = new LightMap(this, fboWidth, fboHeight);
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
	 * @param combined
	 *            matrix that include projection and translation matrices
	 * 
	 * @deprecated use {@link #setCombinedMatrix(OrthographicCamera)} or
	 * {@link #setCombinedMatrix(Matrix4, float, float, float, float)} instead
	 */
	@Deprecated
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
	boolean intersect(float x, float y, float radius) {
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
		for (Light light : lightList) {
			light.update();
		}
	}

	/**
	 * Prepare all lights for rendering.
	 *
	 * <p>You should need to use this method only if you want to render lights
	 * on a frame buffer object. Use {@link #render()} otherwise.
	 *
	 * <p><b>NOTE!</b> Don't call this inside of any begin/end statements.
	 *
	 * @see #renderOnly()
	 * @see #render()
	 */
	public void prepareRender() {
		lightRenderedLastFrame = 0;

		Gdx.gl.glDepthMask(false);
		Gdx.gl.glEnable(GL20.GL_BLEND);

		boolean useLightMap = (shadows || blur);
		if (useLightMap) {
			lightMap.frameBuffer.begin();
			Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		}

		simpleBlendFunc.apply();

		ShaderProgram shader = customLightShader != null ? customLightShader : lightShader;
		shader.bind();
		{
			lightShader.setUniformMatrix("u_projTrans", combined);
			shader.setUniformMatrix("u_projTrans", combined);
			if (customLightShader != null) updateLightShader();

			for (Light light : lightList) {
				if (customLightShader != null) updateLightShaderPerLight(light);
				light.render();
			}
		}

		if (useLightMap) {
			if (customViewport) {
				lightMap.frameBuffer.end(
					viewportX,
					viewportY,
					viewportWidth,
					viewportHeight);
			} else {
				lightMap.frameBuffer.end();
			}
		}

		if (useLightMap && pseudo3d) {
			lightMap.shadowBuffer.begin();
			Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

			for (Light light : lightList) {
				light.dynamicShadowRender();
			}

			if (customViewport) {
				lightMap.shadowBuffer.end(
						viewportX,
						viewportY,
						viewportWidth,
						viewportHeight);
			} else {
				lightMap.shadowBuffer.end();
			}
		}

		boolean needed = lightRenderedLastFrame > 0;
		// this way lot less binding
		if (needed && blur)
			lightMap.gaussianBlur(lightMap.frameBuffer, blurNum);
		if (needed && blur && pseudo3d)
			lightMap.gaussianBlur(lightMap.shadowBuffer, blurNum);
	}

	/**
	 * Manual rendering method for all lights.
	 *
	 * <p><b>NOTE!</b> Remember to set combined matrix and update lights
	 * before using this method manually.
	 *
	 * <p>Don't call this inside of any begin/end statements.
	 * Call this method after you have rendered background but before UI.
	 * Box2d bodies can be rendered before or after depending how you want
	 * the x-ray lights to interact with them.
	 *
	 * @see #updateAndRender()
	 * @see #update()
	 * @see #setCombinedMatrix(Matrix4)
	 * @see #setCombinedMatrix(Matrix4, float, float, float, float)
	 */
	public void render() {
		prepareRender();
		lightMap.render();
	}

	/**
	 * Manual rendering method for all lights tha can be used inside of
	 * begin/end statements
	 *
	 * <p>Use this method if you want to render lights in a frame buffer
	 * object. You must call {@link #prepareRender()} before calling this
	 * method. Also, {@link #prepareRender()} must not be inside of any
	 * begin/end statements
	 *
	 * @see #prepareRender()
	 */
	public void renderOnly() {
		lightMap.render();
	}

	/**
	 * Called before light rendering start
	 *
	 * Override this if you are using custom light shader
	 */
	protected void updateLightShader () {

	}

	/**
	 * Called for custom light shader before each light is rendered
	 *
	 * Override this if you are using custom light shader
	 */
	protected void updateLightShaderPerLight (Light light) {

	}

	/**
	 * Checks whether the given point is inside of any light volume
	 * 
	 * @return true if point is inside of any light volume
	 */
	public boolean pointAtLight(float x, float y) {
		for (Light light : lightList) {
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
		for (Light light : lightList) {
			if (light.contains(x, y)) return false;
		}
		return true;
	}

	/**
	 * Disposes all this rayHandler lights and resources
	 */
	public void dispose() {
		removeAll();
		if (lightMap != null) lightMap.dispose();
		if (lightShader != null) lightShader.dispose();
	}

	/**
	 * Removes and disposes both all active and disabled lights
	 */
	public void removeAll() {
		for (Light light : lightList) {
			light.dispose();
		}
		lightList.clear();

		for (Light light : disabledLights) {
			light.dispose();
		}
		disabledLights.clear();
	}

	/**
	 * Set custom light shader, null to reset to default
	 *
	 * Changes will take effect next time #render() is called
	 */
	public void setLightShader (ShaderProgram customLightShader) {
		this.customLightShader = customLightShader;
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
		this.blurNum = blurNum;
	}

	/**
	 * Enables/disables shadows
	 */
	public void setShadows(boolean shadows) {
		this.shadows = shadows;
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

	/**
	 * Sets physics world to work with for this rayHandler
	 */
	public void setWorld(World world) {
		this.world = world;
	}

	/**
	 * @return if gamma correction is enabled or not
	 */
	public static boolean getGammaCorrection() {
		return gammaCorrection;
	}

	/**
	 * Enables/disables gamma correction.
	 * 
	 * <p><b>This need to be done before creating instance of rayHandler.</b>
	 * 
	 * <p>NOTE: To match the visuals with gamma uncorrected lights the light
	 * distance parameters is modified implicitly.
	 */
	public void applyGammaCorrection(boolean gammaCorrectionWanted) {
		gammaCorrection = gammaCorrectionWanted;
		gammaCorrectionParameter = gammaCorrection ? GAMMA_COR : 1f;
		lightMap.createShaders();
	}

	/**
	 * Enables/disables usage of diffuse algorithm.
	 * 
	 * <p>If set to true lights are blended using the diffuse shader. This is
	 * more realistic model than normally used as it preserve colors but might
	 * look bit darker and also it might improve performance slightly.
	 */
	public void setDiffuseLight(boolean useDiffuse) {
		isDiffuse = useDiffuse;
		lightMap.createShaders();
	}

	public static boolean isDiffuseLight() {
		return isDiffuse;
	}

	public static float getDynamicShadowColorReduction () {
		return dynamicShadowColorReduction;
	}

	/**
	 * Static setters are deprecated, use {@link RayHandlerOptions}
	 */
	@Deprecated
	public static void useDiffuseLight(boolean useDiffuse) {

	}

	/**
	 * Static setters are deprecated, use {@link RayHandlerOptions}
	 */
	@Deprecated
	public static void setGammaCorrection(boolean gammaCorrectionWanted) {

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

	/**
	 * /!\ Experimental mode with dynamic shadowing in pseudo-3d world
	 *
	 * @param flag enable pseudo 3d effect
	 */
	public void setPseudo3dLight(boolean flag) {
		setPseudo3dLight(flag, false);
	}

	/**
	 * /!\ Experimental mode with dynamic shadowing in pseudo-3d world
	 *
	 * @param flag enable pseudo 3d effect
	 * @param interpolateShadows interpolate shadow color
	 */
	public void setPseudo3dLight(boolean flag, boolean interpolateShadows) {
		pseudo3d = flag;
		shadowColorInterpolation = interpolateShadows;

		lightMap.createShaders();
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
