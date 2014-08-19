package box2dLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Mesh.VertexDataType;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.Pools;

public class ChainLight extends Light {
  private Body body;
  private Vector2 bodyPosition = new Vector2();
  
  public static float defaultRayStartOffset = 0.001f;
  public float rayStartOffset;

  private int rayDirection;

  public final FloatArray chain;
  private final FloatArray segmentAngles = new FloatArray();
  private final FloatArray segmentLengths = new FloatArray();
  final float[] startX;
  final float[] startY;
  final float endX[];
  final float endY[];

  private float bodyAngle;

  /**
   * attach positional light to automatically follow body. Position is fixed to
   * given offset.
   */
  @Override
  public void attachToBody(Body body, float offsetX, float offSetY) {
    this.body = body;
    this.bodyPosition.set(body.getPosition());
    bodyAngle = body.getAngle();
    if (staticLight)
      staticUpdate();
  }

  @Override
  public Vector2 getPosition() {
    return tmpPosition;
  }

  public Body getBody() {
    return body;
  }

  /** horizontal starting position of light in world coordinates. */
  @Override
  public float getX() {
    return tmpPosition.x;
  }

  /** vertical starting position of light in world coordinates. */
  @Override
  public float getY() {
    return tmpPosition.y;
  }

  private final Vector2 tmpEnd = new Vector2();
  private final Vector2 tmpStart = new Vector2();
  private final Vector2 tmpPerp = new Vector2();

  @Override
  public void setPosition(float x, float y) {
    tmpPosition.x = x;
    tmpPosition.y = y;
    if (staticLight)
      staticUpdate();
  }

  @Override
  public void setPosition(Vector2 position) {
    tmpPosition.x = position.x;
    tmpPosition.y = position.y;
    if (staticLight)
      staticUpdate();
  }

  private final Matrix3 zeroPosition = new Matrix3(),
      rotateAroundZero = new Matrix3(), restorePosition = new Matrix3();
  private final Vector2 tmpVec = new Vector2();

  @Override
  void update() {
    if (body != null && !staticLight) {

      final Vector2 vec = body.getPosition();
      tmpVec.set(0, 0).sub(bodyPosition);
      bodyPosition.set(vec);
      zeroPosition.setToTranslation(tmpVec);
      restorePosition.setToTranslation(bodyPosition);

      rotateAroundZero.setToRotationRad(bodyAngle).inv()
          .rotateRad(body.getAngle());
      bodyAngle = body.getAngle();

      for (int i = 0; i < rayNum; i++) {
        tmpVec.set(startX[i], startY[i]).mul(zeroPosition)
            .mul(rotateAroundZero).mul(restorePosition);
        startX[i] = tmpVec.x;
        startY[i] = tmpVec.y;

        tmpVec.set(endX[i], endY[i]).mul(zeroPosition).mul(rotateAroundZero)
            .mul(restorePosition);
        endX[i] = tmpVec.x;
        endY[i] = tmpVec.y;
      }
    }

    if (rayHandler.culling) {
      culled = ((!rayHandler.intersect(tmpPosition.x, tmpPosition.y, distance
          + softShadowLength)));
      if (culled)
        return;
    }

    if (staticLight)
      return;

    for (int i = 0; i < rayNum; i++) {
      m_index = i;
      f[i] = 1f;
      tmpEnd.x = endX[i];// + tmpPosition.x;
      mx[i] = tmpEnd.x;
      tmpEnd.y = endY[i];// + tmpPosition.y;
      my[i] = tmpEnd.y;
      tmpStart.x = startX[i];
      tmpStart.y = startY[i];
      if (rayHandler.world != null && !xray) {
        rayHandler.world.rayCast(ray, tmpStart, tmpEnd);
      }
    }
    setMesh();
  }
  
  public void debugRender(ShapeRenderer shapeRenderer) {
    shapeRenderer.setColor(Color.YELLOW);
    for (int i = 0; i < rayNum; i++) {
      shapeRenderer.line(startX[i], startY[i], endX[i], endY[i]);
    }
  }

  void setMesh() {

    // ray starting point
    int size = 0;

    // rays ending points.
    for (int i = 0; i < rayNum; i++) {
      segments[size++] = startX[i];
      segments[size++] = startY[i];
      segments[size++] = colorF;
      segments[size++] = 1;
      segments[size++] = mx[i];
      segments[size++] = my[i];
      segments[size++] = colorF;
      segments[size++] = 1 - f[i];
    }
    lightMesh.setVertices(segments, 0, size);

    if (!soft || xray)
      return;

    size = 0;
    // rays ending points.

    for (int i = 0; i < rayNum; i++) {

      segments[size++] = mx[i];
      segments[size++] = my[i];
      segments[size++] = colorF;
      final float s = (1 - f[i]);
      segments[size++] = s;

      tmpPerp.set(mx[i], my[i]).sub(startX[i], startY[i]).nor()
          .scl(softShadowLength * s).add(mx[i], my[i]);
      segments[size++] = tmpPerp.x;
      segments[size++] = tmpPerp.y;
      segments[size++] = zero;
      segments[size++] = 0f;
    }
    softShadowMesh.setVertices(segments, 0, size);
  }

  @Override
  void render() {
    if (rayHandler.culling && culled)
      return;

    rayHandler.lightRenderedLastFrame++;
    lightMesh.render(rayHandler.lightShader, GL20.GL_TRIANGLE_STRIP, 0,
        vertexNum);
    if (soft && !xray) {
      softShadowMesh.render(rayHandler.lightShader, GL20.GL_TRIANGLE_STRIP, 0,
          vertexNum);
    }
  }

  public ChainLight(RayHandler rayHandler, int rays, Color color,
      float distance, float x, float y, int rayDirection) {
    this(rayHandler, rays, color, distance, x, y, rayDirection, null);
  }
  
  public ChainLight(RayHandler rayHandler, int rays, Color color,
      float distance, float x, float y, int rayDirection, float[] chain) {
    super(rayHandler, rays, color, 0f, distance);
    rayStartOffset = ChainLight.defaultRayStartOffset;
    this.rayDirection = rayDirection;

    vertexNum = (vertexNum - 1) * 2;

    tmpPosition.x = x;
    tmpPosition.y = y;
    endX = new float[rays];
    endY = new float[rays];
    startX = new float[rays];
    startY = new float[rays];
    if (chain != null) {
      this.chain = new FloatArray(chain);
    } else {
      this.chain = new FloatArray();
    }
    

    updateChain();

    lightMesh = new Mesh(VertexDataType.VertexArray, false, vertexNum, 0,
        new VertexAttribute(Usage.Position, 2, "vertex_positions"),
        new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
        new VertexAttribute(Usage.Generic, 1, "s"));
    softShadowMesh = new Mesh(VertexDataType.VertexArray, false, vertexNum * 2,
        0, new VertexAttribute(Usage.Position, 2, "vertex_positions"),
        new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
        new VertexAttribute(Usage.Generic, 1, "s"));
    setMesh();
  }

  public void updateChain() {
    Vector2 v1 = Pools.obtain(Vector2.class);
    Vector2 v2 = Pools.obtain(Vector2.class);
    Vector2 vSegmentStart = Pools.obtain(Vector2.class);
    Vector2 vDirection = Pools.obtain(Vector2.class);
    Vector2 vRayOffset = Pools.obtain(Vector2.class);
    Spinor tmpAngle = Pools.obtain(Spinor.class);
    // following Spinors used to represent perpendicular angle of each segment
    Spinor previousAngle = Pools.obtain(Spinor.class);
    Spinor currentAngle = Pools.obtain(Spinor.class);
    Spinor nextAngle = Pools.obtain(Spinor.class);
    // following Spinors used to represent start, end and interpolated ray
    // angles for a given segment
    Spinor startAngle = Pools.obtain(Spinor.class);
    Spinor endAngle = Pools.obtain(Spinor.class);
    Spinor rayAngle = Pools.obtain(Spinor.class);

    int segmentCount = chain.size / 2 - 1;

    segmentAngles.clear();
    segmentLengths.clear();
    float remainingLength = 0;

    for (int i = 0, j = 0; i < chain.size - 2; i += 2, j++) {
      v1.set(chain.items[i + 2], chain.items[i + 3])
          .sub(chain.items[i], chain.items[i + 1]);
      segmentLengths.add(v1.len());
      segmentAngles.add(v1.rotate90(rayDirection).angle() * MathUtils.degreesToRadians);
      remainingLength += segmentLengths.items[j];
    }

    int rayNumber = 0;
    int remainingRays = rayNum;

    for (int i = 0; i < segmentCount; i++) {

      // get this and adjacent segment angles
      previousAngle.set(i == 0 ? segmentAngles.items[i] : segmentAngles.items[i - 1]);
      currentAngle.set(segmentAngles.items[i]);
      nextAngle.set(i == segmentAngles.size - 1 ? segmentAngles.items[i]
          : segmentAngles.items[i + 1]);

      // interpolate to find actual start and end angles
      startAngle.set(previousAngle).slerp(currentAngle, 0.5f);
      endAngle.set(currentAngle).slerp(nextAngle, 0.5f);

      int segmentVertex = i * 2;
      vSegmentStart.set(chain.items[segmentVertex], chain.items[segmentVertex + 1]);
      vDirection.set(chain.items[segmentVertex + 2], chain.items[segmentVertex + 3])
          .sub(vSegmentStart).nor();

      float raySpacing = remainingLength / remainingRays;

      int segmentRays = i == segmentCount - 1 ? remainingRays
          : (int) ((segmentLengths.items[i] / remainingLength) * remainingRays);

      for (int j = 0; j < segmentRays; j++) {
        float position = j * raySpacing;

        // interpolate ray angle based on position within segment
        rayAngle.set(startAngle).slerp(endAngle, position / segmentLengths.items[i]
            );
        float angle = rayAngle.angle();
        vRayOffset.set(this.rayStartOffset, 0).rotateRad(angle);
        v1.set(vDirection).scl(position).add(vSegmentStart).add(vRayOffset);

        this.startX[rayNumber] = v1.x;
        this.startY[rayNumber] = v1.y;
        v2.set(distance, 0).rotateRad(angle).add(v1);
        this.endX[rayNumber] = v2.x;
        this.endY[rayNumber] = v2.y;
        rayNumber++;
      }

      remainingRays -= segmentRays;
      remainingLength -= segmentLengths.items[i];

    }

    Pools.free(v1);
    Pools.free(v2);
    Pools.free(vSegmentStart);
    Pools.free(vDirection);
    Pools.free(vRayOffset);
    Pools.free(previousAngle);
    Pools.free(currentAngle);
    Pools.free(nextAngle);
    Pools.free(startAngle);
    Pools.free(endAngle);
    Pools.free(rayAngle);
    Pools.free(tmpAngle);
  }

  @Override
  public boolean contains(float x, float y) {

    // fast fail
    final float x_d = tmpPosition.x - x;
    final float y_d = tmpPosition.y - y;
    final float dst2 = x_d * x_d + y_d * y_d;
    if (distance * distance <= dst2)
      return false;

    // actual check

    boolean oddNodes = false;
    float x2 = mx[rayNum] = tmpPosition.x;
    float y2 = my[rayNum] = tmpPosition.y;
    float x1, y1;
    for (int i = 0; i <= rayNum; x2 = x1, y2 = y1, ++i) {
      x1 = mx[i];
      y1 = my[i];
      if (((y1 < y) && (y2 >= y)) || (y1 >= y) && (y2 < y)) {
        if ((y - y1) / (y2 - y1) * (x2 - x1) < (x - x1))
          oddNodes = !oddNodes;
      }
    }
    return oddNodes;

  }

  @Override
  public void setDirection(float directionDegree) {
  }

}
