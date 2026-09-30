package perf.gatling

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Gatling smoke：健康检查 + 场景 Zone + 战令状态。
 * 运行前将 baseUrl 指向 player-service / gateway。
 */
class MmorpgSmokeSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl(System.getProperty("baseUrl", "http://127.0.0.1:8989"))
    .acceptHeader("application/json")
    .header("X-Player-Id", "1")

  val scn = scenario("mmorpg-smoke")
    .exec(http("health").get("/actuator/health").check(status.in(200, 503)))
    .pause(100.millis)
    .exec(http("zones").get("/internal/scene/world/zones?worldId=1").check(status.in(200, 401, 404)))
    .pause(100.millis)
    .exec(http("pass").get("/internal/shop/pass?seasonId=1").check(status.in(200, 401, 404)))

  setUp(
    scn.inject(rampUsers(Integer.getInteger("users", 20)).during(10.seconds))
  ).protocols(httpProtocol)
}
