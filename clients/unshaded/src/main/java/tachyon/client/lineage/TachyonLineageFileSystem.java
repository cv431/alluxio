/*
 * Licensed to the University of California, Berkeley under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package tachyon.client.lineage;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tachyon.Constants;
import tachyon.TachyonURI;
import tachyon.annotation.PublicApi;
import tachyon.client.file.FileOutStream;
import tachyon.client.file.TachyonFileSystem;
import tachyon.client.file.options.OutStreamOptions;
import tachyon.exception.TachyonException;
import tachyon.thrift.LineageDoesNotExistException;

/**
 * Tachyon lineage file system client. This class provides lineage support in the file system
 * operations.
 */
@PublicApi
public class TachyonLineageFileSystem extends TachyonFileSystem {
  private static final Logger LOG = LoggerFactory.getLogger(Constants.LOGGER_TYPE);

  private static TachyonLineageFileSystem sTachyonFileSystem;
  private LineageContext mContext;

  public static synchronized TachyonLineageFileSystem get() {
    if (sTachyonFileSystem == null) {
      sTachyonFileSystem = new TachyonLineageFileSystem();
    }
    return sTachyonFileSystem;
  }

  protected TachyonLineageFileSystem() {
    super();
    mContext = LineageContext.INSTANCE;
  }

  /**
   * A file is created when its lineage is added. This method reinitializes the created file. But
   * it's no-op if the file is already completed.
   *
   * @return the id of the reinitialized file when the file is lost or not completed, -1 otherwise.
   * @throws LineageDoesNotExistException if the lineage does not exist.
   */
  private long recreate(TachyonURI path, OutStreamOptions options)
      throws LineageDoesNotExistException {
    LineageMasterClient masterClient = mContext.acquireMasterClient();
    try {
      long fileId =
          masterClient.recreateFile(path.getPath(), options.getBlockSize(), options.getTTL());
      return fileId;
    } catch (IOException e) {
      throw new RuntimeException("recreation failed", e);
    } finally {
      mContext.releaseMasterClient(masterClient);
    }
  }

  /**
   * Gets the output stream for lineage job. If the file already exists on master, returns a dummy
   * output stream.
   */
  @Override
  public FileOutStream getOutStream(TachyonURI path, OutStreamOptions options)
      throws IOException, TachyonException {
    long fileId;
    try {
      fileId = recreate(path, options);
    } catch (LineageDoesNotExistException e) {
      // not a lineage file
      return super.getOutStream(path, options);
    }
    if (fileId < 0) {
      return new DummyFileOutputStream(fileId, options);
    }
    return new LineageFileOutStream(fileId, options);
  }
}
