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

package org.apache.hoya.providers.agent;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hoya.HoyaKeys;
import org.apache.hoya.api.RoleKeys;
import org.apache.hoya.core.conf.AggregateConf;
import org.apache.hoya.core.conf.ConfTreeOperations;
import org.apache.hoya.core.conf.MapOperations;
import org.apache.hoya.core.launch.AbstractLauncher;
import org.apache.hoya.exceptions.BadConfigException;
import org.apache.hoya.exceptions.HoyaException;
import org.apache.hoya.providers.AbstractClientProvider;
import org.apache.hoya.providers.ProviderRole;
import org.apache.hoya.providers.ProviderUtils;
import org.apache.hoya.tools.ConfigHelper;
import org.apache.hoya.tools.HoyaFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class implements  the client-side aspects
 * of the agent deployer
 */
public class AgentClientProvider extends AbstractClientProvider
              implements AgentKeys, HoyaKeys {


  protected static final Logger log =
    LoggerFactory.getLogger(AgentClientProvider.class);
  protected static final String NAME = "agent";
  private static final ProviderUtils providerUtils = new ProviderUtils(log);


  protected AgentClientProvider(Configuration conf) {
    super(conf);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public List<ProviderRole> getRoles() {
    return AgentRoles.getRoles();
  }

  /**
   * Get a map of all the default options for the cluster; values
   * that can be overridden by user defaults after
   *
   * @return a possibly empty map of default cluster options.
   */
  @Override
  public Configuration getDefaultClusterConfiguration() throws
      FileNotFoundException {
    return ConfigHelper.loadMandatoryResource(
        "org/apache/hoya/providers/agent/agent.xml");
  }

  /**
   * Create the default cluster role instance for a named
   * cluster role;
   *
   * @param rolename role name
   * @return a node that can be added to the JSON
   */
  @Override
  public Map<String, String> createDefaultClusterRole(String rolename) throws
      HoyaException,
      IOException {
    Map<String, String> rolemap = new HashMap<String, String>();

    return rolemap;
  }

  @Override //Client
  public void preflightValidateClusterConfiguration(HoyaFileSystem hoyaFileSystem,
                                                    String clustername,
                                                    Configuration configuration,
                                                    AggregateConf instanceDefinition,
                                                    Path clusterDirPath,
                                                    Path generatedConfDirPath,
                                                    boolean secure) throws
      HoyaException,
      IOException {
    super.preflightValidateClusterConfiguration(hoyaFileSystem, clustername,
                                                configuration,
                                                instanceDefinition,
                                                clusterDirPath,
                                                generatedConfDirPath, secure);

  }


  @Override
  public void validateInstanceDefinition(AggregateConf instanceDefinition) throws
                                                                           HoyaException {
    super.validateInstanceDefinition(instanceDefinition);
    ConfTreeOperations resources =
      instanceDefinition.getResourceOperations();

    providerUtils.validateNodeCount(instanceDefinition, ROLE_NODE,
                                    0, -1);

  

    // Mandatory options for Agents
    // TODO: Enable these after CLI changes
    //clusterSpec.getMandatoryOption(CONTROLLER_URL);
    //clusterSpec.getMandatoryOption(PACKAGE_PATH);
    //clusterSpec.getMandatoryOption(AGENT_PATH);


    Set<String> roleNames = resources.getComponentNames();
    roleNames.remove(HoyaKeys.ROLE_HOYA_AM);
    Map<Integer, String> priorityMap = new HashMap<Integer, String>();
    for (String roleName : roleNames) {
      MapOperations component = resources.getComponent(roleName);
      int count =
        component.getMandatoryOptionInt(RoleKeys.ROLE_INSTANCES);
      component.getMandatoryOption( SCRIPT_PATH);
      // Extra validation for directly executed START
      if (!roleName.equals(ROLE_NODE)) {
        component.getMandatoryOption(SERVICE_NAME);
        component.getMandatoryOption(APP_HOME);
      }

      int priority =
        component.getMandatoryOptionInt(RoleKeys.ROLE_PRIORITY);
      if (priority <= 0) {
        throw new BadConfigException("role %s %s value out of range %d",
                                     roleName,
                                     RoleKeys.ROLE_PRIORITY,
                                     priority);
      }

      String existing = priorityMap.get(priority);
      if (existing != null) {
        throw new BadConfigException(
          "role %s has a %s value %d which duplicates that of %s",
          roleName,
          RoleKeys.ROLE_PRIORITY,
          priority,
          existing);
      }
      priorityMap.put(priority, roleName);
    }
  }

  @Override
  public void prepareAMAndConfigForLaunch(HoyaFileSystem hoyaFileSystem,
                                          Configuration serviceConf,
                                          AbstractLauncher launcher,
                                          AggregateConf instanceDescription,
                                          Path originConfDirPath,
                                          Path generatedConfDirPath,
                                          Configuration clientConfExtras,
                                          String libdir,
                                          Path tempPath) throws
                                                         IOException,
                                                         HoyaException {

    //load in the template site config
    log.debug("Loading template configuration from {}, saving to ",
              originConfDirPath, generatedConfDirPath);

    Path commandJson =
      new Path(originConfDirPath, AgentKeys.COMMAND_JSON_FILENAME);
    hoyaFileSystem.verifyFileExists(commandJson);


    Map<String, LocalResource> providerResources;
    launcher.submitDirectory(generatedConfDirPath,
                             HoyaKeys.PROPAGATED_CONF_DIR_NAME);

  }


}
