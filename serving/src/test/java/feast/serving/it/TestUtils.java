/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2018-2020 The Feast Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feast.serving.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Duration;
import feast.common.auth.credentials.OAuthCredentials;
import feast.proto.core.CoreServiceGrpc;
import feast.proto.core.CoreServiceGrpc.CoreServiceBlockingStub;
import feast.proto.core.DataSourceProto.DataSource;
import feast.proto.core.EntityProto.Entity;
import feast.proto.core.EntityProto.EntitySpecV2;
import feast.proto.core.FeatureProto.FeatureSpecV2;
import feast.proto.core.FeatureTableProto.FeatureTable;
import feast.proto.core.FeatureTableProto.FeatureTableSpec;
import feast.proto.serving.ServingAPIProto.FeatureReferenceV2;
import feast.proto.serving.ServingAPIProto.GetOnlineFeaturesRequestV2;
import feast.proto.serving.ServingServiceGrpc;
import feast.proto.types.ValueProto;
import io.grpc.CallCredentials;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import java.util.*;
import java.util.stream.Collectors;

public class TestUtils {

  public static ServingServiceGrpc.ServingServiceBlockingStub getServingServiceStub(
      boolean isSecure, int feastServingPort, Map<String, String> options) {
    Channel secureChannel =
        ManagedChannelBuilder.forAddress("localhost", feastServingPort).usePlaintext().build();

    if (isSecure) {
      CallCredentials callCredentials = null;
      callCredentials = new OAuthCredentials(options);
      return ServingServiceGrpc.newBlockingStub(secureChannel).withCallCredentials(callCredentials);
    } else {
      return ServingServiceGrpc.newBlockingStub(secureChannel);
    }
  }

  public static CoreSimpleAPIClient getApiClientForCore(int feastCorePort) {
    Channel channel =
        ManagedChannelBuilder.forAddress("localhost", feastCorePort).usePlaintext().build();

    CoreServiceBlockingStub coreService = CoreServiceGrpc.newBlockingStub(channel);

    return new CoreSimpleAPIClient(coreService);
  }

  public static FeatureTableSpec createFeatureTableSpec(
      String name,
      List<String> entities,
      Map<String, ValueProto.ValueType.Enum> features,
      int maxAgeSecs,
      Map<String, String> labels) {
    return FeatureTableSpec.newBuilder()
        .setName(name)
        .setMaxAge(Duration.newBuilder().setSeconds(maxAgeSecs).build())
        .addAllEntities(entities)
        .addAllFeatures(
            features.entrySet().stream()
                .map(
                    entry ->
                        FeatureSpecV2.newBuilder()
                            .setName(entry.getKey())
                            .setValueType(entry.getValue())
                            .putAllLabels(labels)
                            .build())
                .collect(Collectors.toList()))
        .putAllLabels(labels)
        .build();
  }

  public static DataSource createFileDataSourceSpec(
      String fileURL, String fileFormat, String timestampColumn, String datePartitionColumn) {
    return DataSource.newBuilder()
        .setType(DataSource.SourceType.BATCH_FILE)
        .setFileOptions(
            DataSource.FileOptions.newBuilder()
                .setFileFormat(fileFormat)
                .setFileUrl(fileURL)
                .build())
        .setEventTimestampColumn(timestampColumn)
        .setDatePartitionColumn(datePartitionColumn)
        .build();
  }

  public static GetOnlineFeaturesRequestV2 createOnlineFeatureRequest(
      String projectName,
      List<FeatureReferenceV2> featureReferences,
      List<GetOnlineFeaturesRequestV2.EntityRow> entityRows) {
    return GetOnlineFeaturesRequestV2.newBuilder()
        .setProject(projectName)
        .addAllFeatures(featureReferences)
        .addAllEntityRows(entityRows)
        .build();
  }

  public static void applyFeatureTable(
      CoreSimpleAPIClient secureApiClient,
      String projectName,
      String featureTableName,
      List<String> entities,
      ImmutableMap<String, ValueProto.ValueType.Enum> features,
      int maxAgeSecs) {
    FeatureTableSpec expectedFeatureTableSpec =
        createFeatureTableSpec(
                featureTableName,
                entities,
                new HashMap<>() {
                  {
                    putAll(features);
                  }
                },
                maxAgeSecs,
                ImmutableMap.of("feat_key2", "feat_value2"))
            .toBuilder()
            .setBatchSource(
                createFileDataSourceSpec("file:///path/to/file", "parquet", "ts_col", ""))
            .build();
    secureApiClient.simpleApplyFeatureTable(expectedFeatureTableSpec);
    FeatureTable actualFeatureTable =
        secureApiClient.simpleGetFeatureTable(projectName, featureTableName);
    assertEquals(expectedFeatureTableSpec.getName(), actualFeatureTable.getSpec().getName());
  }

  public static void applyEntity(
      CoreSimpleAPIClient coreApiClient, String projectName, EntitySpecV2 entitySpec) {
    coreApiClient.simpleApplyEntity(entitySpec);
    String entityName = entitySpec.getName();
    Entity actualEntity = coreApiClient.getEntity(projectName, entityName);
    assertEquals(entitySpec.getName(), actualEntity.getSpec().getName());
  }
}
