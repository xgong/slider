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

package org.apache.hadoop.hoya.tools;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hoya.HoyaKeys;
import org.apache.hadoop.hoya.exceptions.BadConfigException;
import org.apache.hadoop.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.TreeSet;

/**
 * Methods to aid in config, both in the Configuration class and
 * with other parts of setting up Hoya-initated processes
 */
public class ConfigHelper {
  private static final Logger log = LoggerFactory.getLogger(HoyaUtils.class);

  public static final FsPermission CONF_DIR_PERMISSION =
    new FsPermission(FsAction.ALL,
                     FsAction.READ_EXECUTE,
                     FsAction.NONE);

  /**
   * Dump the (sorted) configuration
   * @param conf config
   * @return the sorted keyset
   */
  public static TreeSet<String> dumpConf(Configuration conf) {
    TreeSet<String> keys = sortedConfigKeys(conf);
    for (String key : keys) {
      log.info("{}={}", key, conf.get(key));
    }
    return keys;
  }

  public static TreeSet<String> sortedConfigKeys(Configuration conf) {
    TreeSet<String> sorted = new TreeSet<String>();
    for (Map.Entry<String, String> entry : conf) {
      sorted.add(entry.getKey());
    }
    return sorted;
  }


  /**
   * Set an entire map full of values
   * @param map map
   * @return nothing
   */
  public static void addConfigMap(Configuration config,
                                  Map<String, String> map) throws
                                                           BadConfigException {
    for (Map.Entry<String, String> mapEntry : map.entrySet()) {
      String value = mapEntry.getValue();
      String key = mapEntry.getKey();
      if (value == null) {
        throw new BadConfigException("Null value for property " + key);
      }
      config.set(key, value);
    }
  }


  /**
   * Generate a config file in a destination directory on a given filesystem
   * @param systemConf system conf used for creating filesystems
   * @param confToSave config to save
   * @param confdir the directory path where the file is to go
   * @param filename the filename
   * @return the destination path where the file was saved
   * @throws IOException IO problems
   */
  public static Path generateConfig(Configuration systemConf,
                                    Configuration confToSave,
                                    Path confdir,
                                    String filename) throws IOException {
    FileSystem fs = FileSystem.get(confdir.toUri(), systemConf);
    Path destPath = new Path(confdir, filename);
    saveConfig(fs, destPath, confToSave);
    return destPath;
  }

  /**
   * Save a config
   * @param fs filesystem
   * @param destPath dest to save
   * @param confToSave  config to save
   * @throws IOException IO problems
   */
  public static void saveConfig(FileSystem fs,
                                Path destPath,
                                Configuration confToSave) throws
                                                              IOException {
    FSDataOutputStream fos = fs.create(destPath);
    try {
      confToSave.writeXml(fos);
    } finally {
      IOUtils.closeStream(fos);
    }
  }

  /**
   * Generate a config file in a destination directory on the local filesystem
   * @param confdir the directory path where the file is to go
   * @param filename the filename
   * @return the destination path
   */
  public static File saveConfig(Configuration generatingConf,
                                    File confdir,
                                    String filename) throws IOException {


    File destPath = new File(confdir, filename);
    OutputStream fos = new FileOutputStream(destPath);
    try {
      generatingConf.writeXml(fos);
    } finally {
      IOUtils.closeStream(fos);
    }
    return destPath;
  }

  public static Configuration loadConfFromFile(File file) throws
                                                          MalformedURLException {
    Configuration conf = new Configuration(false);
    conf.addResource(file.toURI().toURL());
    return conf;
  }

  /**
   * looks for the config under $confdir/$templateFilename; if not there
   * loads it from /conf/templateFile.
   * The property {@link HoyaKeys#KEY_HOYA_TEMPLATE_ORIGIN} is set to the
   * origin to help debug what's happening
   * @param systemConf system conf
   * @param confdir conf dir in FS
   * @param templateFilename filename in the confdir
   * @param fallbackResource resource to fall back on
   * @return loaded conf
   * @throws IOException IO problems
   */
  public static Configuration loadTemplateConfiguration(Configuration systemConf,
                                                        Path confdir,
                                                        String templateFilename,
                                                        String fallbackResource) throws
                                                                         IOException {
    FileSystem fs = FileSystem.get(confdir.toUri(), systemConf);

    Path templatePath = new Path(confdir, templateFilename);
    return loadTemplateConfiguration(fs, templatePath, fallbackResource);
  }

  /**
   * looks for the config under $confdir/$templateFilename; if not there
   * loads it from /conf/templateFile.
   * The property {@link HoyaKeys#KEY_HOYA_TEMPLATE_ORIGIN} is set to the
   * origin to help debug what's happening
   * @param fs Filesystem
   * @param templatePath HDFS path for template
   * @param fallbackResource resource to fall back on, or "" for no fallback
   * @return loaded conf
   * @throws IOException IO problems
   * @throws FileNotFoundException if the path doesn't have a file and there
   * was no fallback.
   */
  public static Configuration loadTemplateConfiguration(FileSystem fs,
                                                        Path templatePath,
                                                        String fallbackResource) throws
                                                                                 IOException {
    Configuration conf = new Configuration(false);
    String origin;
    if (fs.exists(templatePath)) {
      log.debug("Loading template {}", templatePath);
      conf.addResource(templatePath.toUri().toURL());
      origin = templatePath.toString();
    } else {
      if (fallbackResource.isEmpty()) {
        throw new FileNotFoundException("No config file found at " + templatePath);
      }
      log.debug("Template {} not found" +
                " -reverting to classpath resource {}", templatePath, fallbackResource);
      conf.addResource(fallbackResource);
      origin = "Resource " + fallbackResource;
    }
    //force a get
    conf.get(HoyaKeys.KEY_HOYA_TEMPLATE_ORIGIN);
    conf.set(HoyaKeys.KEY_HOYA_TEMPLATE_ORIGIN, origin);
    //now set the origin
    return conf;
  }


  /**
   * For testing: dump a configuration
   * @param conf configuration
   * @return listing in key=value style
   */
  public static String dumpConfigToString(Configuration conf) {
    TreeSet<String> sorted = sortedConfigKeys(conf);

    StringBuilder builder = new StringBuilder();
    for (String key : sorted) {

      builder.append(key)
             .append("=")
             .append(conf.get(key))
             .append("\n");
    }
    return builder.toString();
  }

  /**
   * Merge in one configuration above another
   * @param base base config
   * @param merge one to merge. This MUST be a non-default-load config to avoid
   * merge origin confusion
   * @param origin description of the origin for the put operation
   * @return the base with the merged values
   */
  public static Configuration mergeConfigurations(Configuration base, Configuration merge,
                                                  String origin) {
    for (Map.Entry<String, String> entry : merge) {
      base.set(entry.getKey(),entry.getValue(),origin);
    }
    return base;
  }
}
