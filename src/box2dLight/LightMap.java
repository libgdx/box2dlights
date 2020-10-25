package box2dLight;

import shaders.*;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

class LightMap {
	private ShaderProgram shadowShader;
	FrameBuffer frameBuffer;
	private Mesh lightMapMesh;

	private FrameBuffer pingPongBuffer;

	private RayHandler rayHandler;
	private ShaderProgram withoutShadowShader;
	private ShaderProgram blurShader;
	private ShaderProgram diffuseShader;

	FrameBuffer shadowBuffer;

	boolean lightMapDrawingDisabled;

	private final int fboWidth, fboHeight;

	public LightMap(RayHandler rayHandler, int fboWidth, int fboHeight) {
		this.rayHandler = rayHandler;

		if (fboWidth <= 0)
			fboWidth = 1;
		if (fboHeight <= 0)
			fboHeight = 1;

		this.fboWidth = fboWidth;
		this.fboHeight = fboHeight;

		frameBuffer = new FrameBuffer(Format.RGBA8888, fboWidth,
				fboHeight, false);
		pingPongBuffer = new FrameBuffer(Format.RGBA8888, fboWidth,
				fboHeight, false);
		shadowBuffer = new FrameBuffer(Format.RGBA8888, fboWidth,
				fboHeight, false);

		lightMapMesh = createLightMapMesh();

		createShaders();
	}

	public void render() {
		boolean needed = rayHandler.lightRenderedLastFrame > 0;

		if (lightMapDrawingDisabled)
			return;

		if (rayHandler.pseudo3d) {
			frameBuffer.getColorBufferTexture().bind(1);
			shadowBuffer.getColorBufferTexture().bind(0);
		} else {
			frameBuffer.getColorBufferTexture().bind(0);
		}

		// at last lights are rendered over scene
		if (rayHandler.shadows) {
			final Color c = rayHandler.ambientLight;
			ShaderProgram shader = shadowShader;
			if (rayHandler.pseudo3d) {
				shader.bind();
				if (RayHandler.isDiffuse) {
					rayHandler.diffuseBlendFunc.apply();
					shader.setUniformf("ambient", c.r, c.g, c.b, c.a);
				} else {
					rayHandler.shadowBlendFunc.apply();
					shader.setUniformf("ambient", c.r * c.a, c.g * c.a,
							c.b * c.a, 1f - c.a);
				}
				shader.setUniformi("isDiffuse", RayHandler.isDiffuse ? 1 : 0);
				shader.setUniformi("u_texture", 1);
				shader.setUniformi("u_shadows", 0);
			} else if (RayHandler.isDiffuse) {
				shader = diffuseShader;
				shader.bind();
				rayHandler.diffuseBlendFunc.apply();
				shader.setUniformf("ambient", c.r, c.g, c.b, c.a);
			} else {
				shader.bind();
				rayHandler.shadowBlendFunc.apply();
				shader.setUniformf("ambient", c.r * c.a, c.g * c.a,
						c.b * c.a, 1f - c.a);
			}

			lightMapMesh.render(shader, GL20.GL_TRIANGLE_FAN);
		} else if (needed) {
			rayHandler.simpleBlendFunc.apply();
			withoutShadowShader.bind();

			lightMapMesh.render(withoutShadowShader, GL20.GL_TRIANGLE_FAN);
		}

		Gdx.gl20.glDisable(GL20.GL_BLEND);
	}

	public void gaussianBlur(FrameBuffer buffer, int blurNum) {
		Gdx.gl20.glDisable(GL20.GL_BLEND);
		for (int i = 0; i < blurNum; i++) {
			buffer.getColorBufferTexture().bind(0);
			// horizontal
			pingPongBuffer.begin();
			{
				blurShader.bind();
				blurShader.setUniformf("dir", 1f, 0f);
				lightMapMesh.render(blurShader, GL20.GL_TRIANGLE_FAN, 0, 4);

			}
			pingPongBuffer.end();

			pingPongBuffer.getColorBufferTexture().bind(0);
			// vertical
			buffer.begin();
			{
				blurShader.bind();
				blurShader.setUniformf("dir", 0f, 1f);
				lightMapMesh.render(blurShader, GL20.GL_TRIANGLE_FAN, 0, 4);
			}
			if (rayHandler.customViewport) {
				buffer.end(
					rayHandler.viewportX,
					rayHandler.viewportY,
					rayHandler.viewportWidth,
					rayHandler.viewportHeight);
			} else {
				buffer.end();
			}
		}

		Gdx.gl20.glEnable(GL20.GL_BLEND);
	}

	void dispose() {
		disposeShaders();

		lightMapMesh.dispose();

		frameBuffer.dispose();
		shadowBuffer.dispose();
		pingPongBuffer.dispose();
	}

	void createShaders() {
		disposeShaders();

		shadowShader = rayHandler.pseudo3d ? DynamicShadowShader.createShadowShader() : ShadowShader.createShadowShader();
		diffuseShader = DiffuseShader.createShadowShader();

		withoutShadowShader = WithoutShadowShader.createShadowShader();

		blurShader = Gaussian.createBlurShader(fboWidth, fboHeight);
	}

	private void disposeShaders() {
		if (shadowShader != null)
			shadowShader.dispose();
		if (diffuseShader != null)
			diffuseShader.dispose();
		if (withoutShadowShader != null)
			withoutShadowShader.dispose();
		if (blurShader != null)
			blurShader.dispose();
	}

	private Mesh createLightMapMesh() {
		float[] verts = new float[VERT_SIZE];
		// vertex coord
		verts[X1] = -1;
		verts[Y1] = -1;

		verts[X2] = 1;
		verts[Y2] = -1;

		verts[X3] = 1;
		verts[Y3] = 1;

		verts[X4] = -1;
		verts[Y4] = 1;

		// tex coords
		verts[U1] = 0f;
		verts[V1] = 0f;

		verts[U2] = 1f;
		verts[V2] = 0f;

		verts[U3] = 1f;
		verts[V3] = 1f;

		verts[U4] = 0f;
		verts[V4] = 1f;

		Mesh tmpMesh = new Mesh(true, 4, 0, new VertexAttribute(
				Usage.Position, 2, "a_position"), new VertexAttribute(
				Usage.TextureCoordinates, 2, "a_texCoord"));

		tmpMesh.setVertices(verts);
		return tmpMesh;

	}

	static public final int VERT_SIZE = 16;
	static public final int X1 = 0;
	static public final int Y1 = 1;
	static public final int U1 = 2;
	static public final int V1 = 3;
	static public final int X2 = 4;
	static public final int Y2 = 5;
	static public final int U2 = 6;
	static public final int V2 = 7;
	static public final int X3 = 8;
	static public final int Y3 = 9;
	static public final int U3 = 10;
	static public final int V3 = 11;
	static public final int X4 = 12;
	static public final int Y4 = 13;
	static public final int U4 = 14;
	static public final int V4 = 15;
}
