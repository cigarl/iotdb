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

package org.apache.iotdb.db.mpp.buffer;

import org.apache.iotdb.common.rpc.thrift.TEndPoint;
import org.apache.iotdb.db.mpp.buffer.DataBlockManager.SinkHandleListener;
import org.apache.iotdb.db.mpp.memory.LocalMemoryManager;
import org.apache.iotdb.mpp.rpc.thrift.DataBlockService;
import org.apache.iotdb.mpp.rpc.thrift.TEndOfDataBlockEvent;
import org.apache.iotdb.mpp.rpc.thrift.TFragmentInstanceId;
import org.apache.iotdb.mpp.rpc.thrift.TNewDataBlockEvent;
import org.apache.iotdb.tsfile.read.common.block.TsBlock;
import org.apache.iotdb.tsfile.read.common.block.column.TsBlockSerde;

import com.google.common.util.concurrent.ListenableFuture;
import org.apache.commons.lang3.Validate;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.nonCancellationPropagating;

public class SinkHandle implements ISinkHandle {

  private static final Logger logger = LoggerFactory.getLogger(SinkHandle.class);

  public static final int MAX_ATTEMPT_TIMES = 3;

  private final TEndPoint remoteEndpoint;
  private final TFragmentInstanceId remoteFragmentInstanceId;
  private final String remotePlanNodeId;
  private final TFragmentInstanceId localFragmentInstanceId;
  private final LocalMemoryManager localMemoryManager;
  private final ExecutorService executorService;
  private final DataBlockService.Iface client;
  private final TsBlockSerde serde;
  private final SinkHandleListener sinkHandleListener;

  // Use LinkedHashMap to meet 2 needs,
  //   1. Predictable iteration order so that removing buffered tsblocks can be efficient.
  //   2. Fast lookup.
  private final LinkedHashMap<Integer, TsBlock> sequenceIdToTsBlock = new LinkedHashMap<>();

  private volatile ListenableFuture<Void> blocked = immediateFuture(null);
  private int nextSequenceId = 0;
  private long bufferRetainedSizeInBytes;
  private boolean closed;
  private boolean noMoreTsBlocks;

  public SinkHandle(
      TEndPoint remoteEndpoint,
      TFragmentInstanceId remoteFragmentInstanceId,
      String remotePlanNodeId,
      TFragmentInstanceId localFragmentInstanceId,
      LocalMemoryManager localMemoryManager,
      ExecutorService executorService,
      DataBlockService.Iface client,
      TsBlockSerde serde,
      SinkHandleListener sinkHandleListener) {
    this.remoteEndpoint = Validate.notNull(remoteEndpoint);
    this.remoteFragmentInstanceId = Validate.notNull(remoteFragmentInstanceId);
    this.remotePlanNodeId = Validate.notNull(remotePlanNodeId);
    this.localFragmentInstanceId = Validate.notNull(localFragmentInstanceId);
    this.localMemoryManager = Validate.notNull(localMemoryManager);
    this.executorService = Validate.notNull(executorService);
    this.client = Validate.notNull(client);
    this.serde = Validate.notNull(serde);
    this.sinkHandleListener = Validate.notNull(sinkHandleListener);
  }

  @Override
  public ListenableFuture<Void> isFull() {
    if (closed) {
      throw new IllegalStateException("Sink handle is closed.");
    }
    return nonCancellationPropagating(blocked);
  }

  private void submitSendNewDataBlockEventTask(int startSequenceId, List<Long> blockSizes) {
    // TODO: (xingtanzjr)
    // We temporarily make it sync instead of async to avoid EOS Event(SinkHandle close() method is
    // called) is sent before NewDataBlockEvent arrived
    //    new SendNewDataBlockEventTask(startSequenceId, blockSizes).run();
    executorService.submit(new SendNewDataBlockEventTask(startSequenceId, blockSizes));
  }

  @Override
  public void send(List<TsBlock> tsBlocks) {
    Validate.notNull(tsBlocks, "tsBlocks is null");
    if (closed) {
      throw new IllegalStateException("Sink handle is closed.");
    }
    if (!blocked.isDone()) {
      throw new IllegalStateException("Sink handle is blocked.");
    }
    if (noMoreTsBlocks) {
      return;
    }

    long retainedSizeInBytes = 0L;
    for (TsBlock tsBlock : tsBlocks) {
      retainedSizeInBytes += tsBlock.getRetainedSizeInBytes();
    }
    int startSequenceId;
    List<Long> tsBlockSizes = new ArrayList<>();
    synchronized (this) {
      startSequenceId = nextSequenceId;
      blocked =
          localMemoryManager
              .getQueryPool()
              .reserve(localFragmentInstanceId.getQueryId(), retainedSizeInBytes);
      bufferRetainedSizeInBytes += retainedSizeInBytes;
      for (TsBlock tsBlock : tsBlocks) {
        sequenceIdToTsBlock.put(nextSequenceId, tsBlock);
        nextSequenceId += 1;
      }
      for (int i = startSequenceId; i < nextSequenceId; i++) {
        tsBlockSizes.add(sequenceIdToTsBlock.get(i).getRetainedSizeInBytes());
      }
    }

    // TODO: consider merge multiple NewDataBlockEvent for less network traffic.
    submitSendNewDataBlockEventTask(startSequenceId, tsBlockSizes);
  }

  @Override
  public void send(int partition, List<TsBlock> tsBlocks) {
    throw new UnsupportedOperationException();
  }

  private void sendEndOfDataBlockEvent() throws TException {
    logger.debug(
        "Send end of data block event to plan node {} of {}. {}",
        remotePlanNodeId,
        remoteFragmentInstanceId,
        Thread.currentThread().getName());
    int attempt = 0;
    TEndOfDataBlockEvent endOfDataBlockEvent =
        new TEndOfDataBlockEvent(
            remoteFragmentInstanceId,
            remotePlanNodeId,
            localFragmentInstanceId,
            nextSequenceId - 1);
    while (attempt < MAX_ATTEMPT_TIMES) {
      attempt += 1;
      try {
        client.onEndOfDataBlockEvent(endOfDataBlockEvent);
        break;
      } catch (TException e) {
        logger.error(
            "Failed to send end of data block event to plan node {} of {} due to {}, attempt times: {}",
            remotePlanNodeId,
            remoteFragmentInstanceId,
            e.getMessage(),
            attempt,
            e);
        if (attempt == MAX_ATTEMPT_TIMES) {
          throw e;
        }
      }
    }
  }

  @Override
  public void close() {
    logger.info("Sink handle {} is being closed.", this);
    if (closed) {
      return;
    }
    try {
      sendEndOfDataBlockEvent();
    } catch (TException e) {
      throw new RuntimeException("Send EndOfDataBlockEvent failed", e);
    }
    synchronized (this) {
      closed = true;
      noMoreTsBlocks = true;
    }
    sinkHandleListener.onClosed(this);
    logger.info("Sink handle {} is closed.", this);
  }

  @Override
  public void abort() {
    logger.info("Sink handle {} is being aborted.", this);
    synchronized (this) {
      sequenceIdToTsBlock.clear();
      closed = true;
      if (blocked != null && !blocked.isDone()) {
        blocked.cancel(true);
      }
      if (bufferRetainedSizeInBytes > 0) {
        localMemoryManager
            .getQueryPool()
            .free(localFragmentInstanceId.getQueryId(), bufferRetainedSizeInBytes);
        bufferRetainedSizeInBytes = 0;
      }
    }
    sinkHandleListener.onAborted(this);
    logger.info("Sink handle {} is aborted", this);
  }

  @Override
  public synchronized void setNoMoreTsBlocks() {
    noMoreTsBlocks = true;
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public boolean isFinished() {
    return noMoreTsBlocks && sequenceIdToTsBlock.isEmpty();
  }

  @Override
  public long getBufferRetainedSizeInBytes() {
    return bufferRetainedSizeInBytes;
  }

  @Override
  public int getNumOfBufferedTsBlocks() {
    return sequenceIdToTsBlock.size();
  }

  ByteBuffer getSerializedTsBlock(int partition, int sequenceId) {
    throw new UnsupportedOperationException();
  }

  ByteBuffer getSerializedTsBlock(int sequenceId) throws IOException {
    TsBlock tsBlock;
    tsBlock = sequenceIdToTsBlock.get(sequenceId);
    if (tsBlock == null) {
      throw new IllegalStateException("The data block doesn't exist. Sequence ID: " + sequenceId);
    }
    return serde.serialize(tsBlock);
  }

  void acknowledgeTsBlock(int startSequenceId, int endSequenceId) {
    long freedBytes = 0L;
    synchronized (this) {
      Iterator<Entry<Integer, TsBlock>> iterator = sequenceIdToTsBlock.entrySet().iterator();
      while (iterator.hasNext()) {
        Entry<Integer, TsBlock> entry = iterator.next();
        if (entry.getKey() < startSequenceId) {
          continue;
        }
        if (entry.getKey() >= endSequenceId) {
          break;
        }
        freedBytes += entry.getValue().getRetainedSizeInBytes();
        bufferRetainedSizeInBytes -= entry.getValue().getRetainedSizeInBytes();
        iterator.remove();
      }
    }
    if (isFinished()) {
      sinkHandleListener.onFinish(this);
    }
    localMemoryManager.getQueryPool().free(localFragmentInstanceId.getQueryId(), freedBytes);
  }

  TEndPoint getRemoteEndpoint() {
    return remoteEndpoint;
  }

  TFragmentInstanceId getRemoteFragmentInstanceId() {
    return remoteFragmentInstanceId;
  }

  String getRemotePlanNodeId() {
    return remotePlanNodeId;
  }

  TFragmentInstanceId getLocalFragmentInstanceId() {
    return localFragmentInstanceId;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", SinkHandle.class.getSimpleName() + "[", "]")
        .add("remoteEndpoint='" + remoteEndpoint + "'")
        .add("remoteFragmentInstanceId=" + remoteFragmentInstanceId)
        .add("remotePlanNodeId='" + remotePlanNodeId + "'")
        .add("localFragmentInstanceId=" + localFragmentInstanceId)
        .toString();
  }

  /**
   * Send a {@link org.apache.iotdb.mpp.rpc.thrift.TNewDataBlockEvent} to downstream fragment
   * instance.
   */
  class SendNewDataBlockEventTask implements Runnable {

    private final int startSequenceId;
    private final List<Long> blockSizes;

    SendNewDataBlockEventTask(int startSequenceId, List<Long> blockSizes) {
      Validate.isTrue(
          startSequenceId >= 0,
          "Start sequence ID should be greater than or equal to zero, but was: "
              + startSequenceId
              + ".");
      this.startSequenceId = startSequenceId;
      this.blockSizes = Validate.notNull(blockSizes);
    }

    @Override
    public void run() {
      logger.debug(
          "Send new data block event [{}, {}) to plan node {} of {}.",
          startSequenceId,
          startSequenceId + blockSizes.size(),
          remotePlanNodeId,
          remoteFragmentInstanceId);
      int attempt = 0;
      TNewDataBlockEvent newDataBlockEvent =
          new TNewDataBlockEvent(
              remoteFragmentInstanceId,
              remotePlanNodeId,
              localFragmentInstanceId,
              startSequenceId,
              blockSizes);
      while (attempt < MAX_ATTEMPT_TIMES) {
        attempt += 1;
        try {
          client.onNewDataBlockEvent(newDataBlockEvent);
          break;
        } catch (Throwable e) {
          logger.error(
              "Failed to send new data block event to plan node {} of {} due to {}, attempt times: {}",
              remotePlanNodeId,
              remoteFragmentInstanceId,
              e.getMessage(),
              attempt,
              e);
          if (attempt == MAX_ATTEMPT_TIMES) {
            sinkHandleListener.onFailure(e);
          }
        }
      }
    }
  }
}
