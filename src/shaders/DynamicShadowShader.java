package shaders;

import box2dLight.RayHandler;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public class DynamicShadowShader {
	static final public ShaderProgram createShadowShader() {
		final String vertexShader = "attribute vec4 a_position;\n" //
				+ "attribute vec2 a_texCoord;\n" //
				+ "varying vec2 v_texCoords;\n" //
				+ "\n" //
				+ "void main()\n" //
				+ "{\n" //
				+ "   v_texCoords = a_texCoord;\n" //
				+ "   gl_Position = a_position;\n" //
				+ "}\n";

		// this is always perfect precision
		final String fragmentShader = "#ifdef GL_ES\n" //
				+ "precision lowp float;\n" //
				+ "#define MED mediump\n"
				+ "#else\n"
				+ "#define MED \n"
				+ "#endif\n" //
				+ "varying MED vec2 v_texCoords;\n" //
				+ "uniform sampler2D u_texture;\n" //
				+ "uniform sampler2D u_shadows;\n" //
				+ "uniform vec4 ambient;\n"
				+ "uniform int isDiffuse;\n" //
				+ "void main()\n"//
				+ "{\n" //
				+ "if(isDiffuse == 0)\n"//
				+ "{\n"//
				+ "vec4 c = texture2D(u_texture, v_texCoords);\n"//
				+ "vec4 sh = texture2D(u_shadows, v_texCoords);\n"//
				+ "gl_FragColor.rgb = (ambient.rgb + c.rgb * c.a) - sh.rgb;\n"//
				+ "gl_FragColor.a = ambient.a - c.a;\n"//
				+ "}\n"//
				+ "else\n"//
				+ "{\n"//
				+ "    vec4 c = texture2D(u_texture, v_texCoords);\n"//
				+ "    vec4 sh = texture2D(u_shadows, v_texCoords);\n"//
				+ "    gl_FragColor.rgb = (ambient.rgb + (" + RayHandler.getDynamicShadowColorReduction() + " * c.rgb)) - sh.rgb;\n"
				+ "    gl_FragColor.a = 1.0;\n"//
				+ "}\n"//
				+ "}\n";
		ShaderProgram.pedantic = false;
		ShaderProgram shadowShader = new ShaderProgram(vertexShader,
				fragmentShader);
		if (!shadowShader.isCompiled()) {
			shadowShader = new ShaderProgram("#version 330 core\n" +vertexShader,
					"#version 330 core\n" +fragmentShader);
			if(!shadowShader.isCompiled()){
				Gdx.app.log("ERROR", shadowShader.getLog());
			}
		}

		return shadowShader;
	}

}
