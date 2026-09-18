import base64, hashlib
from cryptography.hazmat.primitives.serialization import (
    load_der_private_key, load_pem_public_key, Encoding, PublicFormat)

def rd(p):
    return open(p, 'rb').read()

def pem_single_line(der):
    b64 = base64.b64encode(der).decode()
    return ("-----BEGIN PUBLIC KEY-----\n%s\n-----END PUBLIC KEY-----" % b64).encode()

priv = load_der_private_key(rd("src/main/resources/keys/passport_1024.der"), None)
der = priv.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo)
derived = pem_single_line(der)

scratch = rd("tools/_key1024_pub.pem")
print("derived len=%d   scratch len=%d" % (len(derived), len(scratch)))
print("derived == scratch file ?", derived == scratch)

OLD = (b"-----BEGIN PUBLIC KEY-----\n"
       b"MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDDvekdPMHN3AYhm/vktJT+YJr7cI5Dc"
       b"sNKqdsx5DZX0gDuWFuIjzdwButrIYPNmRJ1G8ybDIF7oDW2eEpm5sMbL9zs9ExXCdvqrn"
       b"51qELbqj0XxtMTIpaCHFSI50PfPpTFV9Xt/hmyVwokoOXFlAEgCn+QCgGs52bFoYMtyi+x"
       b"EQIDAQAB"
       b"\n-----END PUBLIC KEY-----")
print("derived == miHoYo OLD ?", derived == OLD, "(must be False)")
print("len(derived) == len(OLD) ?", len(derived) == len(OLD), "(must be True)")

# a wrong key (e.g. the 2048-bit SigningKey) would NOT be 268 bytes -> guard works
spriv = load_der_private_key(rd("src/main/resources/keys/SigningKey.der"), None)
sder = spriv.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo)
print("SigningKey derived len=%d  (guard would reject: != 268)" % len(pem_single_line(sder)))

# round trip: the derived literal must parse and match the private key
ok = load_pem_public_key(derived).public_numbers().n == priv.public_numbers().n
print("derived parses and pairs with passport_1024.der ?", ok)

# and it must actually be findable in a pristine DLL's slot
bak = r"C:\Users\InvokerBot\AppData\Local\genshin\versions\7.0.0\YuanShen_Data\Plugins\AccountPlatNative.dll.lunagc-bak"
b = open(bak, 'rb').read()
print("\npristine backup: OLD x%d  derived x%d  offset(OLD)=0x%X"
      % (b.count(OLD), b.count(derived), b.find(OLD)))
