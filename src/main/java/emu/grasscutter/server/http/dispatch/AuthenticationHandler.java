package emu.grasscutter.server.http.dispatch;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.auth.AuthenticationSystem;
import emu.grasscutter.auth.MaPassportAuthenticator;
import emu.grasscutter.auth.OAuthAuthenticator.ClientType;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.server.http.objects.*;
import emu.grasscutter.server.http.objects.ComboTokenReqJson.LoginTokenData;
import emu.grasscutter.utils.*;
import io.javalin.Javalin;
import io.javalin.http.Context;

/** Handles requests related to authentication. */
public final class AuthenticationHandler implements Router {
    /**
     * @route /hk4e_global/mdk/shield/api/login
     */
    private static void clientLogin(Context ctx) {
        // Parse body data.
        String rawBodyData = ctx.body();
        var bodyData = JsonUtils.decode(rawBodyData, LoginAccountRequestJson.class);

        // Validate body data.
        if (bodyData == null) return;

        // Pass data to authentication handler.
        var responseData =
                Grasscutter.getAuthenticationSystem()
                        .getPasswordAuthenticator()
                        .authenticate(AuthenticationSystem.fromPasswordRequest(ctx, bodyData));
        // Send response.
        ctx.json(responseData);

        // Log to console.
        Grasscutter.getLogger()
                .debug(translate("messages.dispatch.account.login_attempt", Utils.address(ctx)));
    }

    /**
     * @route /hk4e_global/mdk/shield/api/verify
     */
    private static void tokenLogin(Context ctx) {
        // Parse body data.
        String rawBodyData = ctx.body();
        var bodyData = JsonUtils.decode(rawBodyData, LoginTokenRequestJson.class);

        // Validate body data.
        if (bodyData == null) return;

        // Pass data to authentication handler.
        var responseData =
                Grasscutter.getAuthenticationSystem()
                        .getTokenAuthenticator()
                        .authenticate(AuthenticationSystem.fromTokenRequest(ctx, bodyData));
        // Send response.
        ctx.json(responseData);

        // Log to console.
        Grasscutter.getLogger()
                .debug(translate("messages.dispatch.account.login_attempt", Utils.address(ctx)));
    }

    /**
     * @route /hk4e_global/combo/granter/login/v2/login
     */
    private static void sessionKeyLogin(Context ctx) {
        // Parse body data.
        String rawBodyData = ctx.body();
        var bodyData = JsonUtils.decode(rawBodyData, ComboTokenReqJson.class);

        // Validate body data.
        // LunaGC: guard against an empty-string `data` field. The real client sends a
        // populated JSON string here, but a replay/stub body may pass `data: ""`, which
        // is non-null yet not valid LoginTokenData -- decode then yields null and
        // fromComboTokenRequest() NPEs, surfacing as an HTTP 500 mid-login.
        if (bodyData == null || bodyData.data == null) return;
        if (bodyData.data.isEmpty()) return;

        // Decode additional body data.
        var tokenData = JsonUtils.decode(bodyData.data, LoginTokenData.class);
        if (tokenData == null) return; // LunaGC: malformed inner payload -> bail instead of NPE

        // Pass data to authentication handler.
        var responseData =
                Grasscutter.getAuthenticationSystem()
                        .getSessionKeyAuthenticator()
                        .authenticate(AuthenticationSystem.fromComboTokenRequest(ctx, bodyData, tokenData));
        // Send response.
        ctx.json(responseData);

        // Log to console.
        Grasscutter.getLogger()
                .debug(translate("messages.dispatch.account.login_attempt", Utils.address(ctx)));
    }

    /**
     * @route /hk4e_global/account/ma-passport/api/appLoginByPassword
     * @route /hk4e_cn/account/ma-passport/api/appLoginByPassword
     */
    private static void maPassportLogin(Context ctx) {
        Grasscutter.getLogger().info("Ma-passport login request from: " + Utils.address(ctx));
        
        try {
            String rawBodyData = ctx.body();
            Grasscutter.getLogger().debug("Ma-passport request body: " + rawBodyData);
            
            var request = JsonUtils.decode(rawBodyData, LoginByPasswordRequestJson.class);
            if (request == null) {
                Grasscutter.getLogger().warn("Failed to parse Ma-Passport login request");
                ctx.status(400).result("{\"retcode\":-1,\"message\":\"Invalid Request\",\"data\":null}");
                return;
            }
            
            var response = MaPassportAuthenticator.appLoginByPassword(request);
            
            ctx.json(response);
            
        } catch (Exception e) {
            Grasscutter.getLogger().error("Error in Ma-Passport login", e);
            e.printStackTrace();
            ctx.status(500).result("{\"retcode\":-1,\"message\":\"Internal server error\",\"data\":null}");
        }
    }

    /**
     * @route /hk4e_global/account/ma-passport/token/verifySToken
     * @route /hk4e_cn/account/ma-passport/token/verifySToken
     */
    private static void maPassportVerify(Context ctx) {
        Grasscutter.getLogger().info("Ma-passport token verify request from: " + Utils.address(ctx));
        
        try {
            String rawBodyData = ctx.body();
            Grasscutter.getLogger().debug("Ma-passport verify body: " + rawBodyData);
            
            var request = JsonUtils.decode(rawBodyData, VerifySTokenRequestJson.class);
            if (request == null) {
                Grasscutter.getLogger().warn("Failed to parse Ma-Passport verify request");
                ctx.status(400).result("{\"retcode\":-1,\"message\":\"Invalid Request\",\"data\":null}");
                return;
            }

            var response = MaPassportAuthenticator.verifySToken(request);

            ctx.json(response);

        } catch (Exception e) {
            Grasscutter.getLogger().error("Error in Ma-Passport verify", e);
            e.printStackTrace();
            ctx.status(500).result("{\"retcode\":-1,\"message\":\"Internal server error\",\"data\":null}");
        }
    }

    /**
     * Handles the CN SDK's session-token exchange call.
     *
     * <p>The official endpoint swaps a login token (token_type 1) for a game token
     * (dst_token_type 4). Our authentication is token-agnostic - the combo login accepts
     * whatever token the client presents - so the source token is echoed back under the
     * requested token type. Returning an empty {@code data} here (the previous behaviour,
     * before this route was registered) leaves the SDK without a usable game token.
     *
     * @route /account/ma-cn-session/app/exchange
     */
    private static void maPassportExchange(Context ctx) {
        try {
            var body = JsonUtils.decode(ctx.body(), com.google.gson.JsonObject.class);
            if (body == null || !body.has("src_token")) {
                ctx.status(400).result("{\"retcode\":-1,\"message\":\"Invalid Request\",\"data\":null}");
                return;
            }

            var srcToken = body.getAsJsonObject("src_token");
            String token = srcToken.has("token") ? srcToken.get("token").getAsString() : "";
            int dstTokenType = body.has("dst_token_type") ? body.get("dst_token_type").getAsInt() : 4;

            var tokenJson = new com.google.gson.JsonObject();
            tokenJson.addProperty("token_type", dstTokenType);
            tokenJson.addProperty("token", token);

            var data = new com.google.gson.JsonObject();
            data.add("token", tokenJson);

            var response = new com.google.gson.JsonObject();
            response.addProperty("retcode", 0);
            response.addProperty("message", "");
            response.add("data", data);

            ctx.json(response);
        } catch (Exception e) {
            Grasscutter.getLogger().error("Error in Ma-Passport exchange", e);
            ctx.status(500).result("{\"retcode\":-1,\"message\":\"Internal server error\",\"data\":null}");
        }
    }

    @Override
    public void applyRoutes(Javalin javalin) {
        // OS
        // Username & Password login (from client).
        javalin.post("/hk4e_global/mdk/shield/api/login", AuthenticationHandler::clientLogin);
        // Cached token login (from registry).
        javalin.post("/hk4e_global/mdk/shield/api/verify", AuthenticationHandler::tokenLogin);
        // Combo token login (from session key).
        javalin.post(
                "/hk4e_global/combo/granter/login/v2/login", AuthenticationHandler::sessionKeyLogin);
        // ma-passport
        javalin.post("/hk4e_global/account/ma-passport/api/appLoginByPassword", AuthenticationHandler::maPassportLogin);
        javalin.post("/hk4e_global/account/ma-passport/token/verifySToken", AuthenticationHandler::maPassportVerify);

        // CN
        // Username & Password login (from client).
        javalin.post("/hk4e_cn/mdk/shield/api/login", AuthenticationHandler::clientLogin);
        // Cached token login (from registry).
        javalin.post("/hk4e_cn/mdk/shield/api/verify", AuthenticationHandler::tokenLogin);
        // Combo token login (from session key).
        javalin.post("/hk4e_cn/combo/granter/login/v2/login", AuthenticationHandler::sessionKeyLogin);
        // ma-passport
        javalin.post("/hk4e_cn/account/ma-passport/api/appLoginByPassword", AuthenticationHandler::maPassportLogin);
        javalin.post("/hk4e_cn/account/ma-passport/token/verifySToken", AuthenticationHandler::maPassportVerify);
        // ma-cn-passport (passport-api.mihoyo.com/account/ma-cn-passport/...) - 国服 SDK 实际请求路径
        javalin.post("/account/ma-cn-passport/app/loginByPassword", AuthenticationHandler::maPassportLogin);
        javalin.post("/account/ma-cn-passport/token/verifySToken", AuthenticationHandler::maPassportVerify);
        // ma-cn-session (session-api.mihoyo.com/account/ma-cn-session/...) - 国服 SDK 实际请求路径。
        // 之前未注册，落到通配 handler 返回空 data，客户端拿不到 user_info/token。
        javalin.post("/account/ma-cn-session/app/verify", AuthenticationHandler::maPassportVerify);
        javalin.post("/account/ma-cn-session/app/exchange", AuthenticationHandler::maPassportExchange);

        // External login (from other clients).
        javalin.get(
                "/authentication/type",
                ctx -> ctx.result(Grasscutter.getAuthenticationSystem().getClass().getSimpleName()));
        javalin.post(
                "/authentication/login",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getExternalAuthenticator()
                                .handleLogin(AuthenticationSystem.fromExternalRequest(ctx)));
        javalin.post(
                "/authentication/register",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getExternalAuthenticator()
                                .handleAccountCreation(AuthenticationSystem.fromExternalRequest(ctx)));
        javalin.post(
                "/authentication/change_password",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getExternalAuthenticator()
                                .handlePasswordReset(AuthenticationSystem.fromExternalRequest(ctx)));

        // External login (from OAuth2).
        javalin.post(
                "/hk4e_global/mdk/shield/api/loginByThirdparty",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getOAuthAuthenticator()
                                .handleLogin(AuthenticationSystem.fromExternalRequest(ctx)));
        javalin.get(
                "/authentication/openid/redirect",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getOAuthAuthenticator()
                                .handleTokenProcess(AuthenticationSystem.fromExternalRequest(ctx)));
        javalin.get(
                "/sdkFacebookLogin.html",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getOAuthAuthenticator()
                                .handleRedirection(
                                        AuthenticationSystem.fromExternalRequest(ctx), ClientType.DESKTOP));
        javalin.get(
                "/Api/twitter_login",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getOAuthAuthenticator()
                                .handleRedirection(
                                        AuthenticationSystem.fromExternalRequest(ctx), ClientType.DESKTOP));
        javalin.get(
                "/sdkTwitterLogin.html",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getOAuthAuthenticator()
                                .handleRedirection(
                                        AuthenticationSystem.fromExternalRequest(ctx), ClientType.MOBILE));
    }
}