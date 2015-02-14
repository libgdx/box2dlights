package box2dLight.p3d;

import com.badlogic.gdx.physics.box2d.Fixture;

/**
 * Data container for linking fixtures with pseudo3d light engine
 * 
 * @author rinold
 */
public class P3dData {
	
	public Object userData = null;
	
	public float height;
	
	public P3dData(float h) {
		height = h;
	}
	
	public P3dData(Object data, float h) {
		height = h;
		userData = data;
	}
	
	public float getLimit(float distance, float lightHeight, float lightRange) {
		float l = 0f;
		if (lightHeight > height) {
			l = distance * height / (lightHeight - height);
			float diff = lightRange - distance;
			if (l > diff) l = diff;
		} else if (lightHeight == 0f) {
			l = lightRange;
		} else {
			l = lightRange - distance;
		}
		
		return l > 0 ? l : 0f;
	}
	
	public static Object getUserData(Fixture fixture) {
		Object data = fixture.getUserData();
		if (data instanceof P3dData) {
			return ((P3dData)data).userData;
		} else {
			return data;
		}
	}
	
}
