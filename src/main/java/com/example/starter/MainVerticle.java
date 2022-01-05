package com.example.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;

import java.util.ArrayList;
import java.util.List;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    vertx.createHttpServer().requestHandler(req -> {
      List<User> users = new ArrayList<>();
      users.add(new User(1, "hello"));
      req.response()
//        .putHeader("content-type", "text/plain")
        .putHeader("content-type", "application/json")
//        .end("Hello from Vert.x!");
        .end((Buffer) new JsonArray(users));
    }).listen(8888, http -> {
      if (http.succeeded()) {
        startPromise.complete();
        System.out.println("HTTP server started on port 8888");
      } else {
        startPromise.fail(http.cause());
      }
    });
  }
}
