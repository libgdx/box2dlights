package box2dLight.p3d;

import box2dLight.base.BaseLight;
import box2dLight.base.BaseLightHandler;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.physics.box2d.World;

/**
 * Handler that manages everything related to lights updating and rendering
 * 
 * @author rinold
 */
public class P3dLightManager extends BaseLightHandler {

	protected final P3dLightMap lightMap;

	/** TODO: This could be made adaptive to ratio of camera sizes * zoom
	 * vs the CircleShape radius - thus will provide smooth radial shadows
	 * while resizing and zooming in and out */
	protected static int CIRCLE_APPROX_POINTS = 32;
	
	protected static int MAX_SHADOW_VERTICES = 64;
	
	protected static int colorReduction = 3;
	
	protected int shadowBlurPasses = 1;
	
	/**
	 * Class constructor specifying the physics world from where collision
	 * geometry is taken.
	 * 
	 * <p>NOTE: FBO size is 1/4 * screen size and used by default.
	 * 
	 * <ul>Default setting are:
	 *     <li>culling = true
	 *     <li>shadows = true
	 *     <li>diffuse = false
	 *     <li>blur = true
	 *     <li>blurNum = 1
	 *     <li>ambientLight = 0f
	 * </ul>
	 * 
	 * @see #RayHandler(World, int, int)
	 */
	public P3dLightManager(World world) {
		this(world, Gdx.graphics.getWidth() / 4, Gdx.graphics
				.getHeight() / 4);
	}

	/**
	 * Class constructor specifying the physics world from where collision
	 * geometry is taken, and size of FBO used for intermediate rendering.
	 * 
	 * @see #RayHandler(World)
	 */
	public P3dLightManager(World world, int fboWidth, int fboHeigth) {
		super(world);
		lightMap = new P3dLightMap(this, fboWidth, fboHeigth);
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
		lightsRenderedLastFrame = 0;

		Gdx.gl.glDepthMask(false);
		Gdx.gl.glEnable(GL20.GL_BLEND);

		lightMap.frameBuffer.begin();
		Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		simpleBlendFunc.apply();
		lightShader.begin();
		{
			lightShader.setUniformMatrix("u_projTrans", combined);
			for (BaseLight light : lightList) {
				light.render();
			}
		}
		lightShader.end();
		
		if (customViewport) {
			lightMap.frameBuffer.end(
				viewportX,
				viewportY,
				viewportWidth,
				viewportHeight);
		} else {
			lightMap.frameBuffer.end();
		}
		
		lightMap.shadowBuffer.begin();
		Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		
		lightShader.begin();
		{
			for (BaseLight light : lightList) {
				light.dynamicShadowRender();
			}
		}
		lightShader.end();
		
		if (customViewport) {
			lightMap.shadowBuffer.end(
				viewportX,
				viewportY,
				viewportWidth,
				viewportHeight);
		} else {
			lightMap.shadowBuffer.end();
		}
		
		lightMap.render();
	}
	
	public void setBlurNum(int num) {
		lightBlurPasses = shadowBlurPasses = num;
	}
	
}
