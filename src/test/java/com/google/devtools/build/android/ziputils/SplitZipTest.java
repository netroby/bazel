// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.android.ziputils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

/**
 * Unit tests for {@link SplitZip}.
 */
@RunWith(JUnit4.class)
public class SplitZipTest {
  private FakeFileSystem fileSystem;

  @Before
  public void setUp() {
    fileSystem = new FakeFileSystem();
  }

  @Test
  public void test() {
    SplitZip instance = new SplitZip();
    assertThat(instance.getMainClassListFile()).isNull();
    assertThat(instance.isVerbose()).isFalse();
    assertThat(instance.getEntryDate()).isNull();
    assertThat(instance.getResourceFile()).isNull();
  }

  @Test
  public void testSetOutput() {
    SplitZip instance = new SplitZip();
    try {
      instance.addOutput((String) null);
      fail("should have failed");
    } catch (Exception ex) {
      assertTrue("NullPointerException expected", ex instanceof NullPointerException);
    }
    try {
      SplitZip result = instance
          .addOutput(new ZipOut(fileSystem.getOutputChannel("out/shard1.jar", false),
              "out/shard1.jar"))
          .addOutput(new ZipOut(fileSystem.getOutputChannel("out/shard2.jar", false),
              "out/shard2.jar"));
      assertSame(instance, result);
    } catch (IOException ex) {
      fail("Unexpected exception: " + ex);
    }
  }

  @Test
  public void testSetResourceFile() {
    SplitZip instance = new SplitZip();
    String res = "res";
    SplitZip result = instance.setResourceFile(res);
    assertSame(instance, result);
  }

  @Test
  public void testGetResourceFile() {
    SplitZip instance = new SplitZip();
    String res = "res";
    assertThat(instance.setResourceFile(res).getResourceFile()).isEqualTo(res);
    assertThat(instance.setResourceFile((String) null).getResourceFile()).isNull();
  }

  @Test
  public void testSetMainClassListFile() {
    SplitZip instance = new SplitZip();
    SplitZip result = instance.setMainClassListFile((String) null);
    assertSame(instance, result);
    result = instance.setMainClassListFile("no format checks");
    assertSame(instance, result);
  }

  @Test
  public void testGetMainClassListFile() {
    SplitZip instance = new SplitZip();
    String file = "list.txt";
    instance.setMainClassListFile(file);
    String result = instance.getMainClassListFile();
    assertThat(result).isEqualTo(file);
  }

  // Instance date test. Implementation has little constraints today.
  // This should be improved.
  @Test
  public void testSetEntryDate() {
    SplitZip instance = new SplitZip();
    SplitZip result = instance.setEntryDate(null);
    assertSame(instance, result);
  }

  @Test
  public void testGetEntryDate() {
    SplitZip instance = new SplitZip();
    Date now = new Date();
    instance.setEntryDate(now);
    Date result = instance.getEntryDate();
    assertSame(result, now);
    instance.setEntryDate(null);
    assertThat(instance.getEntryDate()).isNull();
  }

  @Test
  public void testUseDefaultEntryDate() {
    SplitZip instance = new SplitZip();
    SplitZip result = instance.useDefaultEntryDate();
    assertSame(instance, result);
    Date date = instance.getEntryDate();
    assertThat(date).isEqualTo(DosTime.DOS_EPOCH);
  }

  @Test
  public void testAddInput() {
    try {
      SplitZip instance = new SplitZip();
      String noexists = "noexists.zip";
      instance.addInput(noexists);
      fail("should not be able to add non existing file: " + noexists);
    } catch (IOException ex) {
      assertTrue("FileNotFoundException expected", ex instanceof FileNotFoundException);
    }
  }

  @Test
  public void testAddInputs() {
    try {
      SplitZip instance = new SplitZip();
      String noexists = "noexists.zip";
      instance.addInputs(Arrays.asList(noexists));
      fail("should not be able to add non existing file: " + noexists);
    } catch (IOException ex) {
      assertTrue("FileNotFoundException expected", ex instanceof FileNotFoundException);
    }
  }

  @Test
  public void testCopyOneDir() {
    try {
      new ZipFileBuilder()
          .add("pkg/test.txt", "hello world")
          .create("input.zip");
      byte[] inputBytes = fileSystem.toByteArray("input.zip");

      new SplitZip()
          .addOutput(new ZipOut(fileSystem.getOutputChannel("out/shard1.jar", false),
              "out/shard1.jar"))
          .setVerbose(true)
          .addInput(new ZipIn(fileSystem.getInputChannel("input.zip"), "input.zip"))
          .run()
          .close();

      byte[] outputBytes = fileSystem.toByteArray("out/shard1.jar");
      assertThat(inputBytes).isEqualTo(outputBytes);
    } catch (IOException e) {
      fail("Exception: " + e);
    }
  }

  @Test
  public void testSetDate() {
    try {
      Date now = new Date();
      new ZipFileBuilder()
          .add(new ZipFileBuilder.FileInfo("pkg/test.txt", new DosTime(now).time, "hello world"))
          .create("input.zip");

      new ZipFileBuilder()
          .add(new ZipFileBuilder.FileInfo("pkg/test.txt", DosTime.EPOCH.time, "hello world"))
          .create("expect.zip");
      byte[] expectBytes = fileSystem.toByteArray("expect.zip");

      new SplitZip()
          .addOutput(new ZipOut(fileSystem.getOutputChannel("out/shard1.jar", false),
              "out/shard1.jar"))
          .setVerbose(true)
          .setEntryDate(DosTime.DOS_EPOCH)
          .addInput(new ZipIn(fileSystem.getInputChannel("input.zip"), "input.zip"))
          .run()
          .close();

      byte[] outputBytes = fileSystem.toByteArray("out/shard1.jar");
      assertThat(expectBytes).isEqualTo(outputBytes);
    } catch (IOException e) {
      fail("Exception: " + e);
    }
  }

  @Test
  public void testDuplicatedInput() {
    try {
      new ZipFileBuilder()
          .add("pkg/test.txt", "hello world")
          .create("input1.zip");

      new ZipFileBuilder()
          .add("pkg/test.txt", "Goodbye world")
          .create("input2.zip");

      new SplitZip()
          .addOutput(new ZipOut(fileSystem.getOutputChannel("out/shard1.jar", false),
              "out/shard1.jar"))
          .setVerbose(true)
          .addInput(new ZipIn(fileSystem.getInputChannel("input1.zip"), "input1.zip"))
          .addInput(new ZipIn(fileSystem.getInputChannel("input2.zip"), "input2.zip"))
          .run()
          .close();

      new ZipFileBuilder()
          .add("pkg/test.txt", "hello world")
          .create("expect.zip");
      byte[] expectBytes = fileSystem.toByteArray("expect.zip");
      byte[] outputBytes = fileSystem.toByteArray("out/shard1.jar");
      assertThat(expectBytes).isEqualTo(outputBytes);
    } catch (IOException e) {
      fail("Exception: " + e);
    }
  }

  @Test
  public void testCopyThreeDir() {
    try {
      new ZipFileBuilder()
          .add("pkg/hello.txt", "hello world")
          .add("pkg/greet.txt", "how are you")
          .add("pkg/bye.txt", "bye bye")
          .create("input.zip");
      byte[] inputBytes = fileSystem.toByteArray("input.zip");

      new SplitZip()
          .addOutput(new ZipOut(fileSystem.getOutputChannel("out/shard1.jar", false),
              "out/shard1.jar"))
          .setVerbose(true)
          .addInput(new ZipIn(fileSystem.getInputChannel("input.zip"), "input.zip"))
          .run()
          .close();

      byte[] outputBytes = fileSystem.toByteArray("out/shard1.jar");
      assertThat(inputBytes).isEqualTo(outputBytes);
    } catch (IOException e) {
      fail("Exception: " + e);
    }
  }

  @Test
  public void testSplitInTwo() {
    try {
      new ZipFileBuilder()
          .add("pkg1/test1.class", "hello world")
          .add("pkg2/test1.class", "hello world")
          .add("pkg1/test2.class", "how are you")
          .add("pkg2/test2.class", "how are you")
          .add("pkg1/test3.class", "bye bye")
          .add("pkg2/test3.class", "bye bye")
          .create("input.jar");

      new SplitZip()
          .addOutput(new ZipOut(fileSystem.getOutputChannel("out/shard1.jar", false),
              "out/shard1.jar"))
          .addOutput(new ZipOut(fileSystem.getOutputChannel("out/shard2.jar", false),
              "out/shard2.jar"))
          .setVerbose(true)
          .addInput(new ZipIn(fileSystem.getInputChannel("input.jar"), "input.jar"))
          .run()
          .close();

      new ZipFileBuilder()
          .add("pkg1/test1.class", "hello world")
          .add("pkg1/test2.class", "how are you")
          .add("pkg1/test3.class", "bye bye")
          .create("expected/shard1.jar");
      new ZipFileBuilder()
          .add("pkg2/test1.class", "hello world")
          .add("pkg2/test2.class", "how are you")
          .add("pkg2/test3.class", "bye bye")
          .create("expected/shard2.jar");

      assertThat(fileSystem.toByteArray("out/shard1.jar"))
          .isEqualTo(fileSystem.toByteArray("expected/shard1.jar"));

      assertThat(fileSystem.toByteArray("out/shard2.jar"))
          .isEqualTo(fileSystem.toByteArray("expected/shard2.jar"));

    } catch (IOException e) {
      fail("Exception: " + e);
    }
  }

  @Test
  public void testSeparateResources() {
    try {
      new ZipFileBuilder()
          .add("resources/oil.xml", "oil")
          .add("pkg1/test1.class", "hello world")
          .add("pkg2/test1.class", "hello world")
          .add("pkg1/test2.class", "how are you")
          .add("pkg2/test2.class", "how are you")
          .add("pkg1/test3.class", "bye bye")
          .add("pkg2/test3.class", "bye bye")
          .create("input.jar");
      ZipIn input = new ZipIn(fileSystem.getInputChannel("input.jar"), "input.jar");

      String resources = "out/resources.zip";
      ZipOut resourceOut = new ZipOut(fileSystem.getOutputChannel(resources, false), resources);
      new SplitZip()
          .addOutput(new ZipOut(fileSystem.getOutputChannel("out/shard1.jar", false),
              "out/shard1.jar"))
          .addOutput(new ZipOut(fileSystem.getOutputChannel("out/shard2.jar", false),
              "out/shard2.jar"))
          .setResourceFile(resourceOut)
          .setVerbose(true)
          .addInput(input)
          .run()
          .close();

      new ZipFileBuilder()
          .add("pkg1/test1.class", "hello world")
          .add("pkg1/test2.class", "how are you")
          .add("pkg1/test3.class", "bye bye")
          .create("expected/shard1.jar");
      new ZipFileBuilder()
          .add("pkg2/test1.class", "hello world")
          .add("pkg2/test2.class", "how are you")
          .add("pkg2/test3.class", "bye bye")
          .create("expected/shard2.jar");
      new ZipFileBuilder()
          .add("resources/oil.xml", "oil")
          .create("expected/resources.zip");


      assertThat(fileSystem.toByteArray("out/shard1.jar"))
          .isEqualTo(fileSystem.toByteArray("expected/shard1.jar"));

      assertThat(fileSystem.toByteArray("out/shard2.jar"))
          .isEqualTo(fileSystem.toByteArray("expected/shard2.jar"));

      assertThat(fileSystem.toByteArray("out/resources.zip"))
          .isEqualTo(fileSystem.toByteArray("expected/resources.zip"));

    } catch (IOException e) {
      e.printStackTrace();
      fail("Exception: " + e);
    }
  }

  @Test
  public void testMainClassListFile() {
    SplitZip instance = new SplitZip();
    String filename = "x/y/z/foo.txt";
    instance.setMainClassListFile(filename);
    String out = instance.getMainClassListFile();
    assertThat(out).isEqualTo(filename);

    instance.setMainClassListFile((String) null);
    assertThat(instance.getMainClassListFile()).isNull();

    try {
      new ZipFileBuilder()
          .add("pkg1/test1.class", "hello world")
          .add("pkg2/test1.class", "hello world")
          .add("pkg1/test2.class", "how are you")
          .add("pkg2/test2.class", "how are you")
          .add("pkg1/test3.class", "bye bye")
          .add("pkg2/test3.class", "bye bye")
          .create("input.jar");

      String classFileList = "pkg1/test1.class\npkg2/test2.class\n";
      fileSystem.addFile("main_dex_list.txt", classFileList);

      new SplitZip()
          .addOutput(new ZipOut(fileSystem.getOutputChannel("out/shard1.jar", false),
              "out/shard1.jar"))
          .addOutput(new ZipOut(fileSystem.getOutputChannel("out/shard2.jar", false),
              "out/shard2.jar"))
          .setMainClassListFile(fileSystem.getInputStream("main_dex_list.txt"))
          .addInput(new ZipIn(fileSystem.getInputChannel("input.jar"), "input.jar"))
          .run()
          .close();

      new ZipFileBuilder()
          .add("pkg1/test1.class", "hello world")
          .add("pkg2/test2.class", "how are you")
          .create("expected/shard1.jar");

      // Sorting is used for split calculation, but classes assigned to the same shard are expected
      // to be output in the order they appear in input.
      new ZipFileBuilder()
          .add("pkg2/test1.class", "hello world")
          .add("pkg1/test2.class", "how are you")
          .add("pkg1/test3.class", "bye bye")
          .add("pkg2/test3.class", "bye bye")
          .create("expected/shard2.jar");

      assertThat(fileSystem.toByteArray("out/shard1.jar"))
          .isEqualTo(fileSystem.toByteArray("expected/shard1.jar"));

      assertThat(fileSystem.toByteArray("out/shard2.jar"))
          .isEqualTo(fileSystem.toByteArray("expected/shard2.jar"));

    } catch (IOException e) {
      fail("Exception: " + e);
    }
  }

  @Test
  public void testVerbose() {
    SplitZip instance = new SplitZip();
    instance.setVerbose(true);
    assertThat(instance.isVerbose()).isTrue();
    instance.setVerbose(false);
    assertThat(instance.isVerbose()).isFalse();
  }
}
