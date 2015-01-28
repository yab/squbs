/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.testkit.stress

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{FunSpecLike, Matchers}

import scala.concurrent.duration._

class LoadActorSpec extends TestKit(ActorSystem("LoadActorSpec"))
with ImplicitSender with FunSpecLike with Matchers {

  val warmUp = 20 seconds
  val steady = 40 seconds

  it ("Shall achieve the requested large TPS and report proper CPU statistics") {
    val ir = 500
    val startTime = System.nanoTime()
    val loadActor = system.actorOf(Props[LoadActor])
    val statsActor = system.actorOf(Props[CPUStatsActor])
    loadActor ! StartLoad(startTime, ir, warmUp, steady, { () =>
      system.actorOf(Props[LoadTestActor]) ! TestPing
    })
    statsActor ! StartStats(startTime, warmUp, steady, 1 seconds)

    var responseCount = 0l

    for (i <- 0 to 1) {
      fishForMessage(warmUp + steady + (20 seconds)) {
        case LoadStats(tps) =>
          println(s"Achieved $tps TPS")
          println(s"Response count in steady state: $responseCount")
          tps should be > (ir * 0.95) // Within 5% of IR
          responseCount should be > (ir * steady.toSeconds * 0.95).toLong
          true

        case CPUStats(avg, sDev) =>
          println(s"CPULoad $avg; Standard Deviation $sDev")
          avg should be > 0.0
          true

        case TestPong =>
          val currentTime = System.nanoTime() - startTime
          if (currentTime > warmUp.toNanos && currentTime <= (warmUp + steady).toNanos) {
            responseCount += 1
          }
          false
      }
    }
    loadActor ! PoisonPill
    statsActor ! PoisonPill
  }

  it ("Shall achieve the requested small TPS and report proper CPU statistics") {
    val ir = 10
    val startTime = System.nanoTime()
    val loadActor = system.actorOf(Props[LoadActor])
    val statsActor = system.actorOf(Props[CPUStatsActor])
    loadActor ! StartLoad(startTime, ir, warmUp, steady, { () =>
      system.actorOf(Props[LoadTestActor]) ! TestPing
    })
    statsActor ! StartStats(startTime, warmUp, steady, 1 seconds)

    var responseCount = 0l

    for (i <- 0 to 1) {
      fishForMessage(warmUp + steady + (20 seconds)) {
        case LoadStats(tps) =>
          println(s"Achieved $tps TPS")
          println(s"Response count in steady state: $responseCount")
          tps should be > (ir * 0.95) // Within 5% of IR
          responseCount should be > (ir * steady.toSeconds * 0.95).toLong
          true

        case CPUStats(avg, sDev) =>
          println(s"CPULoad $avg; Standard Deviation $sDev")
          avg should be > 0.0
          true

        case TestPong =>
          val currentTime = System.nanoTime() - startTime
          if (currentTime > warmUp.toNanos && currentTime <= (warmUp + steady).toNanos) {
            responseCount += 1
          }
          false
      }
    }
    loadActor ! PoisonPill
    statsActor ! PoisonPill
  }
}

case object TestPing
case object TestPong

class LoadTestActor extends Actor {
  def receive = {
    case TestPing => sender() ! TestPong
  }
}
