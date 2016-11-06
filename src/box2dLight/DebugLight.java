package box2dLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Created by PiotrJ on 14/11/2015.
 */
public interface DebugLight {
	void debugDraw (ShapeRenderer renderer);
	void drawRays(ShapeRenderer renderer);
	void drawEdge(ShapeRenderer renderer);
	void setDebugColors(Color ray, Color hardEdge, Color softEdge);
	boolean isSleeping ();
}
