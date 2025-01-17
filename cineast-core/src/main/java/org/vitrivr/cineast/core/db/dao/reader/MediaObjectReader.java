package org.vitrivr.cineast.core.db.dao.reader;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.vitrivr.cineast.core.data.MediaType;
import org.vitrivr.cineast.core.data.entities.MediaObjectDescriptor;
import org.vitrivr.cineast.core.data.providers.primitive.PrimitiveTypeProvider;
import org.vitrivr.cineast.core.data.providers.primitive.ProviderDataType;
import org.vitrivr.cineast.core.data.providers.primitive.StringTypeProvider;
import org.vitrivr.cineast.core.db.DBSelector;
import org.vitrivr.cineast.core.util.DBQueryIDGenerator;

public class MediaObjectReader extends AbstractEntityReader {

  private static final Logger LOGGER = LogManager.getLogger();

  private static final Cache<String, MediaObjectDescriptor> objectCache = CacheBuilder.newBuilder()
      .maximumSize(100_000)
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .build(); //TODO make configurable

  /**
   * Constructor for MediaObjectReader
   *
   * @param selector DBSelector to use for the MediaObjectMetadataReader instance.
   */
  public MediaObjectReader(DBSelector selector) {
    super(selector);
    this.selector.open(MediaObjectDescriptor.ENTITY);
  }

  public MediaObjectDescriptor lookUpObjectById(String objectId) {
    List<Map<String, PrimitiveTypeProvider>> result = selector.getRows(MediaObjectDescriptor.FIELDNAMES[0], new StringTypeProvider(objectId));

    if (result.isEmpty()) {
      return new MediaObjectDescriptor();
    }

    return mapToDescriptor(result.get(0));
  }

  private MediaObjectDescriptor mapToDescriptor(Map<String, PrimitiveTypeProvider> map) {
    PrimitiveTypeProvider idProvider = map.get(MediaObjectDescriptor.FIELDNAMES[0]);
    PrimitiveTypeProvider typeProvider = map.get(MediaObjectDescriptor.FIELDNAMES[1]);
    PrimitiveTypeProvider nameProvider = map.get(MediaObjectDescriptor.FIELDNAMES[2]);
    PrimitiveTypeProvider pathProvider = map.get(MediaObjectDescriptor.FIELDNAMES[3]);

    if (!checkProvider(MediaObjectDescriptor.FIELDNAMES[0], idProvider, ProviderDataType.STRING)) {
      return new MediaObjectDescriptor();
    }

    if (!checkProvider(MediaObjectDescriptor.FIELDNAMES[1], typeProvider, ProviderDataType.INT)) {
      return new MediaObjectDescriptor();
    }

    if (!checkProvider(MediaObjectDescriptor.FIELDNAMES[2], nameProvider, ProviderDataType.STRING)) {
      return new MediaObjectDescriptor();
    }

    if (!checkProvider(MediaObjectDescriptor.FIELDNAMES[3], pathProvider, ProviderDataType.STRING)) {
      return new MediaObjectDescriptor();
    }

    MediaObjectDescriptor descriptor = new MediaObjectDescriptor(idProvider.getString(), nameProvider.getString(), pathProvider.getString(), MediaType.fromId(typeProvider.getInt()), true);

    objectCache.put(descriptor.getObjectId(), descriptor);

    return descriptor;

  }

  private boolean checkProvider(String name, PrimitiveTypeProvider provider,
      ProviderDataType expectedType) {
    if (provider == null) {
      LOGGER.error("no {} in multimedia object", name);
      return false;
    }

    if (provider.getType() != expectedType) {
      LOGGER.error("invalid data type for field {} in multimedia object, expected {}, got {}", name,
          expectedType, provider.getType());
      return false;
    }
    return true;
  }

  public MediaObjectDescriptor lookUpObjectByName(String name) {

    List<Map<String, PrimitiveTypeProvider>> result = selector.getRows(MediaObjectDescriptor.FIELDNAMES[2], new StringTypeProvider(name));

    if (result.isEmpty()) {
      return new MediaObjectDescriptor();
    }

    return mapToDescriptor(result.get(0));
  }

  public MediaObjectDescriptor lookUpObjectByPath(String path) {
    List<Map<String, PrimitiveTypeProvider>> result = selector.getRows(MediaObjectDescriptor.FIELDNAMES[3], new StringTypeProvider(path));

    if (result.isEmpty()) {
      return new MediaObjectDescriptor();
    }

    return mapToDescriptor(result.get(0));
  }

  public Map<String, MediaObjectDescriptor> lookUpObjects(Iterable<String> videoIds, String queryID) {
    if (videoIds == null) {
      return new HashMap<>();
    }

    ArrayList<String> notCached = new ArrayList<>();
    HashMap<String, MediaObjectDescriptor> _return = new HashMap<>();

    videoIds.forEach(id -> {
      MediaObjectDescriptor cached = objectCache.getIfPresent(id);
      if (cached != null) {
        _return.put(cached.getObjectId(), cached);
      } else {
        notCached.add(id);
      }
    });

    if (!notCached.isEmpty()) {

      String dbQueryID = DBQueryIDGenerator.generateQueryID("load-obj", queryID);

      List<Map<String, PrimitiveTypeProvider>> results = selector.getRows(MediaObjectDescriptor.FIELDNAMES[0], Lists.newArrayList(videoIds), dbQueryID);
      results.forEach(el -> {
        MediaObjectDescriptor d = mapToDescriptor(el);
        _return.put(d.getObjectId(), d);
      });

    }
    return _return;
  }

  public Map<String, MediaObjectDescriptor> lookUpObjects(Iterable<String> videoIds) {
    return lookUpObjects(videoIds, null);
  }

  public List<MediaObjectDescriptor> getAllObjects() {
    List<Map<String, PrimitiveTypeProvider>> all = selector.getAll();
    List<MediaObjectDescriptor> _return = new ArrayList<>(all.size());
    for (Map<String, PrimitiveTypeProvider> map : all) {
      _return.add(mapToDescriptor(map));
    }
    return _return;
  }

  /**
   * SELECT * from mediaobjects ORDER BY id ASC LIMIT limit SKIP skip
   *
   * @param skip  how many objects should be skipped
   * @param limit how many objects should be fetched
   * @return descriptors
   */
  public List<MediaObjectDescriptor> getAllObjects(int skip, int limit) {
    List<Map<String, PrimitiveTypeProvider>> all = selector.getAll(MediaObjectDescriptor.FIELDNAMES[0], skip, limit);
    List<MediaObjectDescriptor> _return = new ArrayList<>(all.size());
    for (Map<String, PrimitiveTypeProvider> map : all) {
      _return.add(mapToDescriptor(map));
    }
    return _return;
  }

}
