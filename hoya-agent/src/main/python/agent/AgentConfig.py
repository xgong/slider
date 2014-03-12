#!/usr/bin/env python

'''
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
'''

import ConfigParser
import StringIO
import os

config = ConfigParser.RawConfigParser()
content = """

[server]
hostname=localhost
port=8440
secured_port=8441

[agent]
app_pkg_dir=app/pkg
app_pid_dir=app/run
app_log_dir=app/log
app_task_dir=app/data
log_dir=log
log_level=INFO

[python]

[command]
max_retries=2
sleep_between_retries=1

[security]

[heartbeat]
state_interval=6
log_lines_count=300

"""
s = StringIO.StringIO(content)
config.readfp(s)


class AgentConfig:
  APP_PACKAGE_DIR = "app_pkg_dir"
  APP_PID_DIR = "app_pid_dir"
  APP_LOG_DIR = "app_log_dir"
  APP_TASK_DIR = "app_task_dir"
  LOG_DIR = "log_dir"

  SERVER_SECTION = "server"
  AGENT_SECTION = "agent"
  PYTHON_SECTION = "python"
  COMMAND_SECTION = "command"
  SECURITY_SECTION = "security"
  HEARTBEAT_SECTION = "heartbeat"
  agentRoot = "."

  def __init__(self, rootPath, label):
    self.agentRoot = rootPath
    self.label = label

  def getRootPath(self):
    return self.agentRoot

  def getLabel(self):
    return self.label

  def getResolvedPath(self, name):
    global config

    relativePath = config.get(AgentConfig.AGENT_SECTION, name)
    if not os.path.isabs(relativePath):
      return os.path.join(self.agentRoot, relativePath)
    else:
      return relativePath

  def get(self, category, name):
    global config
    return config.get(category, name)

  def setConfig(self, configFile):
    global config
    config.read(configFile)


def main():
  print config


if __name__ == "__main__":
  main()