package com.notessensei.hellogoogle;

import java.io.InputStream;
import java.util.Properties;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;

/**
 *  Sample Vert.x verticle providing two endpoints / and /secret
 *  /secret triggers authentication against Google. If successful it
 *  shows the 
 * 
 * @author Stephan
 */
public class MainVerticle extends AbstractVerticle {

  /**
   * @param args args[0] is the file with ClientID, ClientSecret
   */
  public static void main(final String[] args) {
    String propertyFileName = args.length < 1 ? "/.env" : args[0];
    final VertxOptions options = new VertxOptions();
    options.setBlockedThreadCheckInterval(1000 * 60 * 60);
    final Vertx vertx = Vertx.vertx(options);
    vertx.deployVerticle(new MainVerticle(propertyFileName));
  }

  
  private final String CLIENT_ID;
  private final String CLIENT_SECRET;
  
  /**
   * @param propertyFileName - Where cleintid and client secret come from
   */
  public MainVerticle(final String propertyFileName) {
    Properties prop = new Properties();
    try (final InputStream in = this.getClass().getResourceAsStream(propertyFileName)) { 
      if (in == null) {
        System.err.println("Missing Property resource: "+propertyFileName+"\nNeeded Properties: ClientID, ClientSecret");
      }
      prop.load(in);
    } catch (Exception e) {
      e.printStackTrace();
    }
   this.CLIENT_ID = prop.getProperty("ClientID","someID");
   this.CLIENT_SECRET = prop.getProperty("ClientSecret","someSecret");
  }

  @Override
  public void start(final Promise<Void> startPromise) {
    final Router router = Router.router(this.getVertx());
    router.route().handler(SessionHandler.create(LocalSessionStore.create(this.getVertx())));
    router.route("/").handler(this::handlerHome);
    this.setupGoogleAuth(router, "/secret");

    this.getVertx().createHttpServer().requestHandler(router).listen(8765, http -> {
      if (http.succeeded()) {
        startPromise.complete();
        System.out.println("HTTP server started on port 8765");
      } else {
        startPromise.fail(http.cause());
      }
    });
  }

  private void handlerHome(final RoutingContext ctx) {
    ctx.response().putHeader("Content-Type", "text/plain").end("Hello from Vert.x!");
  }

  private void handlerSecretRevealed(final RoutingContext ctx) {
    System.out.println("About to reveal a secret");
    final User user = ctx.user();
    if (user == null) {
      ctx.response().putHeader("Content-Type", "text/plain").end("Alles Sch...");
      return;
    }
    ctx.response().putHeader("Content-Type", "application/json").end(user.principal().encodePrettily());
  }

  private void setupGoogleAuth(final Router router, final String path) {

    final OAuth2ClientOptions options = new OAuth2ClientOptions()
        .setClientID(this.CLIENT_ID)
        .setClientSecret(this.CLIENT_SECRET)
        .setSite("https://accounts.google.com");

    OpenIDConnectAuth.discover(this.getVertx(), options, ar -> {
      if (ar.succeeded()) {
        final OAuth2Auth authProvider = ar.result();
        final JsonObject extraParams = new JsonObject()
            .put("scope", "email")
            .put("redirect_uri", "http://localhost:8765/auth/callback")
            .put("prompt", "select_account");
        final OAuth2AuthHandler handler = OAuth2AuthHandler
            .create(this.getVertx(), authProvider)
            .extraParams(extraParams)
            .setupCallback(router.route("/auth/callback"));
        router.route(path).handler(handler);
        router.get(path).handler(this::handlerSecretRevealed);
      } else {
        ar.cause().printStackTrace();
      }
    });
  }

}
