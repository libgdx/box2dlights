package tests;

import box2dLight.*;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.BSpline;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

/**
 * Created by PiotrJ on 17/08/2016.
 */
public class DesktopBenchmark {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.width = 800;
		config.height = 600;
		config.samples = 4;
		config.useHDPI = true;
		new LwjglApplication(new Benchmark(), config);
	}

	private static class Benchmark extends ApplicationAdapter {
		private static final String TAG = Benchmark.class.getSimpleName();
		final static float SCALE = 32f;
		final static float INV_SCALE = 1f/SCALE;
		final static float VP_WIDTH = 800 * INV_SCALE;
		final static float VP_HEIGHT = 600 * INV_SCALE;

		OrthographicCamera camera;
		ExtendViewport viewport;
		Array<Run> runs = new Array<Run>();
		int currentId;
		Run current;
		RayHandler rayHandler;
		World world;
		Box2DDebugRenderer worldRenderer;
		Array<Light> lights = new Array<Light>();
		float lightOffset;

		BSpline<Vector2> lightPath;
		Array<Body> obstacles = new Array<Body>();
		@Override public void create () {
			world = new World(new Vector2(), true);
			worldRenderer = new Box2DDebugRenderer();
			rayHandler = new RayHandler(world);
			rayHandler.setShadows(false);
			rayHandler.setBlurNum(0);
			viewport = new ExtendViewport(VP_WIDTH/2, VP_HEIGHT, camera = new OrthographicCamera());
			viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);

			float w = viewport.getWorldWidth();
			float h = viewport.getWorldHeight();
			float hw = w / 2f;
			float hh = h / 2f;

			Vector2[] points = new Vector2[6];
			points[0] = new Vector2(hw - 7, hh - 10);
			points[1] = new Vector2(hw, hh - 6.5f);
			points[2] = new Vector2(hw + 7, hh - 10);
			points[3] = new Vector2(hw + 7, hh + 10);
			points[4] = new Vector2(hw, hh + 6.5f);
			points[5] = new Vector2(hw - 7, hh + 10);

			lightPath = new BSpline<Vector2>(points, 3, true);
			long[][] runsData = new long[][]{
				{1, 32,  1234L, 128},
				{1, 64,  1234L, 128},
				{1, 128, 1234L, 128},
				{5, 32,  1234L, 128},
				{5, 64,  1234L, 128},
				{5, 128, 1234L, 128},
				{10, 32, 1234L, 128},
				{10, 64, 1234L, 128},
				{10, 128, 1234L, 128},
				{25, 32, 1234L, 128},
				{25, 64, 1234L, 128},
				{25, 128, 1234L, 128},
				{50, 32, 1234L, 128},
				{50, 64, 1234L, 128},
				{50, 128, 1234L, 128},
				{100, 32, 1234L, 128},
				{100, 64, 1234L, 128},
				{100, 128, 1234L, 128},
			};
			for (long[] runData : runsData) {
				final int lightCount = (int)runData[0];
				final int obstCount = (int)runData[1];
				final long runSeed = runData[2];
				final int rayCount = (int)runData[3];
				runs.add(new Run() {
					@Override void init () {
						name = "smooth point";
						lights = lightCount;
						obstacles = obstCount;
						runCount = 1;
						seed = runSeed;
						runFrameCount = 2 * 60;
					}
					@Override Light create (RayHandler rayHandler) {
						return new SmoothPointLight(rayHandler, rayCount/4, Color.GREEN, 6, 0, 0, rayCount/4 * 3);
					}
				});
				runs.add(new Run() {
					@Override void init () {
						name = "normal point";
						lights = lightCount;
						obstacles = obstCount;
						runCount = 1;
						seed = runSeed;
						runFrameCount = 2 * 60;
					}
					@Override Light create (RayHandler rayHandler) {
						return new PointLight(rayHandler, rayCount, Color.RED, 6, 0, 0);
					}
				});
				runs.add(new Run() {
					@Override void init () {
						name = "smooth cone";
						lights = lightCount;
						obstacles = obstCount;
						runCount = 1;
						seed = runSeed;
						runFrameCount = 2 * 60;
					}
					@Override Light create (RayHandler rayHandler) {
						return new SmoothConeLight(rayHandler, rayCount/4, Color.GREEN, 6, 0, 0, 0, 45, rayCount/4 * 3);
					}
				});
				runs.add(new Run() {
					@Override void init () {
						name = "normal cone";
						lights = lightCount;
						obstacles = obstCount;
						runCount = 1;
						seed = runSeed;
						runFrameCount = 2 * 60;
					}
					@Override Light create (RayHandler rayHandler) {
						return new ConeLight(rayHandler, rayCount, Color.RED, 6, 0, 0, 0, 45);
					}
				});
			}
			long[][] runsData2 = new long[][]{
				{1, 32, 1234L, 64},
				{1, 64, 1234L, 64},
				{1, 128, 1234L, 64},
				{1, 32, 1234L, 128},
				{1, 64, 1234L, 128},
				{1, 128, 1234L, 128},
				{1, 32, 1234L, 256},
				{1, 64, 1234L, 256},
				{1, 128, 1234L, 256},
			};
			for (long[] runData : runsData2) {
				final int lightCount = (int)runData[0];
				final int obstCount = (int)runData[1];
				final long runSeed = runData[2];
				final int rayCount = (int)runData[3];
				runs.add(new Run() {
					@Override void init () {
						name = "smooth point";
						lights = lightCount;
						obstacles = obstCount;
						seed = runSeed;
						runFrameCount = 2 * 60;
					}
					@Override Light create (RayHandler rayHandler) {
						return new SmoothDirectionalLight(rayHandler, rayCount / 4, Color.GREEN, 0, rayCount / 4 * 3);
					}
				});
				runs.add(new Run() {
					@Override void init () {
						name = "smooth point";
						lights = lightCount;
						obstacles = obstCount;
						seed = runSeed;
						runFrameCount = 2 * 60;
					}
					@Override Light create (RayHandler rayHandler) {
						return new DirectionalLight(rayHandler, rayCount / 4, Color.RED, 0);
					}
				});
			}
		}

		private void initWorld (long seed, int obsCount) {
			for (Body ground : obstacles) {
				world.destroyBody(ground);
			}
			obstacles.clear();

			MathUtils.random.setSeed(seed);
			float w = viewport.getWorldWidth();
			float h = viewport.getWorldHeight();
			{
				BodyDef chainBodyDef = new BodyDef();
				chainBodyDef.type = BodyDef.BodyType.StaticBody;

				ChainShape chainShape = new ChainShape();
				chainShape.createChain(new float[] {0, 2.25f, .5f, 1.5f, -.25f, 1f, 0, 0, -.25f, -1f, .5f, -1.5f, 0, -2.25f});

				for (int i = 0; i < obsCount; i++) {
					Body obstacle = world.createBody(chainBodyDef);
					obstacle.createFixture(chainShape, 0);
					obstacle.setTransform(MathUtils.random(1, w - 2), MathUtils.random(1, h - 2), MathUtils.random(MathUtils.PI2));
					obstacles.add(obstacle);
				}

				chainShape.dispose();
			}
			{
				CircleShape circleShape = new CircleShape();
				circleShape.setRadius(.75f);
				BodyDef obstacleBodyDef = new BodyDef();

				for (int i = 0; i < obsCount; i++) {
					if (i % 2 == 0) {
						obstacleBodyDef.type = BodyDef.BodyType.StaticBody;
					} else {
						obstacleBodyDef.type = BodyDef.BodyType.KinematicBody;
					}
					Body obstacle = world.createBody(obstacleBodyDef);
					obstacle.createFixture(circleShape, 0);
					obstacle.setTransform(MathUtils.random(1, w-2), MathUtils.random(1, h-2), 0);
					obstacles.add(obstacle);
				}

				circleShape.dispose();
			}
			{
				PolygonShape polygonShape = new PolygonShape();
				polygonShape.setAsBox(.75f, .75f);

				BodyDef obstacleBodyDef = new BodyDef();
				for (int i = 0; i < obsCount / 3; i++) {
					if (i % 2 == 0) {
						obstacleBodyDef.type = BodyDef.BodyType.StaticBody;
					} else {
						obstacleBodyDef.type = BodyDef.BodyType.KinematicBody;
					}
					Body obstacle = world.createBody(obstacleBodyDef);
					obstacle.createFixture(polygonShape, 0);
					obstacle.setTransform(w/2, h/2, 0);
					obstacle.setTransform(MathUtils.random(1, w-2), MathUtils.random(1, h-2), MathUtils.random(MathUtils.PI));
					obstacle.setAngularDamping(0);
					obstacle.setAngularVelocity(MathUtils.randomSign() * MathUtils.random(1, 3));
					obstacles.add(obstacle);
				}
				polygonShape.dispose();
			}
			MathUtils.random.setSeed(TimeUtils.nanoTime());
		}

		private Vector2 out = new Vector2();
		@Override public void render () {
			Gdx.gl.glClearColor(.35f, .35f, .35f, 1);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
			if (current == null) {
				if (currentId >= runs.size) {
					return;
				}
				current = runs.get(currentId++);
				current.init();
				initWorld(current.seed, current.obstacles);
				for (Light light : lights) {
					light.remove(true);
				}
				lights.clear();
				for (int i = 0; i < current.lights; i++) {
					lights.add(current.create(rayHandler));
				}
				lightOffset = 0;
			}

			world.step(1f/60f, 6, 4);
			worldRenderer.render(world, camera.combined);
			lightOffset += Gdx.graphics.getDeltaTime() * 60 / current.runFrameCount;
			if (lightOffset > 1) lightOffset -= 1;

			for (int i = 0, s = lights.size; i < s; i++) {
				Light light = lights.get(i);
				float lo = lightOffset + i/(float)s;
				if (lo > 1) lo -= 1;
				lightPath.valueAt(out, lo);
				light.setPosition(out.x, out.y);
				light.setDirection(lo * 360);
			}

			// we only care how long does it take to process the light, nothing else
			long start = TimeUtils.nanoTime();
			rayHandler.setCombinedMatrix(camera);
			rayHandler.updateAndRender();
			long diff = TimeUtils.nanoTime() - start;
			current.frameCount++;
			current.runTime += diff;

			// lets assume that we are running at 60
			if (current.frameCount > current.runFrameCount) {
				lightOffset = 0;
				current.run = current.runs;
				current.runs++;
//				Gdx.app.log(TAG, current.toString());
				current.frameCountTotal += current.frameCount;
				current.runTimeTotal += current.runTime;
				current.runTime = 0;
				current.frameCount = 0;
				if (current.runs >= current.runCount) {

					if (current.frameCountTotal > 0)
						current.avgFrameTimeTotal = current.runTimeTotal / current.frameCountTotal;
					Gdx.app.log(TAG, current.runCount + " runs of " + current.name
						+ " avgFrameTimeTotal " + (current.avgFrameTimeTotal/ 1000000d)
						+ " ms, lights " + current.lights
						+ ", obstacles " + current.obstacles);
					current = null;
				}
			}

		}

		@Override public void resize (int width, int height) {
			viewport.update(width, height, true);
			rayHandler.resizeFBO(width, height);
		}

		@Override public void dispose () {
			world.dispose();
			rayHandler.dispose();
		}

		static abstract class Run {
			String name;
			long seed;
			int obstacles;
			int lights;
			int runs;
			int run;
			long runTime;
			long runTimeTotal;
			long frameCount;
			long frameCountTotal;
			long avgFrameTime;
			long avgFrameTimeTotal;
			int runCount = 5;
			long runFrameCount = 5 * 60;

			abstract void init();
			abstract Light create(RayHandler rayHandler);

			@Override public String toString () {
				if (frameCount > 0)
					avgFrameTime = runTime / frameCount;
				return "Run " + name + " run "+ run + " avgFrameTime " + (avgFrameTime/ 1000000d) + " ms";
			}
		}
	}
}
