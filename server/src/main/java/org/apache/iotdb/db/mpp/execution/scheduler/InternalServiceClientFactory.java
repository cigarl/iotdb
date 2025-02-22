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

package org.apache.iotdb.db.mpp.execution.scheduler;

import org.apache.iotdb.common.rpc.thrift.TEndPoint;
import org.apache.iotdb.mpp.rpc.thrift.InternalService;
import org.apache.iotdb.rpc.RpcTransportFactory;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InternalServiceClientFactory {
  private static final int TIMEOUT_MS = 10_000;

  private static final Logger logger = LoggerFactory.getLogger(InternalServiceClientFactory.class);

  // TODO need to be replaced by mature client pool in the future
  private static final Map<TEndPoint, InternalService.Iface> internalServiceClientMap =
      new ConcurrentHashMap<>();

  public static InternalService.Iface getInternalServiceClient(TEndPoint endpoint)
      throws TTransportException {
    return internalServiceClientMap.computeIfAbsent(
        endpoint,
        address -> {
          TTransport transport;
          try {
            transport =
                RpcTransportFactory.INSTANCE.getTransport(
                    // as there is a try-catch already, we do not need to use TSocket.wrap
                    address.getIp(), address.getPort(), TIMEOUT_MS);
            transport.open();

          } catch (TTransportException e) {
            logger.error(
                "error happened while creating mpp service client for {}:{}",
                endpoint.getIp(),
                endpoint.getPort(),
                e);
            throw new RuntimeException(e);
          }
          TProtocol protocol = new TBinaryProtocol(transport);
          return newSynchronizedClient(new InternalService.Client(protocol));
        });
  }

  public static InternalService.Iface newSynchronizedClient(InternalService.Iface client) {
    return (InternalService.Iface)
        Proxy.newProxyInstance(
            InternalServiceClientFactory.class.getClassLoader(),
            new Class[] {InternalService.Iface.class},
            new SynchronizedHandler(client));
  }

  private static class SynchronizedHandler implements InvocationHandler {

    private final InternalService.Iface client;

    public SynchronizedHandler(InternalService.Iface client) {
      this.client = client;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      try {
        synchronized (client) {
          return method.invoke(client, args);
        }
      } catch (InvocationTargetException e) {
        // all IFace APIs throw TException
        if (e.getTargetException() instanceof TException) {
          throw e.getTargetException();
        } else {
          // should not happen
          throw new TException(
              "Error in calling method " + method.getName(), e.getTargetException());
        }
      } catch (Exception e) {
        throw new TException("Error in calling method " + method.getName(), e);
      }
    }
  }
}
