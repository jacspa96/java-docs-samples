/*
 * Copyright 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.datacatalog;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertNotNull;

import com.google.api.gax.paging.Page;
import com.google.cloud.datacatalog.v1.DataCatalogClient;
import com.google.cloud.datacatalog.v1.DeleteEntryGroupRequest;
import com.google.cloud.datacatalog.v1.EntryGroupName;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.testing.junit4.MultipleAttemptsRule;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class CreateCustomConnectorIT {

  private static final String ID = UUID.randomUUID().toString().substring(0, 8);
  private static final String LOCATION = "us-central1";
  private static final String PROJECT_ID = requireEnvVar("GOOGLE_CLOUD_PROJECT");
  @Rule
  public final MultipleAttemptsRule multipleAttemptsRule = new MultipleAttemptsRule(3);
  private final Logger log = Logger.getLogger(this.getClass().getName());
  private final Storage storageService = StorageOptions.newBuilder().setProjectId(PROJECT_ID)
      .build().getService();
  private String entryGroup;
  private String gcsBucketName;
  private ByteArrayOutputStream bout;
  private PrintStream out;
  private PrintStream originalPrintStream;

  private static String requireEnvVar(String varName) {
    String value = System.getenv(varName);
    assertNotNull("Environment variable " + varName + " is required to perform these tests.",
        System.getenv(varName));
    return value;
  }

  @BeforeClass
  public static void checkRequirements() {
    requireEnvVar("GOOGLE_CLOUD_PROJECT");
  }

  @Before
  public void setUp() throws IOException {
    bout = new ByteArrayOutputStream();
    out = new PrintStream(bout);
    originalPrintStream = System.out;
    System.setOut(out);
    entryGroup = "ENTRY_GROUP_TEST_" + ID;
    gcsBucketName = "bucket_test_" + ID;
    // Create temporary entry group
    CreateEntryGroup.createEntryGroup(PROJECT_ID, LOCATION, entryGroup);
    // Create temporary Google Cloud Storage Bucket
    createTemporaryGcsBucket(gcsBucketName);
  }

  @After
  public void tearDown() throws IOException {
    // Clean up Data Catalog
    try (DataCatalogClient dataCatalogClient = DataCatalogClient.create()) {
      EntryGroupName name = EntryGroupName.of(PROJECT_ID, LOCATION, entryGroup);
      DeleteEntryGroupRequest request =
          DeleteEntryGroupRequest.newBuilder().setName(name.toString()).build();
      dataCatalogClient.deleteEntryGroup(request);
    }
    // Clean up Cloud Storage
    deleteTemporaryGcsBucket(gcsBucketName);
    // restores print statements in the original method
    System.out.flush();
    System.setOut(originalPrintStream);
    log.log(Level.INFO, bout.toString());
  }

  @Ignore
  @Test
  public void testCreateCustomConnector()
      throws IOException, ExecutionException, InterruptedException {
    CreateCustomConnector.importEntriesViaCustomConnector(LOCATION, PROJECT_ID, entryGroup,
        PROJECT_ID, gcsBucketName);
    assertThat(bout.toString()).contains("Long-running operation is created");
  }

  private void createTemporaryGcsBucket(String bucketName) {
    StorageClass storageClass = StorageClass.COLDLINE;
    String location = "ASIA";

    storageService.create(
        BucketInfo.newBuilder(bucketName)
            .setStorageClass(storageClass)
            .setLocation(location)
            .build());
  }

  private void deleteTemporaryGcsBucket(String bucketName) {
    Page<Blob> blobs = storageService.list(bucketName);
    for (Blob blob : blobs.iterateAll()) {
      BlobId blobId = BlobId.of(bucketName, blob.getName());
      storageService.delete(blobId);
    }

    Bucket bucket = storageService.get(gcsBucketName);
    bucket.delete();
  }
}

