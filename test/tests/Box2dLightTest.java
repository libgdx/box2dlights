package tests;

import java.util.ArrayList;

import box2dLight.ChainLight;
import box2dLight.Light;
import box2dLight.PointLight;
import box2dLight.RayHandler;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.QueryCallback;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.MouseJoint;
import com.badlogic.gdx.physics.box2d.joints.MouseJointDef;

public class Box2dLightTest extends InputAdapter implements ApplicationListener {
	/** the camera **/
	private com.badlogic.gdx.graphics.OrthographicCamera camera;

	/**
	 * a spritebatch and a font for text rendering and a Texture to draw our
	 * boxes
	 **/
	private static final int RAYS_PER_BALL = 128;
	private static final int BALLSNUM = 3;

	private static final float LIGHT_DISTANCE = 16f;
	private static final float radius = 1f;
	private SpriteBatch batch;
	private BitmapFont font;
	private TextureRegion textureRegion;
	private Texture bg;

	/** our box2D world **/
	private World world;

	/** our boxes **/
	private ArrayList<Body> balls = new ArrayList<Body>(BALLSNUM);

	/** our ground box **/
	Body groundBody;

	/** our mouse joint **/
	private MouseJoint mouseJoint = null;

	/** a hit body **/
	Body hitBody = null;

	/** BOX2D LIGHT STUFF BEGIN */
	RayHandler rayHandler;

	/** BOX2D LIGHT STUFF END */

	/** pixel perfect projection for font rendering */
	Matrix4 normalProjection = new Matrix4();

	@Override
	public void create() {

		MathUtils.random.setSeed(Long.MIN_VALUE);

		camera = new OrthographicCamera(48, 32);
		camera.position.set(0, 16, 0);
		camera.update();
		batch = new SpriteBatch();
		font = new BitmapFont();
		font.setColor(Color.RED);
		textureRegion = new TextureRegion(new Texture(
				Gdx.files.internal("data/marble.png")));

		bg = new Texture(Gdx.files.internal("data/bg.png"));

		createPhysicsWorld();
		Gdx.input.setInputProcessor(this);

		normalProjection.setToOrtho2D(0, 0, Gdx.graphics.getWidth(),
				Gdx.graphics.getHeight());

		/** BOX2D LIGHT STUFF BEGIN */
		 RayHandler.setGammaCorrection(true);
		RayHandler.useDiffuseLight(true);
		rayHandler = new RayHandler(world);
		rayHandler.setAmbientLight(0.2f, 0.2f, 0.2f, 0.1f);
		rayHandler.setCulling(true);		
		// rayHandler.setBlur(false);
		rayHandler.setBlurNum(1);
		//rayHandler.setShadows(true);
		camera.update(true);

		// rayHandler.setCombinedMatrix(camera.combined, camera.position.x,
		// camera.position.y, camera.viewportWidth * camera.zoom,
		// camera.viewportHeight * camera.zoom);
		for (int i = 0; i < BALLSNUM - 1; i++) {
			// final Color c = new Color(MathUtils.random()*0.4f,
			// MathUtils.random()*0.4f,
			// MathUtils.random()*0.4f, 1f);
			Light light = new PointLight(rayHandler, RAYS_PER_BALL);
			light.setDistance(LIGHT_DISTANCE);
			// Light light = new ConeLight(rayHandler, RAYS_PER_BALL, null,
			// LIGHT_DISTANCE, 0, 0, 0, 60);
			// light.setStaticLight(true);
			light.attachToBody(balls.get(i), 0, 0.5f);
			light.setColor(MathUtils.random(), MathUtils.random(),
					MathUtils.random(), 1f);
			// light.setColor(0.1f,0.1f,0.1f,0.1f);

		}
	//	 new DirectionalLight(rayHandler, 24, new Color(0,0.4f,0,1f), -45);
		shapeRenderer = new ShapeRenderer();

		ChainLight.defaultRayStartOffset = 1;
    chainLight = new ChainLight(rayHandler, 50, null, 30,
        0, 0, 1, new float[]{0, 5, -3, 10, 0, 15});
    
    chainLight2 = new ChainLight(rayHandler, 50, null, 30,
        0, 0, -1, new float[]{0, 5, -3, 10, 0, 15});

    chainLight3 = new ChainLight(rayHandler, 50, null, 30,
        0, 0, 1, new float[]{0, 5, -3, 10, 0, 15});

    chainLight.setColor(MathUtils.random(), MathUtils.random(),
        MathUtils.random(), 1f);
    chainLight2.setColor(MathUtils.random(), MathUtils.random(),
        MathUtils.random(), 1f);
    chainLight3.setColor(MathUtils.random(), MathUtils.random(),
        MathUtils.random(), 1f);

    Body body = balls.get(BALLSNUM - 1);
    body.setTransform(0, 10, 0);
    chainLight3.attachToBody(body, 1, 1);
		
		/** BOX2D LIGHT STUFF END */

	}

	ChainLight chainLight, chainLight2, chainLight3;
	ShapeRenderer shapeRenderer;

	@Override
	public void render() {

		camera.update();

		// should use fixed step

		boolean stepped = fixedStep(Gdx.graphics.getDeltaTime());
		Gdx.gl.glClearColor(0.3f, 0.3f, 0.3f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		batch.setProjectionMatrix(camera.combined);
		batch.disableBlending();
		batch.begin();

		batch.draw(bg, -24, 0, 48, 32);

		batch.enableBlending();

		for (int i = 0; i < BALLSNUM; i++) {

			final Body ball = balls.get(i);
			final Vector2 position = ball.getPosition();
			final float angle = MathUtils.radiansToDegrees * ball.getAngle();
			batch.draw(textureRegion, position.x - radius, position.y - radius,
					radius, radius, radius * 2, radius * 2, 1, 1, angle);
		}

		batch.end();

		/** BOX2D LIGHT STUFF BEGIN */

		rayHandler.setCombinedMatrix(camera.combined, camera.position.x,
				camera.position.y, camera.viewportWidth * camera.zoom,
				camera.viewportHeight * camera.zoom);

		// rayHandler.setCombinedMatrix(camera.combined);
		if (stepped)
			rayHandler.update();
		rayHandler.render();

		/** BOX2D LIGHT STUFF END */

		long time = System.nanoTime();

		boolean atShadow = rayHandler.pointAtShadow(testPoint.x,
				testPoint.y);
		aika += System.nanoTime() - time;
      
		/** FONT */
		batch.setProjectionMatrix(normalProjection);
		batch.begin();

		font.draw(batch, Integer.toString(Gdx.graphics.getFramesPerSecond())
				+ "mouse at shadows: " + atShadow + " time used for shadow calculation:" +aika / ++times + "ns" , 0, 20);

		batch.end();
		shapeRenderer.setColor(Color.WHITE);
		shapeRenderer.setProjectionMatrix(camera.combined);
		shapeRenderer.begin(ShapeType.Line);
		shapeRenderer.polyline(chainLight.chain.items, 0, chainLight.chain.size);
		shapeRenderer.end();

	}

	private final static int MAX_FPS = 30;
	private final static int MIN_FPS = 15;
	public final static float TIME_STEP = 1f / MAX_FPS;
	private final static float MAX_STEPS = 1f + MAX_FPS / MIN_FPS;
	private final static float MAX_TIME_PER_FRAME = TIME_STEP * MAX_STEPS;
	private final static int VELOCITY_ITERS = 6;
	private final static int POSITION_ITERS = 2;

	float physicsTimeLeft;
	long aika;
	int times;

	private boolean fixedStep(float delta) {
		physicsTimeLeft += delta;
		if (physicsTimeLeft > MAX_TIME_PER_FRAME)
			physicsTimeLeft = MAX_TIME_PER_FRAME;

		boolean stepped = false;
		while (physicsTimeLeft >= TIME_STEP) {
			world.step(TIME_STEP, VELOCITY_ITERS, POSITION_ITERS);
			physicsTimeLeft -= TIME_STEP;
			stepped = true;
		}
		return stepped;
	}

	private void createPhysicsWorld() {

		world = new World(new Vector2(0, -10), true);

		ChainShape chainShape = new ChainShape();
		chainShape.createLoop(new Vector2[] { new Vector2(-22, 1),
				new Vector2(22, 1), new Vector2(22, 31), new Vector2(0, 20),
				new Vector2(-22, 31) });
		BodyDef chainBodyDef = new BodyDef();
		chainBodyDef.type = BodyType.StaticBody;
		groundBody = world.createBody(chainBodyDef);
		groundBody.createFixture(chainShape, 0);
		chainShape.dispose();
		createBoxes();
	}

	private void createBoxes() {
		CircleShape ballShape = new CircleShape();
		ballShape.setRadius(radius);

		FixtureDef def = new FixtureDef();
		def.restitution = 0.9f;
		def.friction = 0.01f;
		def.shape = ballShape;
		def.density = 1f;
		BodyDef boxBodyDef = new BodyDef();
		boxBodyDef.type = BodyType.DynamicBody;

		for (int i = 0; i < BALLSNUM; i++) {
			// Create the BodyDef, set a random position above the
			// ground and create a new body
			boxBodyDef.position.x = -20 + (float) (Math.random() * 40);
			boxBodyDef.position.y = 10 + (float) (Math.random() * 15);
			Body boxBody = world.createBody(boxBodyDef);
			boxBody.createFixture(def);
			balls.add(boxBody);
		}
		ballShape.dispose();
	}

	/**
	 * we instantiate this vector and the callback here so we don't irritate the
	 * GC
	 **/
	Vector3 testPoint = new Vector3();
	QueryCallback callback = new QueryCallback() {
		@Override
		public boolean reportFixture(Fixture fixture) {
			if (fixture.getBody() == groundBody)
				return true;

			if (fixture.testPoint(testPoint.x, testPoint.y)) {
				hitBody = fixture.getBody();
				return false;
			} else
				return true;
		}
	};

	@Override
	public boolean touchDown(int x, int y, int pointer, int newParam) {
		// translate the mouse coordinates to world coordinates
		testPoint.set(x, y, 0);
		camera.unproject(testPoint);

		// ask the world which bodies are within the given
		// bounding box around the mouse pointer
		hitBody = null;
		world.QueryAABB(callback, testPoint.x - 0.1f, testPoint.y - 0.1f,
				testPoint.x + 0.1f, testPoint.y + 0.1f);

		// if we hit something we create a new mouse joint
		// and attach it to the hit body.
		if (hitBody != null) {
			MouseJointDef def = new MouseJointDef();
			def.bodyA = groundBody;
			def.bodyB = hitBody;
			def.collideConnected = true;
			def.target.set(testPoint.x, testPoint.y);
			def.maxForce = 1000.0f * hitBody.getMass();

			mouseJoint = (MouseJoint) world.createJoint(def);
			hitBody.setAwake(true);
		}

		return false;
	}

	/** another temporary vector **/
	Vector2 target = new Vector2();

	@Override
	public boolean touchDragged(int x, int y, int pointer) {
    camera.unproject(testPoint.set(x, y, 0));
    float delta = testPoint.x - target.x;
    chainLight.chain.items[2] = chainLight2.chain.items[2] = Math.max(-5f, Math.min(chainLight.chain.items[2] + delta, 5f));
    delta = testPoint.y - target.y;
    chainLight.chain.items[3] = chainLight2.chain.items[3] = Math.max(5f, Math.min(chainLight.chain.items[3] + delta, 15f));
    chainLight.updateChain();
    chainLight2.updateChain();
    target.set(testPoint.x, testPoint.y);
		// if a mouse joint exists we simply update
		// the target of the joint based on the new
		// mouse coordinates
		if (mouseJoint != null) {
			mouseJoint.setTarget(target);
		}
		return false;
	}

	@Override
	public boolean touchUp(int x, int y, int pointer, int button) {
		// if a mouse joint exists we simply destroy it
		if (mouseJoint != null) {
			world.destroyJoint(mouseJoint);
			mouseJoint = null;
		}
		return false;
	}

	@Override
	public void dispose() {
		rayHandler.dispose();
		world.dispose();
	}

	@Override
	public boolean keyDown(int keycode) {
		if (keycode == Input.Keys.RIGHT)
			camera.position.x += 3f;
		if (keycode == Input.Keys.LEFT)
			camera.position.x -= 3f;
		if (keycode == Input.Keys.UP)
			camera.position.y += 3f;
		if (keycode == Input.Keys.DOWN)
			camera.position.y -= 3f;

		return false;
	}

	@Override
	public boolean mouseMoved(int x, int y) {
		testPoint.set(x, y, 0);
		camera.unproject(testPoint);
		return false;
	}

	@Override
	public boolean scrolled(int amount) {
		camera.rotate((float) amount * 3f, 0, 0, 1);
		return false;
	}

	@Override
	public void pause() {
	}

	@Override
	public void resize(int arg0, int arg1) {
	}

	@Override
	public void resume() {
	}
}
