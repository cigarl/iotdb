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
package org.apache.iotdb.db.mpp.sql.planner.plan.node.source;

import org.apache.iotdb.common.rpc.thrift.TRegionReplicaSet;
import org.apache.iotdb.commons.utils.TestOnly;
import org.apache.iotdb.commons.utils.ThriftCommonsSerDeUtils;
import org.apache.iotdb.db.metadata.path.PartialPath;
import org.apache.iotdb.db.metadata.path.PathDeserializeUtil;
import org.apache.iotdb.db.mpp.common.header.ColumnHeader;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.PlanNode;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.PlanNodeId;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.PlanNodeType;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.PlanVisitor;
import org.apache.iotdb.db.mpp.sql.planner.plan.parameter.Aggregation;
import org.apache.iotdb.db.mpp.sql.planner.plan.parameter.GroupByTimeParameter;
import org.apache.iotdb.db.mpp.sql.statement.component.OrderBy;
import org.apache.iotdb.db.query.aggregation.AggregationType;
import org.apache.iotdb.tsfile.exception.NotImplementedException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.filter.basic.Filter;
import org.apache.iotdb.tsfile.read.filter.factory.FilterFactory;
import org.apache.iotdb.tsfile.utils.Pair;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;

import com.google.common.collect.ImmutableList;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This node is responsible to do the aggregation calculation for one series. It will read the
 * target series and calculate the aggregation result by the aggregation digest or raw data of this
 * series.
 *
 * <p>The aggregation result will be represented as a TsBlock
 *
 * <p>This node will split data of the target series into many groups by time range and do the
 * aggregation calculation for each group. Each result will be one row of the result TsBlock. The
 * timestamp of each row is the start time of the time range group.
 *
 * <p>If there is no time range split parameter, the result TsBlock will only contain one row, which
 * represent the whole aggregation result of this series. And the timestamp will be 0, which is
 * meaningless.
 */
public class SeriesAggregateScanNode extends SourceNode {

  // The series path and aggregation functions on this series.
  // (Currently, we only support one series in the aggregation function)
  private final PartialPath seriesPath;
  @Deprecated private final List<AggregationType> aggregateFuncList;
  private List<Aggregation> aggregationList;

  // all the sensors in seriesPath's device of current query
  private final Set<String> allSensors;

  // The order to traverse the data.
  // Currently, we only support TIMESTAMP_ASC and TIMESTAMP_DESC here.
  // The default order is TIMESTAMP_ASC, which means "order by timestamp asc"
  private final OrderBy scanOrder;

  // time filter for current series, could be null if doesn't exist
  private final Filter timeFilter;

  // The parameter of `group by time`
  // Its value will be null if there is no `group by time` clause,
  private final GroupByTimeParameter groupByTimeParameter;

  // contain output column headers of this node
  private final List<ColumnHeader> outputColumnHeaders;

  // The id of DataRegion where the node will run
  private TRegionReplicaSet regionReplicaSet;

  public SeriesAggregateScanNode(
      PlanNodeId id,
      PartialPath seriesPath,
      Set<String> allSensors,
      List<AggregationType> aggregateFuncList,
      OrderBy scanOrder,
      Filter timeFilter,
      GroupByTimeParameter groupByTimeParameter) {
    super(id);
    this.seriesPath = seriesPath;
    this.allSensors = allSensors;
    this.aggregateFuncList = aggregateFuncList;
    this.scanOrder = scanOrder;
    this.timeFilter = timeFilter;
    this.groupByTimeParameter = groupByTimeParameter;
    this.outputColumnHeaders =
        aggregateFuncList.stream()
            .map(
                functionType ->
                    new ColumnHeader(
                        seriesPath.getFullPath(), functionType.name(), seriesPath.getSeriesType()))
            .collect(Collectors.toList());
  }

  public OrderBy getScanOrder() {
    return scanOrder;
  }

  public Set<String> getAllSensors() {
    return allSensors;
  }

  public Filter getTimeFilter() {
    return timeFilter;
  }

  public GroupByTimeParameter getGroupByTimeParameter() {
    return groupByTimeParameter;
  }

  @Override
  public List<PlanNode> getChildren() {
    return ImmutableList.of();
  }

  @Override
  public void addChild(PlanNode child) {}

  @Override
  public PlanNode clone() {
    throw new NotImplementedException("clone of SeriesAggregateScanNode is not implemented");
  }

  @Override
  public int allowedChildCount() {
    return NO_CHILD_ALLOWED;
  }

  @Override
  public List<ColumnHeader> getOutputColumnHeaders() {
    return outputColumnHeaders;
  }

  @Override
  public List<String> getOutputColumnNames() {
    return outputColumnHeaders.stream()
        .map(ColumnHeader::getColumnName)
        .collect(Collectors.toList());
  }

  @Override
  public List<TSDataType> getOutputColumnTypes() {
    return outputColumnHeaders.stream()
        .map(ColumnHeader::getColumnType)
        .collect(Collectors.toList());
  }

  @Override
  public void open() throws Exception {}

  @Override
  public TRegionReplicaSet getRegionReplicaSet() {
    return this.regionReplicaSet;
  }

  @Override
  public void setRegionReplicaSet(TRegionReplicaSet regionReplicaSet) {
    this.regionReplicaSet = regionReplicaSet;
  }

  @Override
  public void close() throws Exception {}

  @Override
  public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
    return visitor.visitSeriesAggregate(this, context);
  }

  @Override
  protected void serializeAttributes(ByteBuffer byteBuffer) {
    PlanNodeType.SERIES_AGGREGATE_SCAN.serialize(byteBuffer);
    seriesPath.serialize(byteBuffer);
    ReadWriteIOUtils.write(allSensors.size(), byteBuffer);
    for (String sensor : allSensors) {
      ReadWriteIOUtils.write(sensor, byteBuffer);
    }
    ReadWriteIOUtils.write(aggregateFuncList.size(), byteBuffer);
    for (AggregationType aggregationType : aggregateFuncList) {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
      try {
        aggregationType.serializeTo(dataOutputStream);
      } catch (IOException ioException) {
        ioException.printStackTrace();
      }
      byteBuffer.put(byteArrayOutputStream.toByteArray());
    }
    ReadWriteIOUtils.write(scanOrder.ordinal(), byteBuffer);
    timeFilter.serialize(byteBuffer);
    groupByTimeParameter.serialize(byteBuffer);
    ThriftCommonsSerDeUtils.writeTRegionReplicaSet(regionReplicaSet, byteBuffer);
  }

  public static SeriesAggregateScanNode deserialize(ByteBuffer byteBuffer) {
    PartialPath partialPath = (PartialPath) PathDeserializeUtil.deserialize(byteBuffer);
    int allSensorsSize = ReadWriteIOUtils.readInt(byteBuffer);
    Set<String> allSensors = new HashSet<>();
    for (int i = 0; i < allSensorsSize; i++) {
      allSensors.add(ReadWriteIOUtils.readString(byteBuffer));
    }
    int aggregateFuncSize = ReadWriteIOUtils.readInt(byteBuffer);
    List<AggregationType> aggregateFuncList = new ArrayList<>();
    for (int i = 0; i < aggregateFuncSize; i++) {
      aggregateFuncList.add(AggregationType.deserialize(byteBuffer));
    }
    OrderBy scanOrder = OrderBy.values()[ReadWriteIOUtils.readInt(byteBuffer)];
    Filter timeFilter = FilterFactory.deserialize(byteBuffer);

    GroupByTimeParameter groupByTimeParameter = GroupByTimeParameter.deserialize(byteBuffer);
    TRegionReplicaSet regionReplicaSet = ThriftCommonsSerDeUtils.readTRegionReplicaSet(byteBuffer);
    PlanNodeId planNodeId = PlanNodeId.deserialize(byteBuffer);
    SeriesAggregateScanNode seriesAggregateScanNode =
        new SeriesAggregateScanNode(
            planNodeId,
            partialPath,
            allSensors,
            aggregateFuncList,
            scanOrder,
            timeFilter,
            groupByTimeParameter);
    seriesAggregateScanNode.regionReplicaSet = regionReplicaSet;
    return seriesAggregateScanNode;
  }

  public PartialPath getSeriesPath() {
    return seriesPath;
  }

  public List<AggregationType> getAggregateFuncList() {
    return aggregateFuncList;
  }

  @TestOnly
  public Pair<String, List<String>> print() {
    String title = String.format("[SeriesAggregateScanNode (%s)]", this.getPlanNodeId());
    List<String> attributes = new ArrayList<>();
    attributes.add("AggregateFunctions: " + this.getAggregateFuncList().toString());
    return new Pair<>(title, attributes);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SeriesAggregateScanNode that = (SeriesAggregateScanNode) o;
    return Objects.equals(groupByTimeParameter, that.groupByTimeParameter)
        && Objects.equals(seriesPath, that.seriesPath)
        && Objects.equals(allSensors, that.allSensors)
        && Objects.equals(
            aggregateFuncList.stream().sorted().collect(Collectors.toList()),
            that.aggregateFuncList.stream().sorted().collect(Collectors.toList()))
        && scanOrder == that.scanOrder
        && Objects.equals(timeFilter, that.timeFilter);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        groupByTimeParameter,
        seriesPath,
        aggregateFuncList.stream().sorted().collect(Collectors.toList()),
        scanOrder,
        timeFilter);
  }
}
