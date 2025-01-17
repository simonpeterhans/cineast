package org.vitrivr.cineast.core.util;

import org.apache.commons.lang3.RandomStringUtils;
import org.vitrivr.cineast.core.config.QueryConfig;
import org.vitrivr.cineast.core.config.ReadableQueryConfig;

/**
 * Util class to generate query IDs which are used to connect with the DB
 */
public class DBQueryIDGenerator {

  public static String generateQueryID() {
    return generateQueryID("");
  }

  /**
   * Generates a queryID with an infix which provides context
   */
  public static String generateQueryID(String infix) {
    return generate(infix, "noqid-" + RandomStringUtils.randomAlphanumeric(3));
  }

  /**
   * Generates a query id with an infix based on an existing query id (e.g. one provided through a {@link QueryConfig}
   *
   * @param infix      infix to use in the queryID
   * @param existingID can be null, in which case {@link #generateQueryID(String)} will be called with the infix
   */
  public static String generateQueryID(String infix, String existingID) {
    return existingID == null ? generateQueryID(infix) : generate(infix, existingID.substring(0, 3));
  }

  /**
   * uses {@link ReadableQueryConfig#getQueryId()} as a basis for postfix
   */
  public static String generateQueryID(String infix, ReadableQueryConfig qc) {
    return qc == null ? generate(infix, "qc-null") : generate(infix, qc.getQueryId().substring(0, 3));
  }

  private static String generate(String infix, String postfix) {
    return "q-cin-" + infix + "-" + postfix;
  }
}
