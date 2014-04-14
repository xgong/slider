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

package org.apache.hoya.yarn.service;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.service.AbstractService;
import org.apache.hadoop.service.ServiceStateException;
import org.apache.hadoop.yarn.service.launcher.ExitCodeProvider;
import org.apache.hadoop.yarn.service.launcher.ServiceLaunchException;
import org.apache.hoya.exceptions.HoyaException;
import org.apache.hoya.exec.ApplicationEventHandler;
import org.apache.hoya.exec.RunLongLivedApp;
import org.apache.hoya.tools.HoyaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service wrapper for an external program that is launched and can/will terminate.
 * This service is notified when the subprocess terminates, and stops itself 
 * and converts a non-zero exit code into a failure exception
 */
public class ForkedProcessService extends AbstractService implements
                                                          ApplicationEventHandler,
                                                          ExitCodeProvider,
                                                          Runnable {

  /**
   * Log for the forked master process
   */
  protected static final Logger log =
    LoggerFactory.getLogger(ForkedProcessService.class);

  private final String name;
  private final AtomicBoolean processTerminated = new AtomicBoolean(false);
  ;
  private boolean processStarted = false;
  private RunLongLivedApp process;
  private Map<String, String> environment;
  private List<String> commands;
  private String commandLine;
  private int executionTimeout = -1;
  private int timeoutCode = 1;

  /**
   * Exit code set when the spawned process exits
   */
  private AtomicInteger exitCode = new AtomicInteger(0);
  private Thread timeoutThread;

  public ForkedProcessService(String name) {
    super(name);
    this.name = name;
  }

  @Override //AbstractService
  protected void serviceInit(Configuration conf) throws Exception {
    super.serviceInit(conf);
  }

  @Override //AbstractService
  protected void serviceStart() throws Exception {
    if (process == null) {
      throw new ServiceStateException("Subprocess not yet configured");
    }
    //now spawn the process -expect updates via callbacks
    process.spawnApplication();
  }

  @Override //AbstractService
  protected void serviceStop() throws Exception {
    completed(0);
    if (process != null) {
      process.stop();
    }
  }

  /**
   * Set the timeout by which time a process must have finished -or -1 for forever
   * @param timeout timeout in milliseconds
   */
  public void setTimeout(int timeout, int code) {
    this.executionTimeout = timeout;
    this.timeoutCode = code;
  }

  /**
   * Build the process to execute when the service is started
   * @param commands list of commands is inserted on the front
   * @param env environment variables above those generated by
   * @throws IOException IO problems
   * @throws HoyaException anything internal
   */
  public void build(Map<String, String> environment,
                    List<String> commands) throws
                                           IOException,
                                           HoyaException {
    assert process == null;
    this.commands = commands;
    this.commandLine = HoyaUtils.join(commands, " ", false);
    this.environment = environment;
    process = new RunLongLivedApp(log, commands);
    process.setApplicationEventHandler(this);
    //set the env variable mapping
    process.putEnvMap(environment);
  }

  @Override // ApplicationEventHandler
  public synchronized void onApplicationStarted(RunLongLivedApp application) {
    log.info("Process has started");
    processStarted = true;
    if (executionTimeout > 0) {
      timeoutThread = new Thread(this);
      timeoutThread.start();
    }
  }

  @Override // ApplicationEventHandler
  public void onApplicationExited(RunLongLivedApp application,
                                  int exitC) {
    synchronized (this) {
      completed(exitC);
      //note whether or not the service had already stopped
      log.info("Process has exited with exit code {}", exitC);
      if (exitC != 0) {
        reportFailure(exitC, name + " failed with code " +
                             exitC);
      }
    }
    //now stop itself
    if (!isInState(STATE.STOPPED)) {
      stop();
    }
  }

  private void reportFailure(int exitC, String text) {
    this.exitCode.set(exitC);
    //error
    ServiceLaunchException execEx =
      new ServiceLaunchException(exitC,
                                 text);
    log.debug("Noting failure", execEx);
    noteFailure(execEx);
  }

  /**
   * handle timeout response by escalating it to a failure
   */
  @Override
  public void run() {
    try {
      synchronized (processTerminated) {
        if (!processTerminated.get()) {
          processTerminated.wait(executionTimeout);
        }
      }

    } catch (InterruptedException e) {
      //assume signalled; exit
    }
    //check the status; if the marker isn't true, bail
    if (!processTerminated.getAndSet(true)) {
      log.info("process timeout: reporting error code {}", timeoutCode);

      //timeout
      if (isInState(STATE.STARTED)) {
        //trigger a failure
        process.stop();
      }
      reportFailure(timeoutCode, name + ": timeout after " + executionTimeout
                   + " millis: exit code =" + timeoutCode);
    }
  }

  protected void completed(int exitCode) {
    this.exitCode.set(exitCode);
    processTerminated.set(true);
    synchronized (processTerminated) {
      processTerminated.notify();
    }
  }

  public boolean isProcessTerminated() {
    return processTerminated.get();
  }

  public synchronized boolean isProcessStarted() {
    return processStarted;
  }


  @Override // ExitCodeProvider
  public int getExitCode() {
    return exitCode.get();
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
