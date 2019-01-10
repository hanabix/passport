package fun.zhongl.passport
import java.net.{Inet4Address, InetAddress, NetworkInterface}

import akka.http.scaladsl.model.RemoteAddress

import scala.collection.JavaConverters._

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
