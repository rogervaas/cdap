/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.templates.etl.batch;

import co.cask.cdap.api.data.batch.Split;
import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.mapreduce.MapReduceContext;
import co.cask.cdap.templates.etl.api.StageSpecification;
import co.cask.cdap.templates.etl.api.batch.BatchSourceContext;
import co.cask.cdap.templates.etl.common.Constants;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * MapReduce Source Context.
 */
public class MapReduceSourceContext extends MapReduceBatchContext implements BatchSourceContext {
  private static final JsonParser JSON_PARSER = new JsonParser();
  private static final Gson GSON = new Gson();
  private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() { }.getType();

  public MapReduceSourceContext(MapReduceContext context, StageSpecification specification) {
    super(context, specification);
  }

  @Override
  public void setInput(String datasetName) {
    mrContext.setInput(datasetName);
  }

  @Override
  public void setInput(String datasetName, List<Split> splits) {
    mrContext.setInput(datasetName, splits);
  }

  @Override
  public void setInput(String datasetName, Dataset dataset) {
    mrContext.setInput(datasetName, dataset);
  }

  @Override
  public Map<String, String> getRuntimeArguments() {
    JsonObject configObject = JSON_PARSER.parse(
      mrContext.getRuntimeArguments().get(Constants.CONFIG_KEY)).getAsJsonObject();
    JsonObject sourceObject = configObject.getAsJsonObject(Constants.SOURCE_KEY);
    JsonObject properties = sourceObject.getAsJsonObject(Constants.PROPERTIES_KEY);
    return GSON.fromJson(properties, STRING_MAP_TYPE);
  }
}
