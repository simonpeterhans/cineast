package org.vitrivr.cineast.core.features;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.vitrivr.cineast.core.config.ReadableQueryConfig;
import org.vitrivr.cineast.core.data.FloatVector;
import org.vitrivr.cineast.core.data.FloatVectorImpl;
import org.vitrivr.cineast.core.data.MultiImage;
import org.vitrivr.cineast.core.data.ReadableFloatVector;
import org.vitrivr.cineast.core.data.score.ScoreElement;
import org.vitrivr.cineast.core.data.segments.SegmentContainer;
import org.vitrivr.cineast.core.descriptor.EdgeImg;
import org.vitrivr.cineast.core.features.abstracts.AbstractFeatureModule;
import org.vitrivr.cineast.core.util.ARPartioner;

public class EdgeARP88 extends AbstractFeatureModule {

  private static final Logger LOGGER = LogManager.getLogger();

  public EdgeARP88() {
    super("features_EdgeARP88", 31f / 4f);
  }

  @Override
  public void processShot(SegmentContainer shot) {
    LOGGER.traceEntry();
    if (!phandler.idExists(shot.getId())) {
      persist(shot.getId(), getEdges(shot.getMostRepresentativeFrame().getImage()));
    }
    LOGGER.traceExit();
  }

  private static FloatVector getEdges(MultiImage img) {
    SummaryStatistics[] stats = new SummaryStatistics[64];
    for (int i = 0; i < 64; ++i) {
      stats[i] = new SummaryStatistics();
    }
    List<Boolean> edgePixels = EdgeImg.getEdgePixels(img,
        new ArrayList<Boolean>(img.getWidth() * img.getHeight()));
    ArrayList<LinkedList<Boolean>> partition = ARPartioner.partition(edgePixels, img.getWidth(),
        img.getHeight(), 8, 8);
    for (int i = 0; i < partition.size(); ++i) {
      LinkedList<Boolean> edge = partition.get(i);
      SummaryStatistics stat = stats[i];
      for (boolean b : edge) {
        stat.addValue(b ? 1 : 0);
      }
    }
    float[] f = new float[64];
    for (int i = 0; i < 64; ++i) {
      f[i] = (float) stats[i].getMean();
    }

    return new FloatVectorImpl(f);
  }

  @Override
  public List<ScoreElement> getSimilar(SegmentContainer sc, ReadableQueryConfig qc) {
    FloatVector query = getEdges(sc.getMostRepresentativeFrame().getImage());
    return getSimilar(ReadableFloatVector.toArray(query), qc);
  }
}
