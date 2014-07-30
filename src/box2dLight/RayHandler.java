package box2dLight;

import shaders.LightShader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * TODO
 * @author kalle_h
 *
 */
public class RayHandler implements Disposable {

	/** TODO */
	static final float GAMMA_COR = 0.625f;

	static boolean gammaCorrection = false;
	static float gammaCorrectionParameter = 1f;

	static public boolean isDiffuse = false;

	boolean culling = true;
	boolean shadows = true;
	boolean blur = true;

	int blurNum = 1;
	Color ambientLight = new Color();

	World world;
	ShaderProgram lightShader;
	
	int viewportX = 0;
	int viewportY = 0;
	int viewportWidth = Gdx.graphics.getWidth();
	int viewportHeight = Gdx.graphics.getHeight();

	/**
	 * @param combined
	 *            matrix that include projection and translation matrices
	 */
	final private Matrix4 combined = new Matrix4();

	/** camera matrix corners */
	float x1, x2, y1, y2;

	private LightMap lightMap;

	/**
	 * This Array contain all the lights.
	 * 
	 * NOTE: DO NOT MODIFY THIS LIST
	 */
	final public Array<Light> lightList = new Array<Light>(false, 16);
	/**
	 * This Array contain all the disabled lights.
	 * 
	 * NOTE: DO NOT MODIFY THIS LIST
	 */
	final public Array<Light> disabledLights = new Array<Light>(false, 16);

	/** TODO how many lights passed culling and rendered to scene */
	public int lightRenderedLastFrame = 0;

	/** TODO
	 * Construct handler that manages everything related to updating and
	 * rendering the lights MINIMUM parameters needed are world where collision
	 * geometry is taken.
	 * 
	 * Default setting: culling = true, shadows = true, blur =
	 * true(GL2.0),blurNum = 1, ambientLight = 0.0f;
	 * 
	 * NOTE1: rays number per lights are capped to 1023. For different size use
	 * other constructor
	 * 
	 * NOTE2: On GL 2.0 FBO size is 1/4 * screen size and used by default. For
	 * different sizes use other constructor
	 * 
	 * @param world
	 * @param camera
	 */
	public RayHandler(World world) {
		this(world, Gdx.graphics.getWidth() / 4, Gdx.graphics
				.getHeight() / 4);
	}

	/** TODO
	 * Construct handler that manages everything related to updating and
	 * rendering the lights MINIMUM parameters needed are world where collision
	 * geometry is taken.
	 * 
	 * Default setting: culling = true, shadows = true, blur =
	 * true(GL2.0),blurNum = 1, ambientLight = 0.0f;
	 * 
	 * 
	 * @param world
	 * @param camera
	 * @param maxRayCount
	 * @param fboWidth
	 * @param fboHeigth
	 */
	public RayHandler(World world, int fboWidth, int fboHeigth) {
		this.world = world;

		lightMap = new LightMap(this, fboWidth, fboHeigth);
		lightShader = LightShader.createLightShader();
	}

	/** TODO
	 * Set combined camera matrix. Matrix will be copied and used for rendering
	 * lights, culling. Matrix must be set to work in box2d coordinates. Matrix
	 * has to be updated every frame(if camera is changed)
	 * 
	 * 
	 * NOTE: Matrix4 is assumed to be orthogonal for culling and directional
	 * lights.
	 * 
	 * If any problems detected Use: [public void setCombinedMatrix(Matrix4
	 * combined, float x, float y, float viewPortWidth, float viewPortHeight)]
	 * Instead
	 * 
	 * 
	 * @param combined
	 *            matrix that include projection and translation matrices
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

	/** TODO
	 * EXPERT USE Set combined camera matrix. Matrix will be copied and used for
	 * rendering lights, culling. Matrix must be set to work in box2d
	 * coordinates. Matrix has to be updated every frame(if camera is changed)
	 * 
	 * NOTE: this work with rotated cameras.
	 * 
	 * @param combined
	 *            matrix that include projection and translation matrices
	 * 
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
	 * TODO
	 * @param x
	 * @param y
	 * @param side
	 * @return
	 */
	boolean intersect(float x, float y, float side) {
		return (x1 < (x + side) && x2 > (x - side) &&
				y1 < (y + side) && y2 > (y - side));
	}

	/** TODO
	 * Remember setCombinedMatrix(Matrix4 combined) before drawing.
	 * 
	 * Don't call this inside of any begin/end statements. Call this method
	 * after you have rendered background but before UI. Box2d bodies can be
	 * rendered before or after depending how you want x-ray light interact with
	 * bodies
	 */
	public final void updateAndRender() {
		update();
		render();
	}

	/** TODO
	 * Manual update method for all lights. Use this if you have less physic
	 * steps than rendering steps.
	 */
	public final void update() {
		for (Light light : lightList) {
			light.update();
		}
	}

	/** TODO
	 * Manual rendering method for all lights.
	 * 
	 * NOTE! Remember to call updateRays if you use this method. * Remember
	 * setCombinedMatrix(Matrix4 combined) before drawing.
	 * 
	 * 
	 * Don't call this inside of any begin/end statements. Call this method
	 * after you have rendered background but before UI. Box2d bodies can be
	 * rendered before or after depending how you want x-ray light interact with
	 * bodies
	 */
	public void render() {
		/** FIXME Seems some lights not calculate themselves and some are not culled **/
		lightRenderedLastFrame = 0;

		Gdx.gl.glDepthMask(false);
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

		boolean useLightMap = (shadows || blur); 
		if (useLightMap) {
			lightMap.frameBuffer.begin();
			Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		}

		lightShader.begin();
		{
			lightShader.setUniformMatrix("u_projTrans", combined);
			for (Light light : lightList) {
				light.render();
			}
		}
		lightShader.end();

		if (useLightMap) {
			lightMap.frameBuffer.end(
				viewportX, viewportY,
				viewportWidth, viewportHeight);
			lightMap.render();
		}
	}

	/** TODO
	 * Checks whether the given point is inside of any light volume.
	 * 
	 * @param x
	 * @param y
	 * @return true if point intersect any light volume
	 */
	public boolean pointAtLight(float x, float y) {
		for (Light light : lightList) {
			if (light.contains(x, y)) return true;
		}
		return false;
	}

	/** TODO
	 * Checks whether the given point outside of all light volumes.
	 * 
	 * @param x
	 * @param y
	 * @return true if point intersect any light volume
	 */
	public boolean pointAtShadow(float x, float y) {
		for (Light light : lightList) {
			if (light.contains(x, y)) return false;
		}
		return true;
	}

	/**
	 * TODO
	 */
	public void dispose() {
		removeAll();
		if (lightMap != null) lightMap.dispose();
		if (lightShader != null) lightShader.dispose();
	}

	/**
	 * Removes and disposes all active and disabled lights
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
 
	/** TODO
	 * Disables/enables culling.
	 * This save cpu and gpu time when world is bigger than screen.
	 * 
	 * <p>Default = true
	 * 
	 * @param culling
	 *            the culling to set
	 */
	public final void setCulling(boolean culling) {
		this.culling = culling;
	}

	/** TODO
	 * Disables/enables gaussian blur.
	 * 
	 * <p>This make lights much more softer and realistic look but cost some
	 * precious shader time. With default FBO size on android cost around 1ms.
	 * 
	 * <p>Default = true
	 * 
	 * @param blur
	 *            the blur to set
	 */
	public final void setBlur(boolean blur) {
		this.blur = blur;
	}

	/** TODO
	 * Set number of gaussian blur passes. Blurring can be pretty heavy weight
	 * operation, 1-3 should be safe. Setting this to 0 is same as
	 * setBlur(false)
	 * 
	 * default = 1
	 * 
	 * @param blurNum
	 *            the blurNum to set
	 */
	public final void setBlurNum(int blurNum) {
		this.blurNum = blurNum;
	}

	/** TODO
	 * Disables/enables shadows. NOTE: If gl1.1 android you need to change
	 * render target to contain alpha channel* default = true
	 * 
	 * @param shadows
	 *            the shadows to set
	 */
	public final void setShadows(boolean shadows) {
		this.shadows = shadows;
	}

	/** TODO
	 * Ambient light is how dark are the shadows. clamped to 0-1
	 * 
	 * default = 0;
	 * 
	 * @param ambientLight
	 *            the ambientLight to set
	 */
	public final void setAmbientLight(float ambientLight) {
		this.ambientLight.a = MathUtils.clamp(ambientLight, 0f, 1f);
	}

	/** TODO
	 * Ambient light color is how dark and what colored the shadows are. clamped
	 * to 0-1 NOTE: color is changed only in gles2.0 default = 0;
	 * 
	 * @param ambientLight
	 *            the ambientLight to set
	 */
	public final void setAmbientLight(float r, float g, float b, float a) {
		this.ambientLight.set(r, g, b, a);   
	}

	/** TODO
	 * Ambient light color is how dark and what colored the shadows are. clamped
	 * to 0-1 NOTE: color is changed only in gles2.0 default = 0,0,0,0;
	 * 
	 * @param ambientLight
	 *            the ambientLight to set
	 */
	public final void setAmbientLight(Color ambientLightColor) {
		this.ambientLight.set(ambientLightColor);
	}

	/** TODO
	 * @param world
	 *            the world to set
	 */
	public final void setWorld(World world) {
		this.world = world;
	}

	/**
	 * @return if gamma correction is enabled or not
	 */
	public static boolean getGammaCorrection() {
		return gammaCorrection;
	}

	/** TODO
	 * Sets gammaCorrection. This need to be done before creating instance of
	 * rayHandler.
	 * <p>NOTE: To match the visuals with gamma uncorrected lights the light
	 * distance parameters is modified implicitly.
	 * 
	 * @param gammaCorrectionWanted
	 */
	public static void setGammaCorrection(boolean gammaCorrectionWanted) {
		gammaCorrection = gammaCorrectionWanted;
		gammaCorrectionParameter = gammaCorrection ? GAMMA_COR : 1f;
	}

	/** TODO
	 * If this is set to true and shadow are on lights are blended with diffuse
	 * algoritm. this preserve colors but might look bit darker. This is more
	 * realistic model than normally used This might improve perfromance
	 * slightly
	 * 
	 * @param useDiffuse
	 */
	public static void useDiffuseLight(boolean useDiffuse) {
		isDiffuse = useDiffuse;
	}

	/**
	 * Sets rendering to custom viewport with specified position and size
	 */
	public void useCustomViewport(int x, int y, int width, int height) {
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
		viewportX = 0;
		viewportY = 0;
		viewportWidth = Gdx.graphics.getWidth();
		viewportHeight = Gdx.graphics.getHeight();
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
	 * 
	 * @param isAutomatic
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
