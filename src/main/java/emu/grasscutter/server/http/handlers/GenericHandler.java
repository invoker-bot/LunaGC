package emu.grasscutter.server.http.handlers;

import static emu.grasscutter.config.Configuration.ACCOUNT;

import emu.grasscutter.*;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.server.http.objects.*;
import io.javalin.Javalin;
import io.javalin.http.Context;

/** Handles all generic, hard-coded responses. */
public final class GenericHandler implements Router {
    private static void serverStatus(Context ctx) {
        int playerCount = Grasscutter.getGameServer().getPlayers().size();
        int maxPlayer = ACCOUNT.maxPlayer;
        String version = GameConstants.VERSION;

        ctx.result(
                "{\"retcode\":0,\"status\":{\"playerCount\":"
                        + playerCount
                        + ",\"maxPlayer\":"
                        + maxPlayer
                        + ",\"version\":\""
                        + version
                        + "\"}}");
    }

    @Override
    public void applyRoutes(Javalin javalin) {
        // hk4e-sdk-os.hoyoverse.com
        javalin.get(
                "/hk4e_global/mdk/agreement/api/getAgreementInfos",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"marketing_agreements\":[]}}"));
        // hk4e-sdk-os.hoyoverse.com (this could be either GET or POST based on the observation of
        // different clients)
        this.allRoutes(
                javalin,
                "/hk4e_global/combo/granter/api/compareProtocolVersion",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"modified\":true,\"protocol\":{\"id\":0,\"app_id\":4,\"language\":\"en\",\"user_proto\":\"\",\"priv_proto\":\"\",\"major\":7,\"minimum\":0,\"create_time\":\"0\",\"teenager_proto\":\"\",\"third_proto\":\"\"}}}"));

        // api-account-os.hoyoverse.com
        javalin.post(
                "/account/risky/api/check",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"id\":\"none\",\"action\":\"ACTION_NONE\",\"geetest\":null}}"));

        // sdk-os-static.hoyoverse.com
        javalin.get(
                "/combo/box/api/config/sdk/combo",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"vals\":{\"disable_email_bind_skip\":\"false\",\"email_bind_remind_interval\":\"7\",\"email_bind_remind\":\"true\"}}}"));
        // hk4e-sdk-os-static.hoyoverse.com
        javalin.get("/hk4e_global/combo/granter/api/getConfig", AnnouncementsHandler::sdkConfig);
        // hk4e-sdk-os-static.hoyoverse.com
        javalin.get(
                "/hk4e_global/mdk/shield/api/loadConfig",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"id\":6,\"game_key\":\"hk4e_global\",\"client\":\"PC\",\"identity\":\"I_IDENTITY\",\"guest\":false,\"ignore_versions\":\"\",\"scene\":\"S_NORMAL\",\"name\":\"原神海外\",\"disable_regist\":false,\"enable_email_captcha\":false,\"thirdparty\":[\"fb\",\"tw\"],\"disable_mmt\":false,\"server_guest\":false,\"thirdparty_ignore\":{\"tw\":\"\",\"fb\":\"\"},\"enable_ps_bind_account\":false,\"thirdparty_login_configs\":{\"tw\":{\"token_type\":\"TK_GAME_TOKEN\",\"game_token_expires_in\":604800},\"fb\":{\"token_type\":\"TK_GAME_TOKEN\",\"game_token_expires_in\":604800}}}}"));
        // Test api?
        // abtest-api-data-sg.hoyoverse.com
        javalin.post(
                "/data_abtest_api/config/experiment/list",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"success\":true,\"message\":\"\",\"data\":[{\"code\":1000,\"type\":2,\"config_id\":\"14\",\"period_id\":\"6036_99\",\"version\":\"1\",\"configs\":{\"cardType\":\"old\"}}]}"));

        // log-upload-os.mihoyo.com
        this.allRoutes(javalin, "/log/sdk/upload", new HttpJsonResponse("{\"code\":0}"));
        this.allRoutes(javalin, "/sdk/upload", new HttpJsonResponse("{\"code\":0}"));
        javalin.post("/sdk/dataUpload", new HttpJsonResponse("{\"code\":0}"));
        // /perf/config/verify?device_id=xxx&platform=x&name=xxx
        this.allRoutes(javalin, "/perf/config/verify", new HttpJsonResponse("{\"code\":0}"));

        // webstatic-sea.hoyoverse.com
        javalin.get("/admin/mi18n/plat_oversea/*", new WebStaticVersionResponse());

        javalin.get("/admin/mi18n/plat_os/*", ctx -> ctx.result("{}"));

        this.allRoutes(
                javalin,
                "/hk4e_global/account/ma-passport/api/getConfig",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"support_reactivate_account\":false,\"enable_ps_bind_account\":false,\"login_mode\":\"account_login\",\"guest_mode\":\"close\",\"realperson_mode\":\"none\",\"safeguard_type\":\"none\",\"apple_login_enabled\":false,\"facebook_login_enabled\":false,\"google_login_enabled\":false,\"twitter_login_enabled\":false}}"));
        this.allRoutes(
                javalin,
                "/hk4e_cn/account/ma-passport/api/getConfig",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"support_reactivate_account\":false,\"enable_ps_bind_account\":false,\"login_mode\":\"account_login\",\"guest_mode\":\"close\",\"realperson_mode\":\"none\",\"safeguard_type\":\"none\",\"apple_login_enabled\":false,\"facebook_login_enabled\":false,\"google_login_enabled\":false,\"twitter_login_enabled\":false}}"));

        javalin.get(
                "/device-fp/api/getExtList",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"ext_list\":[],\"pkg_list\":[]}}"));
        javalin.get(
                "/combo/box/api/config/sw/precache",
                new HttpJsonResponse("{\"retcode\":0,\"message\":\"OK\",\"data\":{}}"));

        javalin.get("/status/server", GenericHandler::serverStatus);

        // 国服原生 SDK (passport-api.mihoyo.com) 登录前置：风控/验证码。一律放行，不要求验证码。
        this.allRoutes(
                javalin,
                "/account/ma-cn-passport/app/checkRiskVerified",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"is_risky\":false,\"risk_tag\":\"\",\"verify_type\":\"\",\"geetest\":null}}"));
        this.allRoutes(
                javalin,
                "/common/aigis/api/createBySmartCaptchaTicket",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"ticket\":\"lunagc-ticket\",\"greeting\":\"\"}}"));
        this.allRoutes(
                javalin,
                "/common/aigis/api/checkSmartCaptcha",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"verify_result\":true,\"ticket\":\"lunagc-ticket\"}}"));
        // 国服 SDK 先请求 createLoginCaptcha 拿 captcha_id，再带着它请求
        // loginByMobileCaptcha。必须返回一个 captcha_id，否则客户端不会提交登录。
        // 私服不发真实短信，任何验证码都接受，因此返回一个固定的占位 id。
        this.allRoutes(
                javalin,
                "/account/ma-cn-verifier/verifier/createLoginCaptcha",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"captcha_id\":\"lunagc-captcha\",\"need_captcha\":false}}"));

        // 国服 SDK 配置接口（客户端请求的是 hk4e_cn 变体，原仓库只注册了 hk4e_global）
        this.allRoutes(
                javalin,
                "/hk4e_cn/combo/granter/api/compareProtocolVersion",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"modified\":true,\"protocol\":{\"id\":0,\"app_id\":4,\"language\":\"zh-Hans\",\"user_proto\":\"\",\"priv_proto\":\"\",\"major\":7,\"minimum\":0,\"create_time\":\"0\",\"teenager_proto\":\"\",\"third_proto\":\"\"}}}"));
        javalin.get("/hk4e_cn/combo/granter/api/getConfig", AnnouncementsHandler::sdkConfig);
        javalin.get(
                "/hk4e_cn/mdk/shield/api/loadConfig",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"id\":6,\"game_key\":\"hk4e_cn\",\"client\":\"PC\",\"identity\":\"I_IDENTITY\",\"guest\":false,\"ignore_versions\":\"\",\"scene\":\"S_NORMAL\",\"name\":\"原神\",\"disable_regist\":false,\"enable_email_captcha\":false,\"thirdparty\":[],\"disable_mmt\":false,\"server_guest\":false,\"thirdparty_ignore\":{},\"enable_ps_bind_account\":false,\"thirdparty_login_configs\":{}}}"));
    }
}
