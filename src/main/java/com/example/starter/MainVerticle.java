// mvn package && mvn exec:java
// mvn clean package && java -jar target/starter-1.0.0-SNAPSHOT-fat.jar

package com.example.starter;

import java.util.Iterator;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.buffer.impl.VertxByteBufAllocator;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.jackson.DatabindCodec;

public class MainVerticle extends AbstractVerticle {

	static final ObjectWriter UW;
	static final int FEATURES;

	static {
//      needs <artifactId>jackson-datatype-jdk8</artifactId> to serialize streams directly
//		DatabindCodec.mapper().registerModule(new Jdk8Module());
//		UW = DatabindCodec.mapper().writerFor(new TypeReference<Stream<User>>() {});
		UW = DatabindCodec.mapper().writerFor(new TypeReference<Iterator<User>>() {
		});
		FEATURES = UW.getFactory().getGeneratorFeatures();
	}

	public static void main(String[] args) {
		Vertx vertx = Vertx.vertx();
		DeploymentOptions options = new DeploymentOptions().setInstances(Runtime.getRuntime().availableProcessors());
		vertx.deployVerticle(MainVerticle.class.getName(), options);
	}

	final BufferImpl buffer = (BufferImpl) Buffer.buffer(VertxByteBufAllocator.DEFAULT.directBuffer(128 * 1024));

	void handle(HttpServerRequest req) {
		var users = IntStream.rangeClosed(1, 1001).mapToObj(i -> new User(
				i,
				"FirstName" + i,
				"LastName" + i,
				25,
				"Java Vert.x"));

		try {
			UW.writeValue(ByteBufJsonGenerator.allocate(buffer.byteBuf().clear(), FEATURES), users.iterator());
			req.response().putHeader("content-type", "application/json; charset=utf-8").end(buffer);
		} catch (Exception e) {
			req.response().setStatusCode(500).end();
		}
	}

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		vertx.createHttpServer()
				.requestHandler(this::handle)//
				.listen(8888, http -> {
					if (http.succeeded()) {
						startPromise.complete();
						System.out.println("HTTP server started on port 8888");
					} else {
						startPromise.fail(http.cause());
					}
				});
	}
}
