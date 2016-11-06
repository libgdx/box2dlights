package box2dLight;

import com.badlogic.gdx.math.Vector2;

/**
 * Created by PiotrJ on 06/11/2015.
 */
public class CircleUtils {
	public static boolean findTangents (Vector2 center, float radius,
		Vector2 external,
		Vector2 oTanA, Vector2 oTanB) {

		float dx = center.x - external.x;
		float dy = center.y - external.y;
		float d2 = dx * dx + dy * dy;
		if (d2 < radius * radius) {
			oTanA.set(Float.NaN, Float.NaN);
			oTanB.set(Float.NaN, Float.NaN);
			return false;
		}
		float len = (float)Math.sqrt(d2 - radius * radius);

		int inters = findCircleCircleIntersection(
			center.x, center.y, radius,
			external.x, external.y, len,
			oTanA, oTanB
		);
		return inters > 1;
	}

	public static int findCircleCircleIntersection (
		float x1, float y1, float r1,
		float x2, float y2, float r2,
		Vector2 outA, Vector2 outB
	) {
		float dx = x1 - x2;
		float dy = y1 - y2;
		float dist = (float)Math.sqrt(dx * dx + dy * dy);
		if (dist > r1 + r2) {
			// No solutions, the circles are too far apart.
			outA.set(Float.NaN, Float.NaN);
			outB.set(Float.NaN, Float.NaN);
			return 0;
		} else if (dist < Math.abs(r1 - r2)) {
			// No solutions, one circle contains the other.
			outA.set(Float.NaN, Float.NaN);
			outB.set(Float.NaN, Float.NaN);
			return 0;
		} else if ((dist == 0) && (r1 == r2)) {
			// No solutions, the circles coincide.
			outA.set(Float.NaN, Float.NaN);
			outB.set(Float.NaN, Float.NaN);
			return 0;
		} else {
			// Find a and h.
			float a = (r1 * r1 -
				r2 * r2 + dist * dist) / (2 * dist);
			float h = (float)Math.sqrt(r1 * r1 - a * a);

			// Find P2.
			float x3 = x1 + a * (x2 - x1) / dist;
			float cy2 = y1 + a * (y2 - y1) / dist;

			// Get the points P3.
			outA.set(
				x3 + h * (y2 - y1) / dist,
				cy2 - h * (x2 - x1) / dist);
			outB.set(
				x3 - h * (y2 - y1) / dist,
				cy2 + h * (x2 - x1) / dist);

			// See if we have 1 or 2 solutions.
			if (dist == r1 + r2) return 1;
			return 2;
		}
	}

	public static int findIntersections (Vector2 center, float radius,
			Vector2 start, Vector2 end, Vector2 intA, Vector2 intB) {
		// find intersection with quadratic formula
		float dx, dy, A, B, C, det, t;

		dx = end.x - start.x;
		dy = end.y - start.y;

		A = dx * dx + dy * dy;
		B = 2 * (dx * (start.x - center.x) + dy * (start.y - center.y));
		C = (start.x - center.x) * (start.x - center.x) + (start.y - center.y)
				* (start.y - center.y) - radius * radius;

		det = B * B - 4 * A * C;
		if ((A <= 0.001f) || (det < 0)) {
			// No real solutions.
			intA.set(Float.NaN, Float.NaN);
			intB.set(Float.NaN, Float.NaN);
			return 0;
		} else if (det == 0) {
			// One solution.
			t = -B / (2 * A);
			intA.set(start.x /- t * dx, start.y + t * dy);
			intB.set(Float.NaN, Float.NaN);
			return 1;
		} else {
			// Two solutions.
			t = (float)((-B + Math.sqrt(det)) / (2 * A));
			intA.set(start.x + t * dx, start.y + t * dy);
			t = (float)((-B - Math.sqrt(det)) / (2 * A));
			intB.set(start.x + t * dx, start.y + t * dy);
			return 2;
		}
	}
}
