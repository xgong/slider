
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





package org.apache.hadoop.hoya.yarn.cluster.archives

import groovy.util.logging.Slf4j
import org.apache.hadoop.fs.FileUtil
import org.apache.hadoop.fs.Path
import org.apache.hadoop.hoya.api.ClusterDescription
import org.apache.hadoop.hoya.tools.Duration
import org.apache.hadoop.hoya.yarn.Arguments
import org.apache.hadoop.hoya.yarn.CommonArgs
import org.apache.hadoop.hoya.yarn.client.HoyaClient
import org.apache.hadoop.hoya.yarn.providers.hbase.HBaseMiniClusterTestBase
import org.apache.hadoop.yarn.service.launcher.ServiceLauncher
import org.junit.Test

/**
 * Test of RM creation. This is so the later test's prereq's can be met
 */
//@CompileStatic
@Slf4j
class TestVersionFromArchiveOnHDFS extends HBaseMiniClusterTestBase {

  String uploadedImage;

  @Test
  public void testVersionFromArchiveOnHDFS() throws Throwable {
    describe "create a cluster from a tar on HFD, exec the version command"
    assumeHBaseArchive();
    String localArchive = super.getHBaseArchive()
    String clustername = "TestVersionFromArchiveOnHDFS"
    createMiniCluster(clustername, createConfiguration(), 1, 1, 1, true, true)

    File localArchiveFile = new File(localArchive)
    assert localArchiveFile.exists()
    Path remoteUnresolvedArchive = new Path(localArchiveFile.name)
    assert FileUtil.copy(localArchiveFile, hdfsCluster.fileSystem, remoteUnresolvedArchive, false, getTestConfiguration())
    Path hbasePath = hdfsCluster.fileSystem.resolvePath(remoteUnresolvedArchive)
    uploadedImage = hbasePath.toUri().toString()
    switchToImageDeploy = true
    ServiceLauncher launcher = createHBaseCluster(clustername,
                                                 0,
                                                 HBASE_VERSION_COMMAND_SEQUENCE,
                                                 true,
                                                 false)
    assert launcher.serviceExitCode == 0
    HoyaClient hoyaClient = (HoyaClient) launcher.service
    hoyaClient.monitorAppToRunning(new Duration(10 * 60000))
    ClusterDescription status = hoyaClient.getClusterStatus(clustername)
    log.info("Status $status")
    waitForAppToFinish(hoyaClient)
  }

  /**
   * Override this so that the path is set up with the HDFS copy
   * @return
   */
  @Override
  String getHBaseArchive() {
    return uploadedImage
  }


  public List<String> getImageCommands() {
    [Arguments.ARG_IMAGE, uploadedImage]
  }

}
