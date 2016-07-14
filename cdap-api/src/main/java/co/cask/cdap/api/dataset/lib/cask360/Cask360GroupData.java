/*
 * Copyright © 2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package co.cask.cdap.api.dataset.lib.cask360;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.lib.cask360.Cask360Group.Cask360GroupMeta;
import co.cask.cdap.api.dataset.lib.cask360.Cask360Group.Cask360GroupType;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The actual data within a single {@link Cask360Group} within a single
 * {@link Cask360Entity}.
 * <p>
 * Implemented as a concrete class but mimics much of the behavior of an
 * abstract class, with two other implementations of the
 * {@link Cask360GroupDataSpec}, the {@link Cask360GroupDataMap} and the
 * {@link Cask360GroupDataMap}.
 * <p>
 * For every {@link Cask360GroupType}, there will be a member variable in this
 * class and a class implementing {@link Cask360GroupDataSpec} for that type.
 * <p>
 * For any given instance of this class, only one member variable of the classes
 * that implement the spec will be set, all others will be null. This will
 * always line up with the type stored.
 */
public class Cask360GroupData implements Cask360GroupDataSpec, Comparable<Cask360GroupData> {

  private static final Gson gson = Cask360Entity.getGson();

  private Cask360GroupType type;

  private Cask360GroupDataMap map;

  private Cask360GroupDataTime time;

  private Cask360GroupData(Cask360GroupType type, Cask360GroupDataMap map, Cask360GroupDataTime time) {
    if (((type == Cask360GroupType.MAP) && ((map == null) || (time != null)))
        || ((type == Cask360GroupType.TIME) && ((time == null) || (map != null)))) {
      throw new IllegalArgumentException("Invalid combination of parameters: " + "type=(" + type + ") map=("
          + (map == null ? "null" : map.toString()) + ") time=(" + (time == null ? "null" : time.toString()) + ")");
    }
    this.type = type;
    this.map = map;
    this.time = time;
  }

  /**
   * Constructs an empty group data of the specified type.
   * @param type
   */
  public Cask360GroupData(Cask360GroupType type) {
    this(type, type == Cask360GroupType.MAP ? new Cask360GroupDataMap() : null,
        type == Cask360GroupType.TIME ? new Cask360GroupDataTime() : null);
  }

  /**
   * Constructs a group data of map type with the specified map data.
   * @param type
   * @param map
   */
  public Cask360GroupData(Cask360GroupType type, Cask360GroupDataMap map) {
    this(type, map, null);
  }

  /**
   * Constructs a group data of time type with the specified time data.
   * @param type
   * @param time
   */
  public Cask360GroupData(Cask360GroupType type, Cask360GroupDataTime time) {
    this(type, null, time);
  }

  @Override
  public Cask360GroupType getType() {
    return this.type;
  }

  public Cask360GroupDataMap getDataAsMap() {
    return this.map;
  }

  public Cask360GroupDataTime getDataAsTime() {
    return this.time;
  }

  public Cask360GroupDataSpec getData() {
    switch (this.type) {
    case MAP:
      return this.map;
    case TIME:
      return this.time;
    default:
      return null;
    }
  }

  public static Cask360GroupData fromJson(JsonObject json) {
    String typeString = json.get("type").getAsString();
    if (typeString.equals("map")) {
      Cask360GroupDataMap data = new Cask360GroupDataMap();
      data.readJson(json.get("data"));
      return new Cask360GroupData(Cask360GroupType.MAP, data);
    } else if (typeString.equals("time")) {
      Cask360GroupDataTime data = new Cask360GroupDataTime();
      data.readJson(json.get("data"));
      return new Cask360GroupData(Cask360GroupType.TIME, data);
    } else {
      throw new IllegalArgumentException("Invalid group type (" + typeString + ")");
    }
  }

  public static Iterator<Cask360Record> newRecordIterator(String id, Cask360GroupMeta meta, byte[] column,
      byte[] value) {
    List<Cask360Record> records = new LinkedList<Cask360Record>();
    String name = Bytes.toString(meta.getName());
    switch (meta.getType()) {
      case MAP: {
        String key = Bytes.toString(column, Bytes.SIZEOF_SHORT, column.length - Bytes.SIZEOF_SHORT);
        records.add(new Cask360Record(id, name, key, Bytes.toString(value)));
        break;
      }
      case TIME: {
        Long time = Cask360GroupData.Cask360GroupDataTime.fromBytesColumn(column);
        Map<String, String> data = Cask360GroupData.Cask360GroupDataTime.fromBytesValue(value);
        for (Map.Entry<String, String> entry : data.entrySet()) {
          records.add(new Cask360Record(id, name, time, entry.getKey(), entry.getValue()));
        }
        break;
      }
    }
    return records.iterator();
  }

  @Override
  public int compareTo(Cask360GroupData o) {
    if (this.type.compareTo(o.getType()) != 0) {
      return this.type.compareTo(o.getType());
    }
    switch (this.type) {
    case MAP:
      return this.map.compareTo(o.getDataAsMap());
    case TIME:
      return this.time.compareTo(o.getDataAsTime());
    default:
      return 0;
    }
  }

  @Override
  public Map<byte[], byte[]> getBytesMap(byte[] prefix) {
    switch (this.type) {
    case MAP:
      return this.map.getBytesMap(prefix);
    case TIME:
      return this.time.getBytesMap(prefix);
    default:
      return null;
    }
  }

  @Override
  public void put(Cask360GroupData data) {
    switch (this.type) {
    case MAP:
      this.map.put(data);
      return;
    case TIME:
      this.time.put(data);
      return;
    }
  }

  @Override
  public void put(byte[] column, byte[] value) {
    switch (this.type) {
    case MAP:
      this.map.put(column, value);
      return;
    case TIME:
      this.time.put(column, value);
      return;
    }
  }

  @Override
  public void readJson(JsonElement json) {
    JsonObject obj = json.getAsJsonObject();
    Cask360GroupType type = Cask360GroupType.fromJsonName(obj.get("type").getAsString());
    switch (type) {
      case MAP: {
        this.type = type;
        this.map = new Cask360GroupDataMap();
        this.map.readJson(obj.get("data"));
        break;
      }
      case TIME: {
        this.type = type;
        this.time = new Cask360GroupDataTime();
        this.time.readJson(obj.get("data"));
        break;
      }
      default: {
        throw new IllegalArgumentException("Invalid group type (" + type + ")");
      }
    }
  }

  @Override
  public JsonElement toJson() {
    JsonObject outer = new JsonObject();
    outer.addProperty("type", this.type.toJsonName());
    JsonElement data = null;
    switch (this.type) {
    case MAP:
      data = this.map.toJson();
      break;
    case TIME:
      data = this.time.toJson();
      break;
    }
    outer.add("data", data);
    return outer;
  }

  /**
   * Group data for MAP which implements an ascending order map of string to
   * string.
   * <p>
   * Data Model:
   *
   * <pre>
   * AscendingMap<String, String>
   * </pre>
   */
  public static class Cask360GroupDataMap
  implements Cask360GroupDataSpec, Comparable<Cask360GroupDataMap> {

    private Map<String, String> data;

    public Cask360GroupDataMap() {
      this(new TreeMap<String, String>());
    }

    public Cask360GroupDataMap(Map<String, String> data) {
      this.data = data;
    }

    public void put(String key, String value) {
      this.data.put(key, value);
    }

    public void putAll(Map<String, String> data) {
      this.data.putAll(data);
    }

    public void put(Cask360GroupDataMap data) {
      putAll(data.getData());
    }

    @Override
    public void put(Cask360GroupData data) {
      put(data.getDataAsMap());
    }

    @Override
    public void put(byte[] column, byte[] value) {
      String key = Bytes.toString(column, Bytes.SIZEOF_SHORT, column.length - Bytes.SIZEOF_SHORT);
      String val = Bytes.toString(value);
      put(key, val);
    }

    @Override
    public Cask360GroupType getType() {
      return Cask360GroupType.MAP;
    }

    public Map<String, String> getData() {
      return this.data;
    }

    @Override
    public Map<byte[], byte[]> getBytesMap(byte[] prefix) {
      Map<byte[], byte[]> map = new TreeMap<byte[], byte[]>(Bytes.BYTES_COMPARATOR);
      for (Map.Entry<String, String> entry : this.data.entrySet()) {
        map.put(Bytes.add(prefix, Bytes.toBytes(entry.getKey())), Bytes.toBytes(entry.getValue()));
      }
      return map;
    }

    @Override
    public int compareTo(Cask360GroupDataMap other) {
      if (this.getType() != other.getType()) {
        return this.getType().compareTo(other.getType());
      }
      Cask360GroupDataMap otherMap = other;
      if (data.size() != otherMap.getData().size()) {
        return data.size() < otherMap.getData().size() ? -1 : 1;
      }
      for (Map.Entry<String, String> entry : this.data.entrySet()) {
        String otherValue = otherMap.getData().get(entry.getKey());
        if (otherValue == null) {
          return 1;
        }
        int ret = entry.getValue().compareTo(otherValue);
        if (ret != 0) {
          return ret;
        }
      }
      return 0;
    }

    @Override
    public JsonElement toJson() {
      JsonObject obj = new JsonObject();
      for (Map.Entry<String, String> entry : this.data.entrySet()) {
        obj.addProperty(entry.getKey(), entry.getValue());
      }
      return obj;
    }

    @Override
    public void readJson(JsonElement json) {
      JsonObject data = json.getAsJsonObject();
      for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue().getAsString();
        this.data.put(key, value);
      }
    }
  }

  /**
   * Group data for TIME which implements a descending order (most recent first)
   * map of long to map of string to string.
   * <p>
   * Data Model:
   *
   * <pre>
   * DescendingMap<Long, AscendingMap<String, String>>
   * </pre>
   */
  public static class Cask360GroupDataTime
  implements Cask360GroupDataSpec, Comparable<Cask360GroupDataTime> {

    private Map<Long, Map<String, String>> data;

    /**
     * Constructs a new instance of a time-series group data. Creates a new
     * descending order sorted map of long to string.
     */
    public Cask360GroupDataTime() {
      this(new TreeMap<Long, Map<String, String>>().descendingMap());
    }

    /**
     * Constructs a new instance of a time-series group data using the specified
     * map of data. The specified map should be in descending order.
     *
     * @param data
     *          descending order sorted map of long to string
     */
    public Cask360GroupDataTime(Map<Long, Map<String, String>> data) {
      this.data = data;
    }

    /**
     * Adds data with the specified time, key, and value.
     * <p>
     * If data already exists for this time, it is replaced with the specified
     * key and value only.
     *
     * @param time
     * @param key
     * @param value
     */
    public void put(Long time, String key, String value) {
      Map<String, String> map = new TreeMap<String, String>();
      map.put(key, value);
      this.data.put(time, map);
    }

    /**
     * Adds data with the specified time and map of keys and values.
     * <p>
     * If data already exists for this time, it is replaced with the specified
     * data.
     *
     * @param time
     * @param data
     */
    public void put(Long time, Map<String, String> data) {
      Map<String, String> map = new TreeMap<String, String>();
      map.putAll(data);
      this.data.put(time, map);
    }

    /**
     * Adds data with the specified times and maps of keys and values.
     * <p>
     * If data already exists for any of the specified times, it is replaced
     * with the specified data.
     *
     * @param data
     */
    public void putAll(Map<Long, Map<String, String>> data) {
      for (Map.Entry<Long, Map<String, String>> entry : data.entrySet()) {
        put(entry.getKey(), entry.getValue());
      }
    }

    /**
     * Adds data with the specified times and maps of keys and values.
     * <p>
     * If data already exists for any of the specified times, it is replaced
     * with the specified data.
     *
     * @param data
     */
    public void put(Cask360GroupDataTime data) {
      putAll(data.getData());
    }

    @Override
    public void put(Cask360GroupData data) {
      put(data.getDataAsTime());
    }

    @Override
    public void put(byte[] column, byte[] value) {
      Long key = Cask360GroupDataTime.fromBytesColumn(column);
      Map<String, String> data = Cask360GroupDataTime.fromBytesValue(value);
      put(key, data);
    }

    @Override
    public Cask360GroupType getType() {
      return Cask360GroupType.TIME;
    }

    public Map<Long, Map<String, String>> getData() {
      return this.data;
    }

    @Override
    public Map<byte[], byte[]> getBytesMap(byte[] prefix) {
      Map<byte[], byte[]> map = new TreeMap<byte[], byte[]>(Bytes.BYTES_COMPARATOR);
      for (Map.Entry<Long, Map<String, String>> timeEntry : this.data.entrySet()) {
        byte[] column = Cask360GroupDataTime.toBytesColumn(prefix, timeEntry.getKey());
        byte[] value = Cask360GroupDataTime.toBytesValue(timeEntry.getValue());
        map.put(column, value);
      }
      return map;
    }

    private static byte[] toBytesColumn(byte[] prefix, Long time) {
      Long reversed = Long.MAX_VALUE - time;
      return Bytes.add(prefix, Bytes.toBytes(reversed));
    }

    private static byte[] toBytesValue(Map<String, String> data) {
      return Bytes.toBytes(gson.toJson(data, new TypeToken<Map<String, String>>() {
      }.getType()));
    }

    private static Long fromBytesColumn(byte[] column) {
      Long reversed = Bytes.toLong(column, Bytes.SIZEOF_SHORT, Bytes.SIZEOF_LONG);
      return Long.MAX_VALUE - reversed;
    }

    private static Map<String, String> fromBytesValue(byte[] value) {
      return gson.fromJson(Bytes.toString(value), new TypeToken<Map<String, String>>() {
      }.getType());
    }

    @Override
    public int compareTo(Cask360GroupDataTime other) {
      if (this.getType() != other.getType()) {
        return this.getType().compareTo(other.getType());
      }
      Cask360GroupDataTime otherTime = other;
      if (data.size() != otherTime.getData().size()) {
        return data.size() < otherTime.getData().size() ? -1 : 1;
      }
      for (Map.Entry<Long, Map<String, String>> entry : this.data.entrySet()) {
        Map<String, String> otherMap = otherTime.getData().get(entry.getKey());
        if (otherMap == null) {
          return 1;
        }
        Map<String, String> map = entry.getValue();
        if (map.size() != otherMap.size()) {
          return map.size() < otherMap.size() ? -1 : 1;
        }
        for (Map.Entry<String, String> innerMapEntry : map.entrySet()) {
          String otherValue = otherMap.get(innerMapEntry.getKey());
          if (otherValue == null) {
            return 1;
          }
          int ret = innerMapEntry.getValue().compareTo(otherValue);
          if (ret != 0) {
            return ret;
          }
        }
      }
      return 0;
    }

    @Override
    public JsonElement toJson() {
      JsonArray arr = new JsonArray();
      for (Map.Entry<Long, Map<String, String>> entry : this.data.entrySet()) {
        JsonObject obj = new JsonObject();
        obj.addProperty("time", entry.getKey());
        JsonObject data = new JsonObject();
        for (Map.Entry<String, String> inner : entry.getValue().entrySet()) {
          data.addProperty(inner.getKey(), inner.getValue());
        }
        obj.add("data", data);
        arr.add(obj);
      }
      return arr;
    }

    @Override
    public void readJson(JsonElement json) {
      JsonArray arr = json.getAsJsonArray();
      int size = arr.size();
      for (int i = 0; i < size; i++) {
        JsonObject obj = arr.get(i).getAsJsonObject();
        Long time = obj.get("time").getAsLong();
        JsonObject data = obj.get("data").getAsJsonObject();
        Map<String, String> map = new TreeMap<String, String>();
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
          map.put(entry.getKey(), entry.getValue().getAsString());
        }
        this.data.put(time, map);
      }
    }
  }
}