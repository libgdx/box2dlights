package box2dLight;

import box2dLight.base.BaseLight;
import box2dLight.base.BaseLightHandler;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Disposable;

/**
 * Handler that manages everything related to lights updating and rendering
 * 
 * <p>Implements {@link Disposable}
 * 
 * @author kalle_h
 */
public class RayHandler extends BaseLightHandler {
	
	protected static boolean diffuse = false;
	
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
	public RayHandler(World world) {
		this(world, Gdx.graphics.getWidth() / 4, Gdx.graphics
				.getHeight() / 4);
	}

	/**
	 * Class constructor specifying the physics world from where collision
	 * geometry is taken, and size of FBO used for intermediate rendering.
	 * 
	 * @see #RayHandler(World)
	 */
	public RayHandler(World world, int fboWidth, int fboHeigth) {
		super(world);
		this.lightMap = new LightMap(this, fboWidth, fboHeigth);
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
		simpleBlendFunc.apply();

		boolean useLightMap = (shadows || blur); 
		if (useLightMap) {
			lightMap.frameBuffer.begin();
			Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		}

		lightShader.begin();
		{
			lightShader.setUniformMatrix("u_projTrans", combined);
			for (BaseLight light : lightList) {
				light.render();
			}
		}
		lightShader.end();

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
			lightMap.render();
		}
	}

}
