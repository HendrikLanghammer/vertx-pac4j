/*
  Copyright 2015 - 2015 pac4j organization

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.pac4j.vertx;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.ext.web.Router;
import org.apache.commons.codec.binary.Base64;
import org.junit.Test;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.http.client.direct.DirectBasicAuthClient;
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator;
import org.pac4j.vertx.auth.Pac4jAuthProvider;
import org.pac4j.vertx.handler.impl.Pac4jAuthHandlerOptions;
import org.pac4j.vertx.handler.impl.RequiresAuthenticationHandler;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author Jeremy Prime
 * @since 2.0.0
 */
public class StatelessPac4jAuthHandlerIntegrationTest extends Pac4jAuthHandlerIntegrationTestBase {

    private static final String AUTH_HEADER_NAME = "Authorization";
    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final String TEST_BASIC_AUTH_HEADER = BASIC_AUTH_PREFIX + Base64.encodeBase64String("testUser:testUser".getBytes());
    private static final String TEST_FAILING_BASIC_AUTH_HEADER = BASIC_AUTH_PREFIX + Base64.encodeBase64String("testUser:testUser2".getBytes());
    public static final String PROTECTED_RESOURCE_URL = "/private/success.html";
    public static final String BASIC_AUTH_CLIENT = "BasicAuthClient";

    @Test
    public void testSuccessfulLogin() throws Exception {

        testLoginAttempt(TEST_BASIC_AUTH_HEADER, 200, protectedResourceContentValidator());

    }

    @Test
    public void testFailedLogin() throws Exception {

        testLoginAttempt(TEST_FAILING_BASIC_AUTH_HEADER, 401, unauthorizedContentValidator());

    }

    private void testLoginAttempt(final String credentialsHeader, final int expectedHttpStatus, final Consumer<String> bodyValidator) throws Exception {
        startWebServer();
        HttpClient client = vertx.createHttpClient();
        // Attempt to get a private url
        final HttpClientRequest request = client.get(8080, "localhost", PROTECTED_RESOURCE_URL)
                .putHeader(AUTH_HEADER_NAME, credentialsHeader);
        // This should get the desired result straight away rather than operating through redirects
        request.handler(response -> {
            assertEquals(expectedHttpStatus, response.statusCode());
            response.bodyHandler(body -> {
                final String bodyContent = body.toString();
                bodyValidator.accept(bodyContent);
                testComplete();
            });
        });
        request.end();
        await(1, TimeUnit.SECONDS);
    }

    private Consumer<String> protectedResourceContentValidator() {
        return body -> assertEquals("authenticationSuccess", body);
    }

    private Consumer<String> unauthorizedContentValidator() {
        return body -> assertEquals(UNAUTHORIZED_BODY, body);
    }

    private void startWebServer() throws Exception {

        final Router router = Router.router(vertx);
        // Configure a pac4j stateless handler configured for basic http auth
        final Pac4jAuthProvider authProvider = new Pac4jAuthProvider();
        Pac4jAuthHandlerOptions options = new Pac4jAuthHandlerOptions()
                .withAuthorizerName(REQUIRE_ALL_AUTHORIZER)
                .withClientName(BASIC_AUTH_CLIENT);
        final RequiresAuthenticationHandler handler =  new RequiresAuthenticationHandler(vertx, config(), authProvider, options);
        startWebServer(router, handler);

    }

    private Config config() {
        final Clients clients = new Clients(client());
        return new Config(clients, authorizers(new ArrayList<String>()));
    }

    private Client client() {
        DirectBasicAuthClient client = new DirectBasicAuthClient();
        client.setName("BasicAuthClient");
        client.setAuthenticator(new SimpleTestUsernamePasswordAuthenticator());
        return client;
    }


}
