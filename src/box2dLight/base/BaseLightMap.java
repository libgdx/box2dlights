package box2dLight.base;

import shaders.Gaussian;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/**
 * Base class for lighting rendering, used to reduce code duplication between
 * the common and pseudo3d box2dlight implementations
 * 
 * @author kalle_h
 * @author rinold
 */
public abstract class BaseLightMap
implements Disposable {

	protected BaseLightHandler lightHandler;
	protected Mesh lightMapMesh;
	protected FrameBuffer pingPongBuffer;
	protected ShaderProgram blurShader;
	protected boolean lightMapDrawingDisabled;

	public FrameBuffer frameBuffer;

	public abstract void render();

	public BaseLightMap(BaseLightHandler handler, int fboWidth, int fboHeight) {
		lightHandler = handler;

		if (fboWidth <= 0) fboWidth = 1;
		if (fboHeight <= 0) fboHeight = 1;
		
		frameBuffer = new FrameBuffer(Format.RGBA8888, fboWidth,
				fboHeight, false);
		pingPongBuffer = new FrameBuffer(Format.RGBA8888, fboWidth,
				fboHeight, false);

		lightMapMesh = createLightMapMesh();
		
		blurShader = Gaussian.createBlurShader(fboWidth, fboHeight);
	}

	public void dispose() {
		lightMapMesh.dispose();
		frameBuffer.dispose();
		pingPongBuffer.dispose();
		blurShader.dispose();
	}

	public void gaussianBlur(FrameBuffer buffer, int blurNum) {
		Gdx.gl20.glDisable(GL20.GL_BLEND);
		
		for (int i = 0; i < blurNum; i++) {
			buffer.getColorBufferTexture().bind(0);

			// horizontal
			pingPongBuffer.begin();
			{
				blurShader.begin();
				blurShader.setUniformf("dir", 1f, 0f);
				lightMapMesh.render(blurShader, GL20.GL_TRIANGLE_FAN, 0, 4);
				blurShader.end();
			}
			pingPongBuffer.end();

			pingPongBuffer.getColorBufferTexture().bind(0);
			// vertical
			buffer.begin();
			{
				blurShader.begin();
				blurShader.setUniformf("dir", 0f, 1f);
				lightMapMesh.render(blurShader, GL20.GL_TRIANGLE_FAN, 0, 4);
				blurShader.end();

			}
			if (lightHandler.customViewport) {
				buffer.end(
						lightHandler.viewportX,
						lightHandler.viewportY,
						lightHandler.viewportWidth,
						lightHandler.viewportHeight);
			} else {
				buffer.end();
			}
		}

		Gdx.gl20.glEnable(GL20.GL_BLEND);
	}

	Mesh createLightMapMesh() {
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
