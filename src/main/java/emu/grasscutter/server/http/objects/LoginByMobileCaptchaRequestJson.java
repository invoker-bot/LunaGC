package emu.grasscutter.server.http.objects;

/**
 * 国服 SDK 手机号验证码登录 (loginByMobileCaptcha) 的请求体。
 * <p>
 * 客户端真实字段名在公网上不可见，且各 SDK 版本不一致，因此把所有可能出现的
 * 手机号/验证码字段都声明出来，登录时取第一个非空值，避免反序列化失败。
 * account/mobile 会被 RSA 加密，captcha/captcha_id 为明文。
 */
public class LoginByMobileCaptchaRequestJson {
    public String account;
    public String mobile;
    public String phone;
    public String area_code;
    public String captcha;
    public String code;
    public String verify_code;
    public String captcha_id;
    public String captchaId;
}
