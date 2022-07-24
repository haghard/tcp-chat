// Copyright (c) 2022 by Vadim Bondarev
// This software is licensed under the Apache License, Version 2.0.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

package server

import java.net.{ InetAddress, NetworkInterface }

import com.typesafe.config.{ Config, ConfigFactory }

import scala.collection.{ Map, Seq }
import scala.io.Source
import scala.util.Try
import scala.jdk.CollectionConverters.*

trait Ops:

  private val Opt = """(\S+)=(\S+)""".r

  def argsToOpts(args: Seq[String]): Map[String, String] =
    args.collect { case Opt(key, value) => key -> value }.toMap
