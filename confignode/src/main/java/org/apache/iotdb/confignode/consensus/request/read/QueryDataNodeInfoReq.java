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
package org.apache.iotdb.confignode.consensus.request.read;

import org.apache.iotdb.confignode.consensus.request.ConfigRequest;
import org.apache.iotdb.confignode.consensus.request.ConfigRequestType;

import java.nio.ByteBuffer;
import java.util.Objects;

/** Get DataNodeInfo by the specific DataNode's id. And return all when dataNodeID is set to -1. */
public class QueryDataNodeInfoReq extends ConfigRequest {

  private int dataNodeID;

  public QueryDataNodeInfoReq() {
    super(ConfigRequestType.QueryDataNodeInfo);
  }

  public QueryDataNodeInfoReq(int dataNodeID) {
    this();
    this.dataNodeID = dataNodeID;
  }

  public Integer getDataNodeID() {
    return dataNodeID;
  }

  @Override
  protected void serializeImpl(ByteBuffer buffer) {
    buffer.putInt(ConfigRequestType.QueryDataNodeInfo.ordinal());
    buffer.putInt(dataNodeID);
  }

  @Override
  protected void deserializeImpl(ByteBuffer buffer) {
    this.dataNodeID = buffer.getInt();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    QueryDataNodeInfoReq that = (QueryDataNodeInfoReq) o;
    return dataNodeID == that.dataNodeID;
  }

  @Override
  public int hashCode() {
    return Objects.hash(dataNodeID);
  }
}
