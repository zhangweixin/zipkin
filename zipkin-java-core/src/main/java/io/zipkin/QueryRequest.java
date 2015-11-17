/**
 * Copyright 2015 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.zipkin;

import io.zipkin.internal.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static io.zipkin.internal.Util.checkArgument;

/**
 * Invoking this request retrieves traces matching the below filters.
 *
 * <p/> Results should be filtered against {@link #endTs}, subject to {@link #limit} and {@link
 * #lookback}. For example, if endTs is 10:20 today, limit is 10, and lookback is 7 days, traces
 * returned should be those nearest to 10:20 today, not 10:20 a week ago.
 *
 * <p/> Time units of {@link #endTs} and {@link #lookback} are milliseconds as opposed to
 * microseconds, the grain of {@link Span#timestamp}. Milliseconds is a more familiar and supported
 * granularity for query, index and windowing functions.
 */
public final class QueryRequest {

  /** Mandatory {@link io.zipkin.Endpoint#serviceName} and constrains. */
  public final String serviceName;

  /** When present, only include traces with this {@link io.zipkin.Span#name} */
  @Nullable
  public final String spanName;

  /**
   * Include traces whose {@link io.zipkin.Span#annotations} include a value in this set.
   *
   * <p/> This is an AND condition against the set, as well against {@link #binaryAnnotations}
   */
  public final List<String> annotations;

  /**
   * Include traces whose {@link io.zipkin.Span#binaryAnnotations} include a String whose key and
   * value are an entry in this set.
   *
   * <p/> This is an AND condition against the set, as well against {@link #annotations}
   */
  public final Map<String, String> binaryAnnotations;

  /**
   * Only return traces whose {@link io.zipkin.Span#duration} is greater than or equal to
   * minDuration microseconds.
   */
  @Nullable
  public final Long minDuration;

  /**
   * Only return traces whose {@link io.zipkin.Span#duration} is less than or equal to maxDuration
   * microseconds. Only valid with {@link #minDuration}.
   */
  @Nullable
  public final Long maxDuration;

  /**
   * Only return traces where all {@link io.zipkin.Span#timestamp} are at or before this time in
   * epoch milliseconds. Defaults to current time.
   */
  public final long endTs;

  /**
   * Only return traces where all {@link io.zipkin.Span#timestamp} are at or after (endTs -
   * lookback) in milliseconds. Defaults to endTs.
   */
  public final long lookback;

  /** Maximum number of traces to return. Defaults to 10 */
  public final int limit;

  private QueryRequest(
      String serviceName,
      String spanName,
      List<String> annotations,
      Map<String, String> binaryAnnotations,
      Long minDuration,
      Long maxDuration,
      long endTs,
      long lookback,
      int limit) {
    checkArgument(serviceName != null && !serviceName.isEmpty(), "serviceName was empty");
    checkArgument(spanName == null || !spanName.isEmpty(), "spanName was empty");
    checkArgument(endTs > 0, "endTs should be positive, in epoch microseconds: was %d", endTs);
    checkArgument(limit > 0, "limit should be positive: was %d", limit);
    this.serviceName = serviceName.toLowerCase();
    this.spanName = spanName != null ? spanName.toLowerCase() : null;
    this.annotations = annotations;
    for (String annotation : annotations) {
      checkArgument(!annotation.isEmpty(), "annotation was empty");
    }
    this.binaryAnnotations = binaryAnnotations;
    for (Map.Entry<String, String> entry : binaryAnnotations.entrySet()) {
      checkArgument(!entry.getKey().isEmpty(), "binary annotation key was empty");
      checkArgument(!entry.getValue().isEmpty(), "binary annotation value was empty");
    }
    this.minDuration = minDuration;
    this.maxDuration = maxDuration;
    this.endTs = endTs;
    this.lookback = lookback;
    this.limit = limit;
  }

  public static final class Builder {
    private String serviceName;
    private String spanName;
    private List<String> annotations = new LinkedList<>();
    private Map<String, String> binaryAnnotations = new LinkedHashMap<>();
    private Long minDuration;
    private Long maxDuration;
    private Long endTs;
    private Long lookback;
    private Integer limit;

    public Builder() {
    }

    public Builder(QueryRequest source) {
      this.serviceName = source.serviceName;
      this.spanName = source.spanName;
      this.annotations = source.annotations;
      this.binaryAnnotations = source.binaryAnnotations;
      this.minDuration = source.minDuration;
      this.maxDuration = source.maxDuration;
      this.endTs = source.endTs;
      this.lookback = source.lookback;
      this.limit = source.limit;
    }

    /** @see QueryRequest#serviceName */
    public Builder serviceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    /** @see QueryRequest#spanName */
    public Builder spanName(@Nullable String spanName) {
      this.spanName = spanName;
      return this;
    }

    /** @see QueryRequest#annotations */
    public Builder addAnnotation(String annotation) {
      this.annotations.add(annotation);
      return this;
    }

    /** @see QueryRequest#binaryAnnotations */
    public Builder addBinaryAnnotation(String key, String value) {
      this.binaryAnnotations.put(key, value);
      return this;
    }

    /** @see QueryRequest#minDuration */
    public Builder minDuration(Long minDuration) {
      this.minDuration = minDuration;
      return this;
    }

    /** @see QueryRequest#maxDuration */
    public Builder maxDuration(Long maxDuration) {
      this.maxDuration = maxDuration;
      return this;
    }

    /** @see QueryRequest#endTs */
    public Builder endTs(Long endTs) {
      this.endTs = endTs;
      return this;
    }

    /** @see QueryRequest#lookback */
    public Builder lookback(Long lookback) {
      this.lookback = lookback;
      return this;
    }

    /** @see QueryRequest#limit */
    public Builder limit(Integer limit) {
      this.limit = limit;
      return this;
    }

    public QueryRequest build() {
      long selectedEndTs = endTs == null ? System.currentTimeMillis() * 1000 : endTs;
      return new QueryRequest(
          serviceName,
          spanName,
          annotations,
          binaryAnnotations,
          minDuration,
          maxDuration,
          selectedEndTs,
          Math.min(lookback == null ? selectedEndTs : lookback, selectedEndTs),
          limit == null ? 10 : limit);
    }
  }

  @Override
  public String toString() {
    return "QueryRequest{"
        + "serviceName=" + serviceName + ", "
        + "spanName=" + spanName + ", "
        + "annotations=" + annotations + ", "
        + "binaryAnnotations=" + binaryAnnotations + ", "
        + "minDuration=" + minDuration + ", "
        + "maxDuration=" + maxDuration + ", "
        + "endTs=" + endTs + ", "
        + "lookback=" + lookback + ", "
        + "limit=" + limit
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof QueryRequest) {
      QueryRequest that = (QueryRequest) o;
      return (this.serviceName.equals(that.serviceName))
          && ((this.spanName == null) ? (that.spanName == null) : this.spanName.equals(that.spanName))
          && ((this.annotations == null) ? (that.annotations == null) : this.annotations.equals(that.annotations))
          && ((this.binaryAnnotations == null) ? (that.binaryAnnotations == null) : this.binaryAnnotations.equals(that.binaryAnnotations))
          && ((this.minDuration == null) ? (that.minDuration == null) : this.minDuration.equals(that.minDuration))
          && ((this.maxDuration == null) ? (that.maxDuration == null) : this.maxDuration.equals(that.maxDuration))
          && (this.endTs == that.endTs)
          && (this.lookback == that.lookback)
          && (this.limit == that.limit);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= serviceName.hashCode();
    h *= 1000003;
    h ^= (spanName == null) ? 0 : spanName.hashCode();
    h *= 1000003;
    h ^= (annotations == null) ? 0 : annotations.hashCode();
    h *= 1000003;
    h ^= (binaryAnnotations == null) ? 0 : binaryAnnotations.hashCode();
    h *= 1000003;
    h ^= (minDuration == null) ? 0 : minDuration.hashCode();
    h *= 1000003;
    h ^= (maxDuration == null) ? 0 : maxDuration.hashCode();
    h *= 1000003;
    h ^= (endTs >>> 32) ^ endTs;
    h *= 1000003;
    h ^= (lookback >>> 32) ^ lookback;
    h *= 1000003;
    h ^= limit;
    return h;
  }
}
