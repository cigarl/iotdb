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

package org.apache.iotdb.db.qp.physical.crud;

import org.apache.iotdb.db.exception.metadata.IllegalPathException;
import org.apache.iotdb.db.metadata.PartialPath;
import org.apache.iotdb.db.qp.logical.Operator.OperatorType;
import org.apache.iotdb.db.qp.physical.PhysicalPlan;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class SelectIntoPlan extends PhysicalPlan {

  private QueryPlan queryPlan;
  private List<PartialPath> intoPaths;

  public SelectIntoPlan() {
    super(true, OperatorType.SELECT_INTO);
  }

  public SelectIntoPlan(QueryPlan queryPlan, List<PartialPath> intoPaths) {
    super(false, OperatorType.SELECT_INTO);
    this.queryPlan = queryPlan;
    this.intoPaths = intoPaths;
  }

  @Override
  public boolean isSelectInto() {
    return true;
  }

  @Override
  public void serialize(DataOutputStream stream) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void serialize(ByteBuffer buffer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deserialize(ByteBuffer buffer) throws IllegalPathException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<PartialPath> getPaths() {
    throw new UnsupportedOperationException();
  }

  public QueryPlan getQueryPlan() {
    return queryPlan;
  }

  public List<PartialPath> getIntoPaths() {
    return intoPaths;
  }
}
