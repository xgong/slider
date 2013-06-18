/*
 * Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.hadoop.hoya.yarn.cluster.masterless

import groovy.util.logging.Commons
import org.apache.hadoop.hoya.HoyaExitCodes
import org.apache.hadoop.hoya.exceptions.HoyaException
import org.apache.hadoop.hoya.yarn.client.HoyaClient
import org.apache.hadoop.hoya.yarn.cluster.YarnMiniClusterTestBase
import org.apache.hadoop.yarn.api.records.ApplicationId
import org.apache.hadoop.yarn.api.records.ApplicationReport
import org.apache.hadoop.yarn.api.records.YarnApplicationState
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.hadoop.yarn.service.launcher.ServiceLauncher
import org.junit.Test

/**
 * create masterless AMs and work with them. This is faster than
 * bringing up full clusters
 */
@Commons
class TestCreateClusterRunning extends YarnMiniClusterTestBase {


    @Test
    public void testCreateClusterRunning() throws Throwable {
      String clustername = "TestCreateClusterRunning"
      createMiniCluster(clustername, new YarnConfiguration(), 1, true)

      describe "create a masterless AM, while it is running, try to create" +
               "a second cluster with the same name"

      //launch fake master
      ServiceLauncher launcher
      launcher = createMasterlessAM(clustername, 0, true, true)
      HoyaClient hoyaClient = (HoyaClient) launcher.service

      //now try to create instance #2, and expect an in-use failure
    try {
      createMasterlessAM(clustername, 0, false, true)
      fail("expected a failure")
    } catch (HoyaException e) {
      assert e.exitCode == HoyaExitCodes.EXIT_BAD_CLUSTER_STATE
      assert e.toString().contains(HoyaClient.E_CLUSTER_RUNNING)
    }


  }


}
