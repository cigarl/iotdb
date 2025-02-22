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
package org.apache.iotdb.confignode.manager;

import org.apache.iotdb.common.rpc.thrift.TDataNodeLocation;
import org.apache.iotdb.common.rpc.thrift.TSStatus;
import org.apache.iotdb.confignode.conf.ConfigNodeDescriptor;
import org.apache.iotdb.confignode.consensus.request.read.QueryDataNodeInfoReq;
import org.apache.iotdb.confignode.consensus.request.write.RegisterDataNodeReq;
import org.apache.iotdb.confignode.consensus.response.DataNodeConfigurationResp;
import org.apache.iotdb.confignode.consensus.response.DataNodeLocationsResp;
import org.apache.iotdb.confignode.persistence.DataNodeInfo;
import org.apache.iotdb.confignode.rpc.thrift.TGlobalConfig;
import org.apache.iotdb.consensus.common.DataSet;
import org.apache.iotdb.consensus.common.response.ConsensusWriteResponse;
import org.apache.iotdb.rpc.TSStatusCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Manager server info of data node, add node or remove node */
public class DataNodeManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(DataNodeManager.class);

  private static final DataNodeInfo dataNodeInfo = DataNodeInfo.getInstance();

  private final Manager configManager;

  /** TODO:do some operate after add node or remove node */
  private final List<ChangeServerListener> listeners = new CopyOnWriteArrayList<>();

  public DataNodeManager(Manager configManager) {
    this.configManager = configManager;
  }

  private void setGlobalConfig(DataNodeConfigurationResp dataSet) {
    // Set TGlobalConfig
    TGlobalConfig globalConfig = new TGlobalConfig();
    globalConfig.setDataNodeConsensusProtocolClass(
        ConfigNodeDescriptor.getInstance().getConf().getDataNodeConsensusProtocolClass());
    globalConfig.setSeriesPartitionSlotNum(
        ConfigNodeDescriptor.getInstance().getConf().getSeriesPartitionSlotNum());
    globalConfig.setSeriesPartitionExecutorClass(
        ConfigNodeDescriptor.getInstance().getConf().getSeriesPartitionExecutorClass());
    globalConfig.setTimePartitionInterval(
        ConfigNodeDescriptor.getInstance().getConf().getTimePartitionInterval());
    dataSet.setGlobalConfig(globalConfig);
  }

  /**
   * Register DataNode
   *
   * @param plan RegisterDataNodePlan
   * @return DataNodeConfigurationDataSet. The TSStatus will be set to SUCCESS_STATUS when register
   *     success, and DATANODE_ALREADY_REGISTERED when the DataNode is already exist.
   */
  public DataSet registerDataNode(RegisterDataNodeReq plan) {
    DataNodeConfigurationResp dataSet = new DataNodeConfigurationResp();

    if (DataNodeInfo.getInstance().containsValue(plan.getLocation())) {
      TSStatus status = new TSStatus(TSStatusCode.DATANODE_ALREADY_REGISTERED.getStatusCode());
      status.setMessage("DataNode already registered.");
      dataSet.setStatus(status);
    } else {
      // Persist DataNodeInfo
      plan.getLocation().setDataNodeId(DataNodeInfo.getInstance().generateNextDataNodeId());
      ConsensusWriteResponse resp = getConsensusManager().write(plan);
      dataSet.setStatus(resp.getStatus());
    }

    dataSet.setDataNodeId(plan.getLocation().getDataNodeId());
    setGlobalConfig(dataSet);
    return dataSet;
  }

  /**
   * Get DataNode info
   *
   * @param plan QueryDataNodeInfoPlan
   * @return The specific DataNode's info or all DataNode info if dataNodeId in
   *     QueryDataNodeInfoPlan is -1
   */
  public DataNodeLocationsResp getDataNodeInfo(QueryDataNodeInfoReq plan) {
    return (DataNodeLocationsResp) getConsensusManager().read(plan).getDataset();
  }

  public int getOnlineDataNodeCount() {
    return dataNodeInfo.getOnlineDataNodeCount();
  }

  public List<TDataNodeLocation> getOnlineDataNodes() {
    return dataNodeInfo.getOnlineDataNodes();
  }

  private ConsensusManager getConsensusManager() {
    return configManager.getConsensusManager();
  }

  public void registerListener(final ChangeServerListener serverListener) {
    listeners.add(serverListener);
  }

  public boolean unregisterListener(final ChangeServerListener serverListener) {
    return listeners.remove(serverListener);
  }

  /** TODO: wait data node register, wait */
  public void waitForDataNodes() {
    listeners.stream().forEach(serverListener -> serverListener.waiting());
  }

  private class ServerStartListenerThread extends Thread implements ChangeServerListener {
    private boolean changed = false;

    ServerStartListenerThread() {
      setDaemon(true);
    }

    @Override
    public void addDataNode(TDataNodeLocation DataNodeInfo) {
      serverChanged();
    }

    @Override
    public void removeDataNode(TDataNodeLocation dataNodeInfo) {
      serverChanged();
    }

    private synchronized void serverChanged() {
      changed = true;
      this.notify();
    }

    @Override
    public void run() {
      while (!configManager.isStopped()) {}
    }
  }

  /** TODO: For listener for add or remove data node */
  public interface ChangeServerListener {

    /** Started waiting on DataNode to check */
    default void waiting() {};

    /**
     * The server has joined the cluster
     *
     * @param dataNodeInfo datanode info
     */
    void addDataNode(final TDataNodeLocation dataNodeInfo);

    /**
     * remove data node
     *
     * @param dataNodeInfo data node info
     */
    void removeDataNode(final TDataNodeLocation dataNodeInfo);
  }
}
