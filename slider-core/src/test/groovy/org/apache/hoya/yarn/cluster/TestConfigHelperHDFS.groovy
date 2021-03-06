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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hoya.yarn.cluster

import groovy.util.logging.Slf4j
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem as HadoopFS
import org.apache.hadoop.fs.Path
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.hoya.tools.ConfigHelper
import org.junit.Test

@Slf4j
class TestConfigHelperHDFS extends YarnMiniClusterTestBase {

  //diabled for now; 
  @Test 
  public void testConfigHelperHDFS() throws Throwable {
    YarnConfiguration config = getConfiguration()
    createMiniHDFSCluster("testConfigHelperHDFS", config)
    
    Configuration conf= new Configuration(false);
    conf.set("key","value");
    URI fsURI = new URI(fsDefaultName)
    Path root = new Path(fsURI)
    Path confPath = new Path(root, "conf.xml")
    HadoopFS dfs = HadoopFS.get(fsURI,config)
    ConfigHelper.saveConfig(dfs,confPath, conf)
    //load time
    Configuration loaded = ConfigHelper.loadConfiguration(dfs,confPath)
    log.info(ConfigHelper.dumpConfigToString(loaded))
    assert loaded.get("key") == "value"
  }

  @Test
  public void testConfigLoaderIteration() throws Throwable {

    String xml =
    """<?xml version="1.0" encoding="UTF-8" standalone="no"?><configuration>
<property><name>key</name><value>value</value><source>programatically</source></property>
</configuration>
    """
    InputStream ins = new ByteArrayInputStream(xml.bytes);
    Configuration conf = new Configuration(false);
    conf.addResource(ins);
    Configuration conf2 = new Configuration(false);
    for (Map.Entry<String, String> entry : conf) {
      String key = entry.getKey();
      String val = entry.getValue();
      conf2.set(key, val, "src")
    }
    
  }
}
