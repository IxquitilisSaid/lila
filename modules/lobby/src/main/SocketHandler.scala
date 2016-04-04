package lila.lobby

import akka.actor._
import akka.pattern.{ ask, pipe }
import play.api.libs.json._
import scala.concurrent.duration._

import actorApi._
import lila.common.PimpedJson._
import lila.hub.actorApi.lobby._
import lila.socket.actorApi._
import lila.socket.Handler
import lila.user.User
import makeTimeout.short

private[lobby] final class SocketHandler(
    hub: lila.hub.Env,
    lobby: ActorRef,
    socket: ActorRef,
    blocking: String => Fu[Set[String]])(implicit system: ActorSystem) {

  private def controller(
    socket: ActorRef,
    uid: String,
    member: Member): Handler.Controller = {
    case ("p", o) => o int "v" foreach { v => socket ! PingVersion(uid, v) }
    case ("join", o) =>
      o str "d" foreach { id =>
        lobby ! BiteHook(id, uid, member.user)
      }
    case ("cancel", o) =>
      lobby ! CancelHook(uid) case ("joinSeek", o) => for {
      id <- o str "d"
      user <- member.user
    } lobby ! BiteSeek(id, user)
    case ("cancelSeek", o) => for {
      id <- o str "d"
      user <- member.user
    } lobby ! CancelSeek(id, user)
  }

  def apply(uid: String, user: Option[User], mobile: Boolean): Fu[JsFlow] =
    (user ?? (u => blocking(u.id))) map { blockedUserIds =>
      Handler.actorRef { out =>
        val member = Member(out, user, blockedUserIds, uid, mobile)
        socket ! AddMember(uid, member)
        Handler.props(out)(hub, socket, member, uid, user.map(_.id)) {
          controller(socket, uid, member)
        }
      }
    }
}
