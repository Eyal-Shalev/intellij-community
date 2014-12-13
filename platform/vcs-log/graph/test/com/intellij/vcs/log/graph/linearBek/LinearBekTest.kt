/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.linearBek

import org.junit.Test
import com.intellij.vcs.log.graph.TestGraphBuilder
import com.intellij.vcs.log.graph.graph
import org.junit.Assert.assertEquals
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutBuilder
import com.intellij.vcs.log.graph.asString

class LinearBekTest {

  fun runTest(beforeLinearBekBuilder: TestGraphBuilder.() -> Unit, afterLinearBekBuilder: TestGraphBuilder.() -> Unit) {
    val beforeLinearBek = graph(beforeLinearBekBuilder)
    val beforeLinearBekLayout = GraphLayoutBuilder.build(beforeLinearBek, {(nodeIndex1, nodeIndex2) -> nodeIndex1 - nodeIndex2 })

    val afterLinearBekExpected = graph(afterLinearBekBuilder)
    val afterLinearBek = LinearBekController.compileGraph(beforeLinearBek, beforeLinearBekLayout)
    assertEquals(afterLinearBekExpected.asString(), afterLinearBek.asString())
  }

  /*
  0
  | \
  | 1
  4 |
  5 |
  6 |
  |/
  7
  */
  Test fun simpleGraphTest() = runTest({
    0(4, 1)
    1(2)
    2(3)
    3(7)
    4(5)
    5(6)
    6(7)
    7()
  }, {
    0(1)
    1(2)
    2(3)
    3(4.dot)
    4(5)
    5(6)
    6(7)
    7()
  })

  /*
  0
  |\
  | 1
  2 |
  |/
  3
  |\
  | 4
  5 |
  |/
  6
   */
  Test fun twoSectionsGraphTest() = runTest({
    0(2, 1)
    1(3)
    2(3)
    3(5, 4)
    4(6)
    5(6)
    6()
  }, {
    0(1)
    1(2.dot)
    2(3)
    3(4)
    4(5.dot)
    5(6)
    6()
  })

  /*
  0
  |\
  | 1
  | |\
  | | 2
  | 3 |
  | |/
  | 4
  5 |
  |/
  6
   */
  Test fun recursiveSectionsTest() = runTest({
    0(5, 1)
    1(3, 2)
    2(4)
    3(4)
    4(6)
    5(6)
    6()
  }, {
    0(1)
    1(2)
    2(3.dot)
    3(4)
    4(5.dot)
    5(6)
    6()
  })

  /*
  0
  |\
  | 1
  2 |
  |\|
  | 3
  4 |
  |/
  5
   */
  Test fun diagonalTest() = runTest({
    0(2, 1)
    1(3)
    2(4, 3)
    3(5)
    4(5)
    5()
  }, {
    0(2, 1)
    1(3)
    2(4, 3)
    3(5)
    4(5)
    5()
  })

  /*
  0
  |\
  | 1
  |/|
  2 |
  \|
  3
   */
  Test fun differentDiagonalTest() = runTest({
    0(2, 1)
    1(2, 3)
    2(3)
    3()
  }, {
    0(1)
    1(2, 3)
    2(3)
    3()
  })

  /*
  0
  |\
  | 1
  | |\
  | 2 \
  | |\|
  | | 3
  | \|
  | 4
  5  \
  |\  \
  6 \ |
  |\| |
  | 7 |
  |/  |
  8  /
  |/
  9
   */
  Test fun complicatedBranchesTest() = runTest({
    0(5, 1)
    1(2, 3)
    2(4, 3)
    3(4)
    4(9)
    5(6, 7)
    6(8, 7)
    7(8)
    8(9)
    9()
  }, {
    0(1)
    1(2, 3)
    2(3)
    3(4)
    4(5.dot)
    5(6, 7)
    6(7)
    7(8)
    8(9)
    9()
  })
}