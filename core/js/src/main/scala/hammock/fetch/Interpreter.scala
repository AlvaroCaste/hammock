package hammock
package fetch

import cats.~>
import cats.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.scalajs.npm.nodefetch._
import scala.scalajs.js.Promise
import cats.syntax.show._
import scala.scalajs.js.JSConverters._

object Interpreter {

  def apply[F[_]](implicit F: InterpTrans[F]): InterpTrans[F] = F

  implicit def instance[F[_]: Async: ContextShift](
      implicit nodeFetch: NodeFetch = io.scalajs.npm.nodefetch.NodeFetch): InterpTrans[F] = new InterpTrans[F] {

    def trans: HttpF ~> F = new (HttpF ~> F) {
      def apply[A](http: HttpF[A]): F[A] = {
        val method = http match {
          case Get(_)     => Method.GET
          case Delete(_)  => Method.DELETE
          case Head(_)    => Method.HEAD
          case Options(_) => Method.OPTIONS
          case Post(_)    => Method.POST
          case Put(_)     => Method.PUT
          case Trace(_)   => Method.TRACE
          case Patch(_)   => Method.PATCH
        }
        http match {
          case Get(_) | Options(_) | Delete(_) | Head(_) | Options(_) | Trace(_) | Post(_) | Put(_) | Patch(_) =>
            for {
              response <- Async.fromFuture(Async[F].delay {
                val headers = http.req.headers.toJSDictionary
                nodeFetch(
                  http.req.uri.show,
                  http.req.entity
                    .flatMap(
                      _.cata(
                        string => Some(string.content),
                        bytes => Some(bytes.content.map(_.toChar).mkString),
                        _ => None
                      ))
                    .map(body => new RequestOptions(body = body, headers = headers, method = method.name))
                    .getOrElse(
                      new RequestOptions(
                        headers = headers,
                        method = method.name
                      ))
                ).toFuture
              })
              entity <- Async.fromFuture(Async[F].delay(response.text().asInstanceOf[Promise[String]].toFuture))
            } yield HttpResponse(Status.Statuses(response.status), response.headers.toMap, Entity.StringEntity(entity))
        }
      }
    }
  }
}
