package box2dLight;

public class RayHandlerOptions {
	boolean gammaCorrection = false;
	boolean isDiffuse = false;

	boolean pseudo3d = false;
	boolean shadowColorInterpolation = false;

	public void setDiffuse (boolean diffuse) {
		isDiffuse = diffuse;
	}

	public void setGammaCorrection (boolean gammaCorrection) {
		this.gammaCorrection = gammaCorrection;
	}

	public void setPseudo3d (boolean pseudo3d) {
		setPseudo3d(pseudo3d, false);
	}

	public void setPseudo3d (boolean pseudo3d, boolean shadowColorInterpolation) {
		this.pseudo3d = pseudo3d;
		this.shadowColorInterpolation = shadowColorInterpolation;
	}
}
