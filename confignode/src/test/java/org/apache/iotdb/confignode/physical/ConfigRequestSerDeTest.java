/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.confignode.physical;

import org.apache.iotdb.common.rpc.thrift.TConsensusGroupId;
import org.apache.iotdb.common.rpc.thrift.TConsensusGroupType;
import org.apache.iotdb.common.rpc.thrift.TDataNodeLocation;
import org.apache.iotdb.common.rpc.thrift.TEndPoint;
import org.apache.iotdb.common.rpc.thrift.TRegionReplicaSet;
import org.apache.iotdb.common.rpc.thrift.TSeriesPartitionSlot;
import org.apache.iotdb.common.rpc.thrift.TTimePartitionSlot;
import org.apache.iotdb.confignode.consensus.request.ConfigRequest;
import org.apache.iotdb.confignode.consensus.request.ConfigRequestType;
import org.apache.iotdb.confignode.consensus.request.auth.AuthorReq;
import org.apache.iotdb.confignode.consensus.request.read.GetOrCreateDataPartitionReq;
import org.apache.iotdb.confignode.consensus.request.read.GetOrCreateSchemaPartitionReq;
import org.apache.iotdb.confignode.consensus.request.read.QueryDataNodeInfoReq;
import org.apache.iotdb.confignode.consensus.request.write.CreateDataPartitionReq;
import org.apache.iotdb.confignode.consensus.request.write.CreateRegionsReq;
import org.apache.iotdb.confignode.consensus.request.write.CreateSchemaPartitionReq;
import org.apache.iotdb.confignode.consensus.request.write.RegisterDataNodeReq;
import org.apache.iotdb.confignode.consensus.request.write.SetStorageGroupReq;
import org.apache.iotdb.confignode.rpc.thrift.TStorageGroupSchema;
import org.apache.iotdb.db.auth.AuthException;
import org.apache.iotdb.db.auth.entity.PrivilegeType;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigRequestSerDeTest {

  private final ByteBuffer buffer = ByteBuffer.allocate(10240);

  @After
  public void cleanBuffer() {
    buffer.clear();
  }

  @Test
  public void RegisterDataNodePlanTest() throws IOException {
    TDataNodeLocation dataNodeLocation = new TDataNodeLocation();
    dataNodeLocation.setDataNodeId(1);
    dataNodeLocation.setExternalEndPoint(new TEndPoint("0.0.0.0", 6667));
    dataNodeLocation.setInternalEndPoint(new TEndPoint("0.0.0.0", 9003));
    dataNodeLocation.setDataBlockManagerEndPoint(new TEndPoint("0.0.0.0", 8777));
    dataNodeLocation.setConsensusEndPoint(new TEndPoint("0.0.0.0", 7777));
    RegisterDataNodeReq plan0 = new RegisterDataNodeReq(dataNodeLocation);
    plan0.serialize(buffer);
    buffer.flip();
    RegisterDataNodeReq plan1 = (RegisterDataNodeReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
  }

  @Test
  public void QueryDataNodeInfoPlanTest() throws IOException {
    QueryDataNodeInfoReq plan0 = new QueryDataNodeInfoReq(-1);
    plan0.serialize(buffer);
    buffer.flip();
    QueryDataNodeInfoReq plan1 = (QueryDataNodeInfoReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
  }

  @Test
  public void SetStorageGroupPlanTest() throws IOException {
    SetStorageGroupReq plan0 =
        new SetStorageGroupReq(
            new TStorageGroupSchema()
                .setName("sg")
                .setTTL(Long.MAX_VALUE)
                .setSchemaReplicationFactor(3)
                .setDataReplicationFactor(3)
                .setTimePartitionInterval(604800)
                .setSchemaRegionGroupIds(new ArrayList<>())
                .setDataRegionGroupIds(new ArrayList<>()));
    plan0.serialize(buffer);
    buffer.flip();
    SetStorageGroupReq plan1 = (SetStorageGroupReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
  }

  @Test
  public void DeleteStorageGroupPlanTest() {
    // TODO: Add serialize and deserialize test
  }

  @Test
  public void CreateRegionsPlanTest() throws IOException {
    TDataNodeLocation dataNodeLocation = new TDataNodeLocation();
    dataNodeLocation.setDataNodeId(0);
    dataNodeLocation.setExternalEndPoint(new TEndPoint("0.0.0.0", 6667));
    dataNodeLocation.setInternalEndPoint(new TEndPoint("0.0.0.0", 9003));
    dataNodeLocation.setDataBlockManagerEndPoint(new TEndPoint("0.0.0.0", 8777));
    dataNodeLocation.setConsensusEndPoint(new TEndPoint("0.0.0.0", 40010));

    CreateRegionsReq plan0 = new CreateRegionsReq();
    plan0.setStorageGroup("sg");
    TRegionReplicaSet dataRegionSet = new TRegionReplicaSet();
    dataRegionSet.setRegionId(new TConsensusGroupId(TConsensusGroupType.DataRegion, 0));
    dataRegionSet.setDataNodeLocations(Collections.singletonList(dataNodeLocation));
    plan0.addRegion(dataRegionSet);

    TRegionReplicaSet schemaRegionSet = new TRegionReplicaSet();
    schemaRegionSet.setRegionId(new TConsensusGroupId(TConsensusGroupType.SchemaRegion, 1));
    schemaRegionSet.setDataNodeLocations(Collections.singletonList(dataNodeLocation));
    plan0.addRegion(schemaRegionSet);

    plan0.serialize(buffer);
    buffer.flip();
    CreateRegionsReq plan1 = (CreateRegionsReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
  }

  @Test
  public void CreateSchemaPartitionPlanTest() throws IOException {
    TDataNodeLocation dataNodeLocation = new TDataNodeLocation();
    dataNodeLocation.setDataNodeId(0);
    dataNodeLocation.setExternalEndPoint(new TEndPoint("0.0.0.0", 6667));
    dataNodeLocation.setInternalEndPoint(new TEndPoint("0.0.0.0", 9003));
    dataNodeLocation.setDataBlockManagerEndPoint(new TEndPoint("0.0.0.0", 8777));
    dataNodeLocation.setConsensusEndPoint(new TEndPoint("0.0.0.0", 40010));

    String storageGroup = "root.sg0";
    TSeriesPartitionSlot seriesPartitionSlot = new TSeriesPartitionSlot(10);
    TRegionReplicaSet regionReplicaSet = new TRegionReplicaSet();
    regionReplicaSet.setRegionId(new TConsensusGroupId(TConsensusGroupType.SchemaRegion, 0));
    regionReplicaSet.setDataNodeLocations(Collections.singletonList(dataNodeLocation));

    Map<String, Map<TSeriesPartitionSlot, TRegionReplicaSet>> assignedSchemaPartition =
        new HashMap<>();
    assignedSchemaPartition.put(storageGroup, new HashMap<>());
    assignedSchemaPartition.get(storageGroup).put(seriesPartitionSlot, regionReplicaSet);

    CreateSchemaPartitionReq plan0 = new CreateSchemaPartitionReq();
    plan0.setAssignedSchemaPartition(assignedSchemaPartition);
    plan0.serialize(buffer);
    buffer.flip();
    CreateSchemaPartitionReq plan1 =
        (CreateSchemaPartitionReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
  }

  @Test
  public void GetOrCreateSchemaPartitionPlanTest() throws IOException {
    String storageGroup = "root.sg0";
    TSeriesPartitionSlot seriesPartitionSlot = new TSeriesPartitionSlot(10);

    Map<String, List<TSeriesPartitionSlot>> partitionSlotsMap = new HashMap<>();
    partitionSlotsMap.put(storageGroup, Collections.singletonList(seriesPartitionSlot));

    GetOrCreateSchemaPartitionReq plan0 =
        new GetOrCreateSchemaPartitionReq(ConfigRequestType.GetOrCreateSchemaPartition);
    plan0.setPartitionSlotsMap(partitionSlotsMap);
    plan0.serialize(buffer);
    buffer.flip();
    GetOrCreateSchemaPartitionReq plan1 =
        (GetOrCreateSchemaPartitionReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
  }

  @Test
  public void CreateDataPartitionPlanTest() throws IOException {
    TDataNodeLocation dataNodeLocation = new TDataNodeLocation();
    dataNodeLocation.setDataNodeId(0);
    dataNodeLocation.setExternalEndPoint(new TEndPoint("0.0.0.0", 6667));
    dataNodeLocation.setInternalEndPoint(new TEndPoint("0.0.0.0", 9003));
    dataNodeLocation.setDataBlockManagerEndPoint(new TEndPoint("0.0.0.0", 8777));
    dataNodeLocation.setConsensusEndPoint(new TEndPoint("0.0.0.0", 40010));

    String storageGroup = "root.sg0";
    TSeriesPartitionSlot seriesPartitionSlot = new TSeriesPartitionSlot(10);
    TTimePartitionSlot timePartitionSlot = new TTimePartitionSlot(100);
    TRegionReplicaSet regionReplicaSet = new TRegionReplicaSet();
    regionReplicaSet.setRegionId(new TConsensusGroupId(TConsensusGroupType.DataRegion, 0));
    regionReplicaSet.setDataNodeLocations(Collections.singletonList(dataNodeLocation));

    Map<String, Map<TSeriesPartitionSlot, Map<TTimePartitionSlot, List<TRegionReplicaSet>>>>
        assignedDataPartition = new HashMap<>();
    assignedDataPartition.put(storageGroup, new HashMap<>());
    assignedDataPartition.get(storageGroup).put(seriesPartitionSlot, new HashMap<>());
    assignedDataPartition
        .get(storageGroup)
        .get(seriesPartitionSlot)
        .put(timePartitionSlot, new ArrayList<>());
    assignedDataPartition
        .get(storageGroup)
        .get(seriesPartitionSlot)
        .get(timePartitionSlot)
        .add(regionReplicaSet);

    CreateDataPartitionReq plan0 = new CreateDataPartitionReq();
    plan0.setAssignedDataPartition(assignedDataPartition);
    plan0.serialize(buffer);
    buffer.flip();
    CreateDataPartitionReq plan1 = (CreateDataPartitionReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
  }

  @Test
  public void GetOrCreateDataPartitionPlanTest() throws IOException {
    String storageGroup = "root.sg0";
    TSeriesPartitionSlot seriesPartitionSlot = new TSeriesPartitionSlot(10);
    TTimePartitionSlot timePartitionSlot = new TTimePartitionSlot(100);

    Map<String, Map<TSeriesPartitionSlot, List<TTimePartitionSlot>>> partitionSlotsMap =
        new HashMap<>();
    partitionSlotsMap.put(storageGroup, new HashMap<>());
    partitionSlotsMap.get(storageGroup).put(seriesPartitionSlot, new ArrayList<>());
    partitionSlotsMap.get(storageGroup).get(seriesPartitionSlot).add(timePartitionSlot);

    GetOrCreateDataPartitionReq plan0 =
        new GetOrCreateDataPartitionReq(ConfigRequestType.GetDataPartition);
    plan0.setPartitionSlotsMap(partitionSlotsMap);
    plan0.serialize(buffer);
    buffer.flip();
    GetOrCreateDataPartitionReq plan1 =
        (GetOrCreateDataPartitionReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
  }

  @Test
  public void AuthorPlanTest() throws IOException, AuthException {

    AuthorReq plan0 = null;
    AuthorReq plan1 = null;
    Set<Integer> permissions = new HashSet<>();
    permissions.add(PrivilegeType.GRANT_USER_PRIVILEGE.ordinal());
    permissions.add(PrivilegeType.REVOKE_USER_ROLE.ordinal());

    // create user
    plan0 =
        new AuthorReq(
            ConfigRequestType.CREATE_USER, "thulab", "", "passwd", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // create role
    plan0 = new AuthorReq(ConfigRequestType.CREATE_ROLE, "", "admin", "", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // alter user
    plan0 =
        new AuthorReq(
            ConfigRequestType.UPDATE_USER, "tempuser", "", "", "newpwd", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // grant user
    plan0 =
        new AuthorReq(ConfigRequestType.GRANT_USER, "tempuser", "", "", "", permissions, "root.ln");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // grant role
    plan0 =
        new AuthorReq(
            ConfigRequestType.GRANT_ROLE_TO_USER,
            "tempuser",
            "temprole",
            "",
            "",
            permissions,
            "root.ln");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // grant role to user
    plan0 =
        new AuthorReq(ConfigRequestType.GRANT_ROLE, "", "temprole", "", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // revoke user
    plan0 =
        new AuthorReq(
            ConfigRequestType.REVOKE_USER, "tempuser", "", "", "", permissions, "root.ln");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // revoke role
    plan0 =
        new AuthorReq(
            ConfigRequestType.REVOKE_ROLE, "", "temprole", "", "", permissions, "root.ln");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // revoke role from user
    plan0 =
        new AuthorReq(
            ConfigRequestType.REVOKE_ROLE_FROM_USER,
            "tempuser",
            "temprole",
            "",
            "",
            new HashSet<>(),
            "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // drop user
    plan0 = new AuthorReq(ConfigRequestType.DROP_USER, "xiaoming", "", "", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // drop role
    plan0 = new AuthorReq(ConfigRequestType.DROP_ROLE, "", "admin", "", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // list user
    plan0 = new AuthorReq(ConfigRequestType.LIST_USER, "", "", "", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // list role
    plan0 = new AuthorReq(ConfigRequestType.LIST_ROLE, "", "", "", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // list privileges user
    plan0 =
        new AuthorReq(ConfigRequestType.LIST_USER_PRIVILEGE, "", "", "", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // list privileges role
    plan0 =
        new AuthorReq(ConfigRequestType.LIST_ROLE_PRIVILEGE, "", "", "", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // list user privileges
    plan0 =
        new AuthorReq(ConfigRequestType.LIST_USER_PRIVILEGE, "", "", "", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // list role privileges
    plan0 =
        new AuthorReq(ConfigRequestType.LIST_ROLE_PRIVILEGE, "", "", "", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // list all role of user
    plan0 = new AuthorReq(ConfigRequestType.LIST_USER_ROLES, "", "", "", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();

    // list all user of role
    plan0 = new AuthorReq(ConfigRequestType.LIST_ROLE_USERS, "", "", "", "", new HashSet<>(), "");
    plan0.serialize(buffer);
    buffer.flip();
    plan1 = (AuthorReq) ConfigRequest.Factory.create(buffer);
    Assert.assertEquals(plan0, plan1);
    cleanBuffer();
  }
}
