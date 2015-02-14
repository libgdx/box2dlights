package box2dLight;

import shaders.DiffuseShader;
import shaders.ShadowShader;
import shaders.WithoutShadowShader;
import box2dLight.base.BaseLightMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

class LightMap extends BaseLightMap {
	
	RayHandler rayHandler;
	
	ShaderProgram withoutShadowShader;
	ShaderProgram shadowShader;
	ShaderProgram diffuseShader;

	boolean lightMapDrawingDisabled;

	public void render() {

		boolean needed = rayHandler.lightsRenderedLastFrame > 0;
		// this way lot less binding
		if (needed && rayHandler.isBlur())
			gaussianBlur(frameBuffer, rayHandler.getBlurNum());

		if (lightMapDrawingDisabled) return;
		
		frameBuffer.getColorBufferTexture().bind(0);
		// at last lights are rendered over scene
		if (rayHandler.isShadows()) {
			final Color c = rayHandler.getAmbientLight();
			ShaderProgram shader = shadowShader;
			if (RayHandler.isDiffuseLight()) {
				shader = diffuseShader;
				shader.begin();
				rayHandler.diffuseBlendFunc.apply();
				shader.setUniformf("ambient", c.r, c.g, c.b, c.a);
			} else {
				shader.begin();
				rayHandler.shadowBlendFunc.apply();
				shader.setUniformf("ambient", c.r * c.a, c.g * c.a,
						c.b * c.a, 1f - c.a);
			}
			lightMapMesh.render(shader, GL20.GL_TRIANGLE_FAN);
			shader.end();
		} else if (needed) {
			rayHandler.simpleBlendFunc.apply();
			withoutShadowShader.begin();
			lightMapMesh.render(withoutShadowShader, GL20.GL_TRIANGLE_FAN);
			withoutShadowShader.end();
		}

		Gdx.gl20.glDisable(GL20.GL_BLEND);
	}

	public LightMap(RayHandler rayHandler, int fboWidth, int fboHeight) {
		super(rayHandler, fboWidth, fboHeight);
		this.rayHandler = rayHandler;

		withoutShadowShader = WithoutShadowShader.createShadowShader();
		shadowShader = ShadowShader.createShadowShader();
		diffuseShader = DiffuseShader.createShadowShader();
	}

	public void dispose() {
		super.dispose();
		withoutShadowShader.dispose();
		shadowShader.dispose();
		diffuseShader.dispose();
	}

}
