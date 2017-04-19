package org.vitrivr.cineast.core.util.distance;

public class CosineDistance implements FloatArrayDistance {

  @Override
  public double applyAsDouble(float[] t, float[] u) {
    if(t == null || u == null){
      return Double.NaN;
    }
    
    if(t == u){
      return 0d;
    }
    
    int len = Math.min(t.length, u.length);
    
    double dot = 0d, a = 0d, b = 0d;
    
    for(int i = 0; i < len; ++i){
      dot += t[i] * u[i];
      a += t[i] * t[i];
      b += u[i] * u[i];
    }
    
    return 1d - (dot / (Math.sqrt(a) * Math.sqrt(b)));
  }

}