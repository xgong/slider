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

package org.apache.hadoop.hoya.yarn.service;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hoya.api.ClusterDescription;
import org.apache.hadoop.hoya.exceptions.HoyaException;
import org.apache.hadoop.hoya.exec.ApplicationEventHandler;
import org.apache.hadoop.hoya.exec.RunLongLivedApp;
import org.apache.hadoop.hoya.tools.HoyaUtils;
import org.apache.hadoop.hoya.yarn.appmaster.HoyaAppMaster;
import org.apache.hadoop.service.AbstractService;
import org.apache.hadoop.service.Service;
import org.apache.hadoop.util.ExitUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Service wrapper for an external program that is launched and can/will terminate.
 * It will notify the owner on events
 */
public class ForkedProcessService extends AbstractService implements
                                                          ApplicationEventHandler {

  /**
   * Log for the forked master process
   */
  protected static final Logger log =
    LoggerFactory.getLogger(ForkedProcessService.class);

  private final String name;
  private final Service owner;
  private final ClusterDescription clusterSpec;
  private final boolean earlyExitIsFailure;
  private boolean processTerminated = false;
  private boolean processStarted = false;
  private boolean processTerminatedBeforeServiceStopped = false;
  private RunLongLivedApp process;
  private Map<String, String> environment;
  private List<String> commands;
  private String commandLine;

  /**
   * Exit code set when the spawned process exits
   */
  private int exitCode;

  public ForkedProcessService(Service owner,
                              String name,
                              ClusterDescription clusterSpec,
                              boolean earlyExitIsFailure) {
    super("name");
    this.name = name;
    this.owner = owner;
    this.clusterSpec = clusterSpec;
    this.earlyExitIsFailure =
      earlyExitIsFailure;
  }

  @Override
  protected void serviceInit(Configuration conf) throws Exception {
    super.serviceInit(conf);
  }


  @Override
  protected void serviceStart() throws Exception {
  }


  @Override
  protected void serviceStop() throws Exception {
    if (process != null) {
      process.stop();
    }
  }

  /**
   * Exec the process
   * @param commands list of commands is inserted on the front
   * @param env environment variables above those generated by
   * @throws IOException IO problems
   * @throws HoyaException anything internal
   */
  public void exec(Map<String, String> environment,
                   List<String> commands) throws
                                          IOException,
                                          HoyaException {
    assert isInState(STATE.STARTED);
    assert process == null;
    this.commands = commands;
    this.commandLine = HoyaUtils.join(commands, " ");
    this.environment = environment;
    process = new RunLongLivedApp(log, commands);
    process.setApplicationEventHandler(this);
    //set the env variable mapping
    process.putEnvMap(environment);

    //now spawn the process -expect updates via callbacks
    process.spawnApplication();

  }

  @Override // ApplicationEventHandler
  public synchronized void onApplicationStarted(RunLongLivedApp application) {
    log.info("Process has started");
    processStarted = true;
  }

  @Override // ApplicationEventHandler
  public void onApplicationExited(RunLongLivedApp application,
                                  int exitCode) {
    synchronized (this) {
      processTerminated = true;
      this.exitCode = exitCode;
      //note whether or not the service had already stopped
      processTerminatedBeforeServiceStopped =
        getServiceState() != STATE.STOPPED;
      log.info("Process has exited with exit code {}", exitCode);
      if (exitCode != 0 && getFailureCause() != null) {
        //error
        ExitUtil.ExitException ee =
          new ExitUtil.ExitException(exitCode,
                                     name + " exited with code " +
                                     exitCode);
        ee.initCause(getFailureCause());
        noteFailure(ee);
      }
    }
    //now stop itself
    if (!isInState(STATE.STOPPED)) {
      stop();
    }
  }

  /**
   * flag for use by the AM
   * @return the status
   */
  public boolean isEarlyExitIsFailure() {
    return earlyExitIsFailure;
  }

  public synchronized boolean isProcessTerminated() {
    return processTerminated;
  }

  public synchronized boolean isProcessStarted() {
    return processStarted;
  }


  public int getExitCode() {
    return exitCode;
  }

  public String getCommandLine() {
    return commandLine;
  }

  /**
   * Get the recent output from the process, or [] if not defined
   * @return a possibly empty list
   */
  public List<String> getRecentOutput() {
    return process != null
           ? process.getRecentOutput()
           : new LinkedList<String>();
  }

}
