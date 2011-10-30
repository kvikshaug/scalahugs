package no.oyvindio.sh

import handlers.{JoinNotifier, Echo}
import java.io.IOException
import org.jibble.pircbot.{NickAlreadyInUseException, IrcException, PircBot}
import java.lang.String
import actors.Actor


sealed trait IRCEvent {
  val created = System.nanoTime
  def reply(channel: String,  replyMessage: String) = Reply(this, channel, replyMessage)
}

final case class IRCMessage(channel: String, nick: String, message: String) extends IRCEvent {
  def hasTrigger = message.startsWith("!")
  def trigger = message.split(" ").head
  def args = message.substring(message.indexOf(" "))
  def reply(replyMessage: String) = Reply(this, this.channel, replyMessage)
}

final case class IRCJoin(channel: String, nick: String) extends IRCEvent

final case class Reply(inReplyTo: IRCEvent, channel: String,  message: String) {
  val created = System.nanoTime
  def processingTime = (this.created - inReplyTo.created) / 1000
}

object Scalahugs extends App {
  override def main(args: Array[String]) {
    val bot = new Scalahugs()
    bot.connect()
    bot.start()
    bot.joinChannel("#grouphugs")
  }
}

final class Scalahugs extends PircBot with Actor with Logging {
  private val nicks = Seq("sh", "sh_", "scalahugs")

  val echo = new Echo(this)
  echo.start()
  val join = new JoinNotifier(this)
  join.start()

  private def doConnect(host: String, port: Int): Boolean = {
    log.info("Connecting to %s:%d".format(host, port))
    for (nick <- nicks) {
      setName(nick)
      log.info("Trying nick '%s'".format(nick))
      try {
        connect(host, port)
        return true
      } catch {
        case naiue: NickAlreadyInUseException => log.error("Nick %s already in use".format(getName))
      }
    }
    false
  }

  def connect() {
    val host = "localhost" // TODO: need some sort of configuration
    val port = 6667
    try {
      val success = doConnect(host, port)
      if (success) {
        log.info("Connected as %s".format(getName))
      } else {
        log.error("Unable to connect to %s:%d, none of the configured nicks were available. Tried: %s".format(host, port, nicks))
      }

    } catch {
      case ie: IrcException => log.error("Unable to join server", ie)
      case ioe: IOException => log.error("Unable to connect to server", ioe)
    }
  }

  override def onMessage(channel: String, sender: String, login: String, hostname: String, message: String) {
    log.debug("[%s] %s: %s".format(channel, sender, message))
    echo ! IRCMessage(channel, sender, message)
  }


  override def onJoin(channel: String, sender: String, login: String, hostname: String) {
    log.debug("[%s] %s joined".format(channel, sender))
    join ! IRCJoin(channel, sender)
  }

  def act() {
    loop {
      react {
        case msg: Reply => {
          log.debug("got Reply{channel: %s, message: %s}".format(msg.channel, msg.message))
          log.debug("processing took: %d ms".format(msg.processingTime))
          sendMessage(msg.channel, msg.message)
        }
      }
    }
  }
}
