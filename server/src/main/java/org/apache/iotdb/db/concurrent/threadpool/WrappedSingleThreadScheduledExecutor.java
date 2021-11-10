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

package org.apache.iotdb.db.concurrent.threadpool;

import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.service.JMXService;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WrappedSingleThreadScheduledExecutor
    implements ScheduledExecutorService, WrappedSingleThreadScheduledExecutorMBean {
  private final String mbeanName;
  ScheduledExecutorService service;

  public WrappedSingleThreadScheduledExecutor(ScheduledExecutorService service, String mbeanName) {
    this.service = service;
    this.mbeanName =
        String.format(
            "%s:%s=%s", IoTDBConstant.IOTDB_THREADPOOL_PACKAGE, IoTDBConstant.JMX_TYPE, mbeanName);
    JMXService.registerMBean(this, this.mbeanName);
  }

  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return service.schedule(command, delay, unit);
  }

  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    return service.schedule(callable, delay, unit);
  }

  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable command, long initialDelay, long period, TimeUnit unit) {
    return service.scheduleAtFixedRate(command, initialDelay, period, unit);
  }

  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return service.scheduleWithFixedDelay(command, initialDelay, delay, unit);
  }

  public void shutdown() {
    service.shutdown();
    JMXService.deregisterMBean(mbeanName);
  }

  public List<Runnable> shutdownNow() {
    JMXService.deregisterMBean(mbeanName);
    return service.shutdownNow();
  }

  public boolean isShutdown() {
    return service.isShutdown();
  }

  public boolean isTerminated() {
    return service.isTerminated();
  }

  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return service.awaitTermination(timeout, unit);
  }

  public <T> Future<T> submit(Callable<T> task) {
    return service.submit(task);
  }

  public <T> Future<T> submit(Runnable task, T result) {
    return service.submit(task, result);
  }

  public Future<?> submit(Runnable task) {
    return service.submit(task);
  }

  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return service.invokeAll(tasks);
  }

  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return service.invokeAll(tasks, timeout, unit);
  }

  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    return service.invokeAny(tasks);
  }

  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return service.invokeAny(tasks, timeout, unit);
  }

  public void execute(Runnable command) {
    service.execute(command);
  }
}
