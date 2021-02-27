/*
 *  Copyright 2019 Zhong Lunfu
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package zhongl.passport

import java.net.{Inet4Address, InetAddress, NetworkInterface}

import akka.http.scaladsl.model.RemoteAddress

import scala.jdk.CollectionConverters._

object NetworkInterfaces {
  implicit val string2RemoteAddress: String => RemoteAddress = s => RemoteAddress(InetAddress.getByName(s))

  def localAddress(name: String): Option[RemoteAddress] = {
    NetworkInterface
      .getByName(name)
      .getInetAddresses
      .asScala
      .find(_.isInstanceOf[Inet4Address])
      .map(RemoteAddress(_))
  }

  def findFirstNetworkInterfaceHasInet4Address: Option[NetworkInterface] = {

    @inline
    def hasInet4Address(i: NetworkInterface) = i.getInetAddresses.asScala.exists(_.isInstanceOf[Inet4Address])

    NetworkInterface.getNetworkInterfaces.asScala.find(hasInet4Address)
  }
}
