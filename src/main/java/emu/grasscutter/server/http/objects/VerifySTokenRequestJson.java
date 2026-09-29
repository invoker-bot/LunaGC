package emu.grasscutter.server.http.objects;

/**
 * Body of the stoken-verify requests ({@code /account/ma-cn-passport/token/verifySToken} and
 * {@code /account/ma-cn-session/app/verify}).
 *
 * <p>The CN client sends two different shapes for the same logical field:
 *
 * <ul>
 *   <li>flat: {@code {"mid": "10009", "stoken": "v2_abc…"}}
 *   <li>nested (the form the ma-cn-session SDK actually uses on session resume): {@code
 *       {"mid": "10009", "token": {"token_type": 1, "token": "v2_abc…"}, "refresh": true}}
 * </ul>
 *
 * Deserialising the nested form into a bare {@code stoken} field leaves it null, which used to be
 * written straight into the account record and wiped the stored session key. Use {@link
 * #getToken()} so both shapes resolve to the real token.
 */
public class VerifySTokenRequestJson {
    public String mid;
    public String stoken;
    public TokenData token;

    /**
     * Resolves the token from whichever shape the client sent: the flat {@code stoken} field, else
     * the nested {@code token.token}. Returns null only if the client supplied neither.
     */
    public String getToken() {
        if (stoken != null && !stoken.isBlank()) return stoken;
        if (token != null && token.token != null && !token.token.isBlank()) return token.token;
        return null;
    }

    public static class TokenData {
        public int token_type;
        public String token;
    }
}
