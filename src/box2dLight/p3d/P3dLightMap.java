package box2dLight.p3d;

import shaders.DynamicShadowShader;
import box2dLight.base.BaseLightMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * Light map for pseudo-3d rendering
 * 
 * @author rinold
 */
class P3dLightMap extends BaseLightMap {
	
	P3dLightManager lightManager;

	FrameBuffer shadowBuffer;
	ShaderProgram shadowShader;

	public void render() {

		boolean needed = lightManager.getLigtsRenderedLastFrame() > 0;
		if (needed && lightManager.isBlur()) {
			gaussianBlur(frameBuffer, lightManager.getBlurNum());
			gaussianBlur(shadowBuffer, lightManager.shadowBlurPasses);
		}

		if (lightMapDrawingDisabled)
			return;
		
		frameBuffer.getColorBufferTexture().bind(1);
		shadowBuffer.getColorBufferTexture().bind(0);

		final Color c = lightHandler.getAmbientLight();
		shadowShader.begin();
		{
			lightHandler.diffuseBlendFunc.apply();
			shadowShader.setUniformf("ambient", c.r, c.g, c.b, c.a);
			shadowShader.setUniformi("u_texture", 1);
			shadowShader.setUniformi("u_shadows", 0);
			lightMapMesh.render(shadowShader, GL20.GL_TRIANGLE_FAN);
		}
		shadowShader.end();
		
		Gdx.gl20.glDisable(GL20.GL_BLEND);
	}

	public P3dLightMap(P3dLightManager manager, int fboWidth, int fboHeight) {
		super(manager, fboWidth, fboHeight);
		lightManager = manager;
		
		shadowShader = DynamicShadowShader.createShadowShader(P3dLightManager.colorReduction);
		shadowBuffer = new FrameBuffer(Format.RGBA8888, fboWidth,
				fboHeight, false);
	}

	public void dispose() {
		super.dispose();
		shadowBuffer.dispose();
		shadowShader.dispose();
	}

}
