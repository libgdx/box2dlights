package tests;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

public class CustomShaderDesktopLauncher {

	public static void main(String[] argv) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.title = "box2d lights test";
		config.width = 800;
		config.height = 480;
		config.samples = 4;
		config.depth = 0;
		config.vSyncEnabled = true;

		config.fullscreen = false;
		new LwjglApplication(new Box2dLightCustomShaderTest(), config);
	}

}
