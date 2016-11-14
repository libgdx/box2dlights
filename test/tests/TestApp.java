package tests;

import box2dLight.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/**
 * Created by PiotrJ on 17/08/2016.
 */
public class TestApp extends ApplicationAdapter implements InputProcessor {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.width = 1280;
		config.height = 720;
		config.samples = 4;
		config.useHDPI = true;
		new LwjglApplication(new TestApp(), config);
	}

	public final static float SCALE = 32f;
	public final static float INV_SCALE = 1f/SCALE;
	public final static float VP_WIDTH = 1280 * INV_SCALE;
	public final static float VP_HEIGHT = 720 * INV_SCALE;


	enum LightType {POINT, CONE, LINE, CHAIN, DIRECTIONAL}

	private SpriteBatch batch;
	private ScreenViewport gui;
	private LightsView smooth;
	private LightsView normal;
	private Stage stage;
	private boolean drawGUI;

	public TestApp () {}

	@Override public void create () {
		gui = new ScreenViewport();
		long seed = TimeUtils.nanoTime();
		RayHandler.setGammaCorrection(false);
		RayHandler.useDiffuseLight(true);
		final int rayCount = 64;
		smooth = new LightsView(seed) {
			@Override public void setLightType (LightType type) {
				if (light != null) light.remove(true);
				switch (type) {
				case POINT:
					light = new SmoothPointLight(rayHandler, rayCount/4, Color.GREEN, 6, 0, 0, rayCount/4 * 3);
					break;
				case CONE:
					light = new SmoothConeLight(rayHandler, rayCount/4, Color.GREEN, 6, 0, 0, 0, 45, rayCount/4 * 3);
					break;
				case LINE:
					light = new SmoothLineLight(rayHandler, rayCount/4, Color.GREEN, 6, 1, 3, rayCount/4 * 3);
					break;
				case CHAIN:
					float[] chain = new float[] {
						0, 2.5f,
						.5f, 2.5f/3,
						-.5f, -2.5f/3,
						0, -2.5f};
					light = new SmoothChainLight(rayHandler, rayCount/4, Color.GREEN, 6, 1, 1, chain, rayCount/4 * 3);
					break;
				case DIRECTIONAL:
					light = new SmoothDirectionalLight(rayHandler, rayCount/4 * 2, Color.GREEN, 41, rayCount/4 * 3 * 2);
					break;
				}
				light.setColor(0, 1, 0, .75f);
				light.setSoftnessLength(2);
				light.setSoft(true);
			}
		};
		smooth.clear.set(.2f, .21f, .2f, 1);
		smooth.setLightType(LightType.POINT);
		normal = new LightsView(seed) {
			@Override public void setLightType (LightType type) {
				if (light != null) light.remove(true);
				switch (type) {
				case POINT:
					light = new DebugPointLight(rayHandler, rayCount, Color.RED, 6, 0, 0);
					break;
				case CONE:
					light = new DebugConeLight(rayHandler, rayCount, Color.RED, 6, 0, 0, 0, 45);
					break;
				case LINE:
					// note: default ChainLight is fixed, doesnt move or rotate
					light = new ChainLight(rayHandler, rayCount, Color.RED, 6, 1, new float[] {
						-1.5f, 0, 1.5f, 0});
					break;
				case CHAIN:
					// note: default ChainLight is fixed, doesnt move or rotate
					light = new ChainLight(rayHandler, rayCount, Color.RED, 6, 1, new float[] {
						0, 2.5f,
						.5f, 2.5f/3,
						-.5f, -2.5f/3,
						0, -2.5f});
					break;
				case DIRECTIONAL:
					light = new DebugDirectionalLight(rayHandler, rayCount * 2, Color.RED, 41);
					break;
				}
				light.setColor(1, 0, 0, .75f);
				light.setSoftnessLength(2);
				light.setSoft(true);
			}
		};
		normal.clear.set(.21f, .2f, .2f, 1);
		normal.setLightType(LightType.POINT);

		batch = new SpriteBatch();
		stage = new Stage(gui, batch);

		Gdx.input.setInputProcessor(new InputMultiplexer(stage, this));
	}

	@Override public void render () {
		Gdx.gl.glClearColor(.5f, .5f, .5f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		float delta = Gdx.graphics.getDeltaTime();
		smooth.render(delta);
		normal.render(delta);

		if (drawGUI) {
			gui.apply();
			Gdx.gl.glScissor(gui.getScreenX(), gui.getScreenY(), gui.getScreenWidth(), gui.getScreenHeight());
			stage.act(delta);
			stage.draw();
		}
	}

	@Override public void resize (int width, int height) {
		int margin = 10;
		gui.update(width, height, false);
		smooth.resize(margin, margin, width/2 - margin, height - margin * 2);
		normal.resize(width/2 + margin, margin, width/2 - margin * 2, height - margin * 2);
	}

	@Override public void dispose () {
		smooth.dispose();
		normal.dispose();
		batch.dispose();
	}

	private static abstract class LightsView implements Disposable {
		Box2DDebugRenderer worldRenderer;
		RayHandler rayHandler;
		World world;
		ShapeRenderer renderer;
		OrthographicCamera camera;
		ExtendViewport viewport;
		Color clear = new Color(Color.WHITE);
		Rectangle bounds = new Rectangle();
		Vector3 tp = new Vector3();
		Vector3 lastTp = new Vector3();
		Light light;
		BSpline<Vector2> lightPath;
		boolean over;
		Array<Body> obstacles = new Array<Body>();
		long seed;

		public LightsView (long seed) {
			this.seed = seed;
			world = new World(new Vector2(0, -10), true);
			worldRenderer = new Box2DDebugRenderer();
			rayHandler = new RayHandler(world);
			rayHandler.setShadows(false);
			rayHandler.setBlurNum(0);
			light = new DebugPointLight(rayHandler, 64, Color.GREEN, 5, VP_WIDTH/2, 6);
			light.setSoft(false);
			renderer = new ShapeRenderer();
			viewport = new ExtendViewport(VP_WIDTH/2, VP_HEIGHT, camera = new OrthographicCamera());
		}

		public abstract void setLightType (LightType type);

		private Vector2 out = new Vector2();
		private Vector2 out2 = new Vector2();
		float lightProgress;
		float rotation;
		boolean debugDraw = true;
		public void render (float delta) {
			Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
			Gdx.gl.glScissor(viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
			viewport.apply();
			Gdx.gl.glClearColor(clear.r, clear.g, clear.b, clear.a);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
			world.step(1/60f, 6, 4);
			worldRenderer.render(world, camera.combined);

			lightProgress += delta * 0.1f;
			if (lightProgress > 1) lightProgress -= 1;
			lightPath.valueAt(out, lightProgress);
			light.setPosition(out.x, out.y);
			if (over && Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
				light.setPosition(tp.x, tp.y);
			}

			rotation += delta * 15;
			if (rotation > 360) rotation -= 360;
			light.setDirection(rotation);

			// lights are clipped by viewport, so this is not required
			Gdx.gl.glScissor(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
			rayHandler.setCombinedMatrix(camera);
			rayHandler.useCustomViewport(viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
			rayHandler.updateAndRender();

			renderer.setProjectionMatrix(camera.combined);
			renderer.begin(ShapeRenderer.ShapeType.Line);
			renderer.setColor(Color.CYAN);
//			float val = 0f;
//			while (val <= 1f) {
//				lightPath.valueAt(out, val);
//				val += 1/100f;
//				lightPath.valueAt(out2, val);
//				renderer.line(out, out2);
//			}
//			for (Vector2 point : lightPath.controlPoints) {
//				renderer.circle(point.x, point.y, .1f, 8);
//			}

			Gdx.gl.glEnable(GL20.GL_BLEND);
			if (light instanceof DebugLight && debugDraw) {
				DebugLight dl = (DebugLight)light;
				dl.drawEdge(renderer);
				dl.drawRays(renderer);
			}
			renderer.end();
		}

		public void resize(int x, int y, int width, int height) {
			bounds.set(x, y, width, height);
			viewport.update(width, height, true);
			viewport.setScreenX(x);
			viewport.setScreenY(y);
			rayHandler.resizeFBO(width, height);
			reload();
		}

		@Override public void dispose () {
			rayHandler.dispose();
			renderer.dispose();
			worldRenderer.dispose();
			world.dispose();
		}

		public void mouseMoved (int screenX, int screenY) {
			if (bounds.contains(screenX, screenY)) {
				over = true;
			} else {
				if (over) {
					touchUp(screenX, screenY, Input.Buttons.LEFT);
					touchUp(screenX, screenY, Input.Buttons.MIDDLE);
					touchUp(screenX, screenY, Input.Buttons.RIGHT);
				}
				over = false;
			}
		}

		public void reload () {
			for (Body ground : obstacles) {
				world.destroyBody(ground);
			}
			obstacles.clear();

			MathUtils.random.setSeed(seed);
			float w = viewport.getWorldWidth();
			float h = viewport.getWorldHeight();
			float hw = w / 2f;
			float hh = h / 2f;
			BodyDef chainBodyDef = new BodyDef();
			chainBodyDef.type = BodyDef.BodyType.StaticBody;

			ChainShape chainShape = new ChainShape();
			chainShape.createChain(new float[] {
				0, hh/4,
				.5f, hh/7,
				-.25f, hh/10,
				0, 0,
				-.25f, -hh/10,
				.5f, -hh/7,
				0, -hh/4});
			{
				Body obstacle = world.createBody(chainBodyDef);
				obstacle.createFixture(chainShape, 0);
				obstacle.setTransform(2.5f, hh - 5, 200 * MathUtils.degreesToRadians);
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(chainBodyDef);
				obstacle.createFixture(chainShape, 0);
				obstacle.setTransform(2.5f, hh + 5, -20 * MathUtils.degreesToRadians);
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(chainBodyDef);
				obstacle.createFixture(chainShape, 0);
				obstacle.setTransform(w - 2.5f, hh - 5, 160 * MathUtils.degreesToRadians);
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(chainBodyDef);
				obstacle.createFixture(chainShape, 0);
				obstacle.setTransform(w - 2.5f, hh + 5, 20 * MathUtils.degreesToRadians);
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(chainBodyDef);
				obstacle.createFixture(chainShape, 0);
				obstacle.setTransform(hw, 2, 90 * MathUtils.degreesToRadians);
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(chainBodyDef);
				obstacle.createFixture(chainShape, 0);
				obstacle.setTransform(hw, h - 2, 90 * MathUtils.degreesToRadians);
				obstacles.add(obstacle);
			}
			chainShape.dispose();

			CircleShape circleShape = new CircleShape();
			circleShape.setRadius(.75f);
			BodyDef obstacleBodyDef = new BodyDef();
			obstacleBodyDef.type = BodyDef.BodyType.KinematicBody;
			{
				Body obstacle = world.createBody(obstacleBodyDef);
				obstacle.createFixture(circleShape, 0);
				obstacle.setTransform(hw, hh + 5, 0);
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(obstacleBodyDef);
				obstacle.createFixture(circleShape, 0);
				obstacle.setTransform(hw, hh - 5, 0);
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(obstacleBodyDef);
				obstacle.createFixture(circleShape, 0);
				obstacle.setTransform(hw - 5, hh + 2, 0);
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(obstacleBodyDef);
				obstacle.createFixture(circleShape, 0);
				obstacle.setTransform(hw - 5, hh - 2, 0);
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(obstacleBodyDef);
				obstacle.createFixture(circleShape, 0);
				obstacle.setTransform(hw + 5, hh + 2, 0);
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(obstacleBodyDef);
				obstacle.createFixture(circleShape, 0);
				obstacle.setTransform(hw + 5, hh - 2, 0);
				obstacles.add(obstacle);
			}
			circleShape.dispose();

			PolygonShape polygonShape = new PolygonShape();
			polygonShape.setAsBox(.75f, .75f);
			{
				Body obstacle = world.createBody(obstacleBodyDef);
				obstacle.createFixture(polygonShape, 0);
				obstacle.setTransform(hw - 3.5f, hh - 5.5f, 0);
				obstacle.setAngularDamping(0);
				obstacle.setAngularVelocity(MathUtils.randomSign() * MathUtils.random(1, 3));
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(obstacleBodyDef);
				obstacle.createFixture(polygonShape, 0);
				obstacle.setTransform(hw + 3.5f, hh - 5.5f, 0);
				obstacle.setAngularDamping(0);
				obstacle.setAngularVelocity(MathUtils.randomSign() * MathUtils.random(1, 3));
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(obstacleBodyDef);
				obstacle.createFixture(polygonShape, 0);
				obstacle.setTransform(hw - 3.5f, hh + 5.5f, 0);
				obstacle.setAngularDamping(0);
				obstacle.setAngularVelocity(MathUtils.randomSign() * MathUtils.random(1, 3));
				obstacles.add(obstacle);
			}
			{
				Body obstacle = world.createBody(obstacleBodyDef);
				obstacle.createFixture(polygonShape, 0);
				obstacle.setTransform(hw + 3.5f, hh + 5.5f, 0);
				obstacle.setAngularDamping(0);
				obstacle.setAngularVelocity(MathUtils.randomSign() * MathUtils.random(1, 3));
				obstacles.add(obstacle);
			}
			polygonShape.dispose();

			MathUtils.random.setSeed(TimeUtils.nanoTime());

			Vector2[] points = new Vector2[6];
			points[0] = new Vector2(hw - 7, hh - 10);
			points[1] = new Vector2(hw, hh - 6.5f);
			points[2] = new Vector2(hw + 7, hh - 10);
			points[3] = new Vector2(hw + 7, hh + 10);
			points[4] = new Vector2(hw, hh + 6.5f);
			points[5] = new Vector2(hw - 7, hh + 10);

			lightPath = new BSpline<Vector2>(points, 3, true);
		}

		boolean panning;
		Vector3 panDelta = new Vector3();
		public void touchDown (int screenX, int screenY, int button) {
			if (!over) return;
			viewport.unproject(tp.set(screenX, screenY, 0));
			if (button == Input.Buttons.MIDDLE) {
				panDelta.set(tp);
				panning = true;
			}
			lastTp.set(tp);
		}

		public void touchDragged (int screenX, int screenY) {
			if (!over) return;
			viewport.unproject(tp.set(screenX, screenY, 0));
			if (panning) {
				camera.position.add(panDelta.x - tp.x, panDelta.y - tp.y, 0);
				camera.update();
			}
			lastTp.set(tp);
		}

		public void touchUp (int screenX, int screenY, int button) {
			if (!over) return;
			viewport.unproject(tp.set(screenX, screenY, 0));
			if (panning) {
				panning = false;
			}
			lastTp.set(tp);
		}

		public void scrolled (int amount) {
			if (!over) return;
			viewport.unproject(lastTp.set(Gdx.input.getX(), Gdx.input.getY(), 0));
			camera.zoom = MathUtils.clamp(camera.zoom + camera.zoom * .1f * amount, .1f, 3f);
			camera.update();
			viewport.unproject(tp.set(Gdx.input.getX(), Gdx.input.getY(), 0));
			camera.translate(lastTp.sub(tp));
			camera.update();
		}

		public void resetCamera() {
			camera.zoom = 1;
			camera.position.set(viewport.getWorldWidth()/2, viewport.getWorldHeight()/2, 0);
			camera.update();
		}

		public void toggleDebug () {
			debugDraw = !debugDraw;
		}

		public void toggleSoft () {
			light.setSoft(!light.isSoft());
		}
	}

	@Override public boolean keyDown (int keycode) {
		switch (keycode) {
		case Input.Keys.R: {
			Gdx.app.log("", "reload");
			smooth.reload();
			normal.reload();
		} break;
		case Input.Keys.SLASH: {
			drawGUI = !drawGUI;
		} break;
		case Input.Keys.SPACE: {
			smooth.resetCamera();
			normal.resetCamera();
		} break;
		case Input.Keys.D: {
			smooth.toggleDebug();
			normal.toggleDebug();
		} break;
		case Input.Keys.S: {
			smooth.toggleSoft();
			normal.toggleSoft();
		} break;
		case Input.Keys.NUM_1: {
			smooth.setLightType(LightType.POINT);
			normal.setLightType(LightType.POINT);
		} break;
		case Input.Keys.NUM_2: {
			smooth.setLightType(LightType.CONE);
			normal.setLightType(LightType.CONE);
		} break;
		case Input.Keys.NUM_3: {
			smooth.setLightType(LightType.LINE);
			normal.setLightType(LightType.LINE);
		} break;
		case Input.Keys.NUM_4: {
			smooth.setLightType(LightType.CHAIN);
			normal.setLightType(LightType.CHAIN);
		} break;
		case Input.Keys.NUM_5: {
			smooth.setLightType(LightType.DIRECTIONAL);
			normal.setLightType(LightType.DIRECTIONAL);
		} break;
		}
		return false;
	}

	@Override public boolean keyUp (int keycode) {
		return false;
	}

	@Override public boolean keyTyped (char character) {
		return false;
	}

	@Override public boolean touchDown (int screenX, int screenY, int pointer, int button) {
		smooth.touchDown(screenX, screenY, button);
		normal.touchDown(screenX, screenY, button);
		return false;
	}

	@Override public boolean touchUp (int screenX, int screenY, int pointer, int button) {
		smooth.touchUp(screenX, screenY, button);
		normal.touchUp(screenX, screenY, button);
		return false;
	}

	@Override public boolean touchDragged (int screenX, int screenY, int pointer) {
		smooth.touchDragged(screenX, screenY);
		normal.touchDragged(screenX, screenY);
		return false;
	}

	@Override public boolean mouseMoved (int screenX, int screenY) {
		smooth.mouseMoved(screenX, screenY);
		normal.mouseMoved(screenX, screenY);
		return false;
	}

	@Override public boolean scrolled (int amount) {
		smooth.scrolled(amount);
		normal.scrolled(amount);
		return false;
	}

	private static class DebugPointLight extends PointLight implements DebugLight {
		public DebugPointLight (RayHandler rayHandler, int rays, Color color, float distance, float x, float y) {
			super(rayHandler, rays, color, distance, x, y);
		}

		public void drawEdge(ShapeRenderer renderer) {
			if (isSoft()) {
				int numVertices = softShadowMesh.getNumVertices();
				// default mesh edge
				renderer.setColor(1, 1, 0, .25f);
				for (int i = 0; i < numVertices * 4 - 8; i += 8) {
					renderer.line(segments[i], segments[i + 1], segments[i + 8], segments[i + 9]);
				}
				renderer.setColor(1, 0, 0, .25f);
				// soft mesh edge
				for (int i = 0; i < numVertices * 4 - 8; i += 8) {
					renderer.line(segments[i + 4], segments[i + 5], segments[i + 12], segments[i + 13]);
				}
			} else {
				int numVertices = lightMesh.getNumVertices();
				renderer.setColor(1, 0, 0, .25f);
				for (int i = 4; i < numVertices * 4 - 4; i += 4) {
					renderer.line(segments[i], segments[i + 1], segments[i + 4], segments[i + 5]);
				}
			}
		}
		public void drawRays(ShapeRenderer renderer) {
			renderer.setColor(0, 1, 1, .1f);
			float sx = getX();
			float sy = getY();
			if (isSoft()) {
				int numVertices = softShadowMesh.getNumVertices();
				for (int i = 0; i < numVertices * 4 - 8; i += 8) {
					renderer.line(sx, sy, segments[i + 4], segments[i + 5]);
				}
			} else {
				// rays
				int numVertices = lightMesh.getNumVertices();
				for (int i = 4; i < numVertices * 4; i += 4) {
					renderer.line(sx, sy, segments[i], segments[i + 1]);
				}
			}
		}
		@Override public void setDebugColors (Color ray, Color hardEdge, Color softEdge) {}
		@Override public void debugDraw (ShapeRenderer renderer) {}

		@Override public boolean isSleeping () {
			return false;
		}
	}

	public static class DebugConeLight extends ConeLight implements DebugLight {
		public DebugConeLight (RayHandler rayHandler, int rays, Color color, float distance, float x, float y, float direction, float cone) {
			super(rayHandler, rays, color, distance, x, y, direction, cone);
		}

		public void drawRays(ShapeRenderer renderer) {
			float sx = getX();
			float sy = getY();
			renderer.setColor(0, 1, 1, .1f);
			if (isSoft()) {
				int numVertices = softShadowMesh.getNumVertices();
				for (int i = 0; i < numVertices * 4 - 8; i += 8) {
					renderer.line(sx, sy, segments[i + 4], segments[i + 5]);
				}
			} else {
				// rays
				int numVertices = lightMesh.getNumVertices();
				for (int i = 4; i < numVertices * 4; i += 4) {
					renderer.line(sx, sy, segments[i], segments[i + 1]);
				}
			}
		}

		public void drawEdge(ShapeRenderer renderer) {
			if (isSoft()) {
				int numVertices = softShadowMesh.getNumVertices();
				renderer.setColor(1, 1, 0, .25f);
				// soft mesh edge
				for (int i = 0; i < numVertices * 4 - 8; i += 8) {
					renderer.line(segments[i + 4], segments[i + 5], segments[i + 12], segments[i + 13]);
				}
				// default mesh edge
				renderer.setColor(1, 0, 0, .25f);
				for (int i = 0; i < numVertices * 4 - 8; i += 8) {
					renderer.line(segments[i], segments[i + 1], segments[i + 8], segments[i + 9]);
				}
			} else {
				int numVertices = lightMesh.getNumVertices();
				renderer.setColor(1, 0, 0, .25f);
				for (int i = 4; i < numVertices * 4 - 4; i += 4) {
					renderer.line(segments[i], segments[i + 1], segments[i + 4], segments[i + 5]);
				}
			}
		}
		@Override public void setDebugColors (Color ray, Color hardEdge, Color softEdge) {}
		@Override public void debugDraw (ShapeRenderer renderer) {}
		@Override public boolean isSleeping () {
			return false;
		}
	}

	public static class DebugDirectionalLight extends DirectionalLight implements DebugLight {

		public DebugDirectionalLight (RayHandler rayHandler, int rays, Color color, float directionDegree) {
			super(rayHandler, rays, color, directionDegree);
		}

		public void drawRays(ShapeRenderer renderer) {
			renderer.setColor(0, 1, 1, .1f);
			if (isSoft()) {
				int numVertices = softShadowMesh.getNumVertices();
				for (int i = 0; i < numVertices * 4 - 8; i += 8) {
					renderer.line(segments[i], segments[i + 1], segments[i + 4], segments[i + 5]);
				}
				lightMesh.getVertices(segments);
				numVertices = lightMesh.getNumVertices();
				for (int i = 0; i < numVertices * 4; i += 8) {
					renderer.line(segments[i], segments[i + 1],segments[i + 4], segments[i + 5]);
				}
			} else {
				// rays
				int numVertices = lightMesh.getNumVertices();
				for (int i = 0; i < numVertices * 4; i += 8) {
					renderer.line(segments[i], segments[i + 1],segments[i + 4], segments[i + 5]);
				}
			}
		}

		public void drawEdge(ShapeRenderer renderer) {
			if (isSoft()) {
				int numVertices = softShadowMesh.getNumVertices();
				renderer.setColor(1, 1, 0, .25f);
				// soft mesh edge
				for (int i = 0; i < numVertices * 4 - 8; i += 8) {
					renderer.line(segments[i + 4], segments[i + 5], segments[i + 12], segments[i + 13]);
				}
				// default mesh edge
				renderer.setColor(1, 0, 0, .25f);
				for (int i = 0; i < numVertices * 4 - 8; i += 8) {
					renderer.line(segments[i], segments[i + 1], segments[i + 8], segments[i + 9]);
				}
			} else {
				int numVertices = lightMesh.getNumVertices();
				renderer.setColor(1, 1, 0, .25f);
				for (int i = 0; i < numVertices * 4 - 8; i += 8) {
					renderer.line(segments[i + 4], segments[i + 5], segments[i + 12], segments[i + 13]);
				}
			}
		}
		@Override public void setDebugColors (Color ray, Color hardEdge, Color softEdge) {}
		@Override public void debugDraw (ShapeRenderer renderer) {}
		@Override public boolean isSleeping () {
			return false;
		}
	}
}
