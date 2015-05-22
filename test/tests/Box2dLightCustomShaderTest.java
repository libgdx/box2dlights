package tests;

import box2dLight.*;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.joints.MouseJoint;
import com.badlogic.gdx.physics.box2d.joints.MouseJointDef;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.util.ArrayList;

public class Box2dLightCustomShaderTest extends InputAdapter implements ApplicationListener {
	
	static final int RAYS_PER_BALL = 64;
	static final int BALLSNUM = 8;
	static final float LIGHT_DISTANCE = 16f;
	static final float RADIUS = 1f;
	
	public static final float SCALE = 1.f/16.f;
	public static final float viewportWidth = 48;
	public static final float viewportHeight = 32;
	
	OrthographicCamera camera;
	FitViewport viewport;

	SpriteBatch batch;
	BitmapFont font;
//	TextureRegion textureRegion;

	/** our box2D world **/
	World world;

	/** our boxes **/
	ArrayList<Body> balls = new ArrayList<Body>(BALLSNUM);

	/** our ground box **/
	Body groundBody;

	/** our mouse joint **/
	MouseJoint mouseJoint = null;

	/** a hit body **/
	Body hitBody = null;

	/** pixel perfect projection for font rendering */
	Matrix4 normalProjection = new Matrix4();
	
	boolean showText = true;
	
	/** BOX2D LIGHT STUFF */
	RayHandler rayHandler;
	
	ArrayList<Light> lights = new ArrayList<Light>(BALLSNUM);
	
	float sunDirection = -90f;

	Texture bg, bgN;

	TextureRegion objectReg, objectRegN;

	FrameBuffer normalFbo;
	Array<DeferredObject> assetArray = new Array<DeferredObject>();
	DeferredObject marble;

	ShaderProgram lightShader;
	ShaderProgram normalShader;

	@Override
	public void create() {
		bg = new Texture(Gdx.files.internal("data/bg-deferred.png"));
		bgN = new Texture(Gdx.files.internal("data/bg-deferred-n.png"));

		MathUtils.random.setSeed(Long.MIN_VALUE);

		camera = new OrthographicCamera(viewportWidth, viewportHeight);
		camera.update();

		viewport = new FitViewport(viewportWidth, viewportHeight, camera);

		batch = new SpriteBatch();
		font = new BitmapFont();
		font.setColor(Color.RED);

		TextureRegion marbleD = new TextureRegion(new Texture(
			Gdx.files.internal("data/marble.png")));

		TextureRegion marbleN = new TextureRegion(new Texture(
			Gdx.files.internal("data/marble-n.png")));

		marble = new DeferredObject(marbleD, marbleN);
		marble.width = RADIUS * 2;
		marble.height = RADIUS * 2;

		createPhysicsWorld();
		Gdx.input.setInputProcessor(this);

		normalProjection.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

		/** BOX2D LIGHT STUFF BEGIN */
		RayHandler.setGammaCorrection(true);
		RayHandler.useDiffuseLight(true);

		normalShader = createNormalShader();

		lightShader = createLightShader();
		rayHandler = new RayHandler(world, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()) {
			@Override protected void updateLightShader () {}

			@Override protected void updateLightShaderPerLight (Light light) {
				// light position must be normalized
				float x = (light.getX())/viewportWidth;
				float y = (light.getY())/viewportHeight;
				lightShader.setUniformf("u_lightpos", x, y, 0.05f);
				lightShader.setUniformf("u_intensity", 5);
			}
		};
		rayHandler.setLightShader(lightShader);
		rayHandler.setAmbientLight(0.1f, 0.1f, 0.1f, 0.5f);
		rayHandler.setBlurNum(0);

		initPointLights();
		/** BOX2D LIGHT STUFF END */


		objectReg = new TextureRegion(new Texture(Gdx.files.internal("data/object-deferred.png")));
		objectRegN = new TextureRegion(new Texture(Gdx.files.internal("data/object-deferred-n.png")));

		for (int x = 0; x < 4; x++) {
			for (int y = 0; y < 3; y++) {
				DeferredObject deferredObject = new DeferredObject(objectReg, objectRegN);
				deferredObject.x = 4 + x * (deferredObject.diffuse.getRegionWidth()*SCALE + 8);
				deferredObject.y = 4 + y * (deferredObject.diffuse.getRegionHeight()*SCALE + 7);
				deferredObject.color.set(MathUtils.random(0.5f, 1), MathUtils.random(0.5f, 1), MathUtils.random(0.5f, 1), 1);
				if (x > 0)
					deferredObject.rot = true;
				deferredObject.rotation = MathUtils.random(90);
				assetArray.add(deferredObject);
			}
		}
		once = false;
		normalFbo = new FrameBuffer(Pixmap.Format.RGB565, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);
	}

	private ShaderProgram createLightShader () {
		// Shader adapted from https://github.com/mattdesl/lwjgl-basics/wiki/ShaderLesson6
		final String vertexShader =
			"attribute vec4 vertex_positions;\n" //
				+ "attribute vec4 quad_colors;\n" //
				+ "attribute float s;\n"
				+ "uniform mat4 u_projTrans;\n" //
				+ "varying vec4 v_color;\n" //
				+ "void main()\n" //
				+ "{\n" //
				+ "   v_color = s * quad_colors;\n" //
				+ "   gl_Position =  u_projTrans * vertex_positions;\n" //
				+ "}\n";
		final String fragmentShader = "#ifdef GL_ES\n" //
			+ "precision lowp float;\n" //
			+ "#define MED mediump\n"
			+ "#else\n"
			+ "#define MED \n"
			+ "#endif\n" //
			+ "varying vec4 v_color;\n" //
			+ "uniform sampler2D u_normals;\n" //
			+ "uniform vec3 u_lightpos;\n" //
			+ "uniform vec2 u_resolution;\n" //
			+ "uniform float u_intensity = 1.0;\n" //
			+ "void main()\n"//
			+ "{\n"
			+ "  vec2 screenPos = gl_FragCoord.xy / u_resolution.xy;\n"
			+ "  vec3 NormalMap = texture2D(u_normals, screenPos).rgb; "
			+ "  vec3 LightDir = vec3(u_lightpos.xy - screenPos, u_lightpos.z);\n"

			+ "  vec3 N = normalize(NormalMap * 2.0 - 1.0);\n"

			+ "  vec3 L = normalize(LightDir);\n"

			+ "  float maxProd = max(dot(N, L), 0.0);\n"
			+ "" //
			+ "  gl_FragColor = v_color * maxProd * u_intensity;\n" //
			+ "}";

		ShaderProgram.pedantic = false;
		ShaderProgram lightShader = new ShaderProgram(vertexShader,
			fragmentShader);
		if (!lightShader.isCompiled()) {
			Gdx.app.log("ERROR", lightShader.getLog());
		}

		lightShader.begin();
		lightShader.setUniformi("u_normals", 1);
		lightShader.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		lightShader.end();

		return lightShader;
	}

	private ShaderProgram createNormalShader () {
		String vertexShader = "attribute vec4 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n" //
			+ "attribute vec4 " + ShaderProgram.COLOR_ATTRIBUTE + ";\n" //
			+ "attribute vec2 " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n" //
			+ "uniform mat4 u_projTrans;\n" //
			+ "uniform float u_rot;\n" //
			+ "varying vec4 v_color;\n" //
			+ "varying vec2 v_texCoords;\n" //
			+ "varying mat2 v_rot;\n" //
			+ "\n" //
			+ "void main()\n" //
			+ "{\n" //
			+ "   vec2 rad = vec2(-sin(u_rot), cos(u_rot));\n" //
			+ "   v_rot = mat2(rad.y, -rad.x, rad.x, rad.y);\n" //
			+ "   v_color = " + ShaderProgram.COLOR_ATTRIBUTE + ";\n" //
			+ "   v_color.a = v_color.a * (255.0/254.0);\n" //
			+ "   v_texCoords = " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n" //
			+ "   gl_Position =  u_projTrans * " + ShaderProgram.POSITION_ATTRIBUTE + ";\n" //
			+ "}\n";
		String fragmentShader = "#ifdef GL_ES\n" //
			+ "#define LOWP lowp\n" //
			+ "precision mediump float;\n" //
			+ "#else\n" //
			+ "#define LOWP \n" //
			+ "#endif\n" //
			+ "varying LOWP vec4 v_color;\n" //
			+ "varying vec2 v_texCoords;\n" //
			+ "varying mat2 v_rot;\n" //
			+ "uniform sampler2D u_texture;\n" //
			+ "void main()\n"//
			+ "{\n" //
			+ "  vec4 normal = texture2D(u_texture, v_texCoords).rgba;\n" //
			// got to translate normal vector to -1, 1 range
			+ "  vec2 rotated = v_rot * (normal.xy * 2.0 - 1.0);\n" //
			// and back to 0, 1
			+ "  rotated = (rotated.xy / 2.0 + 0.5 );\n" //
			+ "  gl_FragColor = vec4(rotated.xy, normal.z, normal.a);\n" //
			+ "}";

		ShaderProgram shader = new ShaderProgram(vertexShader, fragmentShader);
		if (!shader.isCompiled()) throw new IllegalArgumentException("Error compiling shader: " + shader.getLog());
		return shader;
	}

	boolean drawNormals = false;
	Color bgColor = new Color();
	@Override
	public void render() {
		
		/** Rotate directional light like sun :) */
		if (lightsType == 3) {
			sunDirection += Gdx.graphics.getDeltaTime() * 4f;
			lights.get(0).setDirection(sunDirection);
		}

		camera.update();

		boolean stepped = fixedStep(Gdx.graphics.getDeltaTime());
		Gdx.gl.glClearColor(1f, 1f, 1f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		batch.setProjectionMatrix(camera.combined);
		for (DeferredObject deferredObject :assetArray) {
			deferredObject.update();
		}
		normalFbo.begin();
		batch.disableBlending();
		batch.begin();
		batch.setShader(normalShader);
		normalShader.setUniformf("u_rot", 0f);
		float bgWidth = bgN.getWidth() * SCALE;
		float bgHeight = bgN.getHeight() * SCALE;
		for (int x = 0; x < 6; x++) {
			for (int y = 0; y < 6; y++) {
				batch.draw(bgN, x * bgWidth, y * bgHeight, bgWidth, bgHeight);
			}
		}
		batch.enableBlending();
		for (DeferredObject deferredObject :assetArray) {
			normalShader.setUniformf("u_rot", MathUtils.degreesToRadians * deferredObject.rotation);
			deferredObject.drawNormal(batch);
			// flush batch or uniform wont change
			// TODO this is baaaad, maybe modify SpriteBatch to add rotation in the attributes? Flushing after each defeats the point of batch
			batch.flush();
		}
		for (int i = 0; i < BALLSNUM; i++) {
			Body ball = balls.get(i);
			Vector2 position = ball.getPosition();
			float angle = MathUtils.radiansToDegrees * ball.getAngle();
			marble.x = position.x - RADIUS;
			marble.y = position.y - RADIUS;
			marble.rotation = angle;
			normalShader.setUniformf("u_rot", MathUtils.degreesToRadians * marble.rotation);
			marble.drawNormal(batch);
			// TODO same as above
			batch.flush();
		}
		batch.end();
		normalFbo.end();

		Texture normals = normalFbo.getColorBufferTexture();

		batch.disableBlending();
		batch.begin();
		batch.setShader(null);
		if (drawNormals) {
			// draw flipped so it looks ok
			batch.draw(normals, 0, 0, // x, y
				viewportWidth / 2, viewportHeight / 2, // origx, origy
				viewportWidth, viewportHeight, // width, height
				1, 1, // scale x, y
				0,// rotation
				0, 0, normals.getWidth(), normals.getHeight(), // tex dimensions
				false, true); // flip x, y
		} else {
			for (int x = 0; x < 6; x++) {
				for (int y = 0; y < 6; y++) {
					batch.setColor(bgColor.set(x/5.0f, y/6.0f, 0.5f, 1));
					batch.draw(bg, x * bgWidth, y * bgHeight, bgWidth, bgHeight);
				}
			}
			batch.setColor(Color.WHITE);
			batch.enableBlending();
			for (DeferredObject deferredObject :assetArray) {
				deferredObject.draw(batch);
			}
			for (int i = 0; i < BALLSNUM; i++) {
				Body ball = balls.get(i);
				Vector2 position = ball.getPosition();
				float angle = MathUtils.radiansToDegrees * ball.getAngle();
				marble.x = position.x - RADIUS;
				marble.y = position.y - RADIUS;
				marble.rotation = angle;
				marble.draw(batch);
			}
		}
		batch.end();

		/** BOX2D LIGHT STUFF BEGIN */
		if (!drawNormals) {
			rayHandler.setCombinedMatrix(camera);
			if (stepped) rayHandler.update();
			normals.bind(1);
			rayHandler.render();
		}
		/** BOX2D LIGHT STUFF END */

		long time = System.nanoTime();

		boolean atShadow = rayHandler.pointAtShadow(testPoint.x,
				testPoint.y);
		aika += System.nanoTime() - time;
      
		/** FONT */
		if (showText) {
			batch.setProjectionMatrix(normalProjection);
			batch.begin();
			
			font.draw(batch,
					"F1 - PointLight",
					0, Gdx.graphics.getHeight());
			font.draw(batch,
					"F2 - ConeLight",
					0, Gdx.graphics.getHeight() - 15);
			font.draw(batch,
					"F3 - ChainLight",
					0, Gdx.graphics.getHeight() - 30);
			font.draw(batch,
					"F4 - DirectionalLight",
					0, Gdx.graphics.getHeight() - 45);
			font.draw(batch,
					"F5 - random lights colors",
					0, Gdx.graphics.getHeight() - 75);
			font.draw(batch,
				"F6 - random lights distance",
				0, Gdx.graphics.getHeight() - 90);
			font.draw(batch,
				"F7 - toggle drawing of normals",
				0, Gdx.graphics.getHeight() - 105);
			font.draw(batch,
					"F9 - default blending (1.3)",
					0, Gdx.graphics.getHeight() - 120);
			font.draw(batch,
					"F10 - over-burn blending (default in 1.2)",
					0, Gdx.graphics.getHeight() - 135);
			font.draw(batch,
					"F11 - some other blending",
					0, Gdx.graphics.getHeight() - 150);
			
			font.draw(batch,
					"F12 - toggle help text",
					0, Gdx.graphics.getHeight() - 180);
	
			font.draw(batch,
					Integer.toString(Gdx.graphics.getFramesPerSecond())
					+ "mouse at shadows: " + atShadow
					+ " time used for shadow calculation:"
					+ aika / ++times + "ns" , 0, 20);
	
			batch.end();
		}
	}
	
	void clearLights() {
		if (lights.size() > 0) {
			for (Light light : lights) {
				light.remove();
			}
			lights.clear();
		}
		groundBody.setActive(true);
	}
	
	void initPointLights() {
		clearLights();
		for (int i = 0; i < BALLSNUM; i++) {
			PointLight light = new PointLight(
					rayHandler, RAYS_PER_BALL, null, LIGHT_DISTANCE, 0f, 0f);
			light.attachToBody(balls.get(i), RADIUS / 2f, RADIUS / 2f);
			light.setColor(
					MathUtils.random(),
					MathUtils.random(),
					MathUtils.random(),
					1f);
			lights.add(light);
		}
	}
	
	void initConeLights() {
		clearLights();
		for (int i = 0; i < BALLSNUM; i++) {
			ConeLight light = new ConeLight(
					rayHandler, RAYS_PER_BALL, null, LIGHT_DISTANCE,
					0, 0, 0f, MathUtils.random(15f, 40f));
			light.attachToBody(
					balls.get(i),
					RADIUS / 2f, RADIUS / 2f, MathUtils.random(0f, 360f));
			light.setColor(
					MathUtils.random(),
					MathUtils.random(),
					MathUtils.random(),
					1f);
			lights.add(light);
		}
	}
	
	void initChainLights() {
		clearLights();
		for (int i = 0; i < BALLSNUM; i++) {
			ChainLight light = new ChainLight(
					rayHandler, RAYS_PER_BALL, null, LIGHT_DISTANCE, 1,
					new float[]{-5, 0, 0, 3, 5, 0});
			light.attachToBody(
					balls.get(i),
					MathUtils.random(0f, 360f));
			light.setColor(
					MathUtils.random(),
					MathUtils.random(),
					MathUtils.random(),
					1f);
			lights.add(light);
		}
	}
	
	void initDirectionalLight() {
		clearLights();
		
		groundBody.setActive(false);
		sunDirection = MathUtils.random(0f, 360f);
		
		DirectionalLight light = new DirectionalLight(
				rayHandler, 4 * RAYS_PER_BALL, null, sunDirection);
		lights.add(light);
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

		world = new World(new Vector2(0, 0), true);
		
		float halfWidth = viewportWidth / 2f;
		ChainShape chainShape = new ChainShape();
		chainShape.createLoop(new Vector2[] {
				new Vector2(0, 0f),
				new Vector2(viewportWidth, 0f),
				new Vector2(viewportWidth, viewportHeight),
				new Vector2(0, viewportHeight) });
		BodyDef chainBodyDef = new BodyDef();
		chainBodyDef.type = BodyType.StaticBody;
		groundBody = world.createBody(chainBodyDef);
		groundBody.createFixture(chainShape, 0);
		chainShape.dispose();
		createBoxes();

	}

	private void createBoxes() {
		CircleShape ballShape = new CircleShape();
		ballShape.setRadius(RADIUS);

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
			boxBodyDef.position.x = 1 + (float) (Math.random() * (viewportWidth - 2));
			boxBodyDef.position.y = 1 + (float) (Math.random() * (viewportHeight - 2));
			Body boxBody = world.createBody(boxBodyDef);
			boxBody.createFixture(def);
			boxBody.setFixedRotation(true);
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

		objectReg.getTexture().dispose();
		objectRegN.getTexture().dispose();

		normalFbo.dispose();
	}

	/**
	 * Type of lights to use:
	 * 0 - PointLight
	 * 1 - ConeLight
	 * 2 - ChainLight
	 * 3 - DirectionalLight
	 */
	int lightsType = 0;
	
	@Override
	public boolean keyDown(int keycode) {
		switch (keycode) {
		
		case Input.Keys.F1:
			if (lightsType != 0) {
				initPointLights();
				lightsType = 0;
			}
			return true;
			
		case Input.Keys.F2:
			if (lightsType != 1) {
				initConeLights();
				lightsType = 1;
			}
			return true;
			
		case Input.Keys.F3:
			if (lightsType != 2) {
				initChainLights();
				lightsType = 2;
			}
			return true;
			
		case Input.Keys.F4:
			if (lightsType != 3) {
				initDirectionalLight();
				lightsType = 3;
			}
			return true;
			
		case Input.Keys.F5:
			for (Light light : lights)
				light.setColor(
						MathUtils.random(),
						MathUtils.random(),
						MathUtils.random(),
						1f);
			return true;

		case Input.Keys.F6:
			for (Light light : lights)
				light.setDistance(MathUtils.random(LIGHT_DISTANCE * 0.5f, LIGHT_DISTANCE * 2f));
			return true;

		case Input.Keys.F7:
			drawNormals = !drawNormals;
			return true;

		case Input.Keys.F9:
			rayHandler.diffuseBlendFunc.reset();
			return true;
			
		case Input.Keys.F10:
			rayHandler.diffuseBlendFunc.set(
					GL20.GL_DST_COLOR, GL20.GL_SRC_COLOR);
			return true;
			
		case Input.Keys.F11:
			rayHandler.diffuseBlendFunc.set(
					GL20.GL_SRC_COLOR, GL20.GL_DST_COLOR);
			return true;
			
		case Input.Keys.F12:
			showText = !showText;
			return true;
			
		default:
			return false;
			
		}
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
	boolean once = true;
	@Override
	public void resize(int width, int height) {
		viewport.update(width, height, true);
	}

	@Override
	public void resume() {
	}

	private static class DeferredObject {
		TextureRegion diffuse;
		TextureRegion normal;
		Color color = new Color(Color.WHITE);
		float x, y;
		float width, height;
		float rotation;
		boolean rot;

		public DeferredObject (TextureRegion diffuse, TextureRegion normal) {
			this.diffuse = diffuse;
			this.normal = normal;
			width = diffuse.getRegionWidth() * SCALE;
			height = diffuse.getRegionHeight() * SCALE;
		}

		public void update() {
			if (rot) {
				rotation += 1f;
				if (rotation > 360)
					rotation = 0;
			}
		}

		public void drawNormal(Batch batch) {
			batch.draw(normal, x, y, width/2, height/2, width, height, 1, 1, rotation);
		}
		public void draw (Batch batch) {
			batch.setColor(color);
			batch.draw(diffuse, x, y, width/2, height/2, width, height, 1, 1, rotation);
			batch.setColor(Color.WHITE);
		}
	}
}
