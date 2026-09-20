import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 从「应用私钥」反推「应用公钥」，用来排掉支付接入里最常见的一类事故：**两对密钥混用**。
 *
 * <p>为什么需要它：支付宝只让你上传**应用公钥**，私钥在你自己手里。如果你手里有两对密钥
 * （例如控制台密钥工具生成的一对、别人给你的另一对），很容易出现"私钥是 A 的、控制台上传的是 B 的公钥"，
 * 结果就是签名怎么都不通过，而报错信息完全看不出原因。本工具直接从私钥算出**它真正对应的公钥**，
 * 拿去和沙箱控制台上的应用公钥逐字符比一下就知道有没有混。
 *
 * <p>同时顺手做两件常见格式的事：
 * <ul>
 *   <li>识别 PKCS#8（Java 与支付宝 SDK 需要）与 PKCS#1（{@code BEGIN RSA PRIVATE KEY}，Java 用不了），
 *       若是 PKCS#1 就**另存一份 PKCS#8** 到同目录；</li>
 *   <li>报告密钥位数（必须是 2048）与一次签名/验签自检。</li>
 * </ul>
 *
 * <p>用法（JDK 11+ 单文件运行）：
 * <pre>
 *   java KeyDerive.java &lt;应用私钥文件&gt; [参考应用公钥文件]
 * </pre>
 * 第二个参数可选：给了就做**是否同一对**的比对（比如拿它和我此前生成的 app_public_key.pem 比）。
 *
 * <p><b>私钥内容不会被打印</b>，只打印由它推导出来的公钥（公钥本来就该公开）。
 */
public class KeyDerive {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("用法: java KeyDerive.java <应用私钥文件> [参考应用公钥文件]");
            return;
        }
        Path keyFile = Paths.get(args[0]).toAbsolutePath();
        if (!Files.exists(keyFile)) {
            System.out.println("找不到文件: " + keyFile);
            return;
        }

        String raw = new String(Files.readAllBytes(keyFile), "UTF-8");
        boolean pkcs1Header = raw.contains("BEGIN RSA PRIVATE KEY");
        String body = stripPem(raw);
        byte[] der = Base64.getDecoder().decode(body);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey;
        String format;
        byte[] pkcs8Der;
        if (pkcs1Header) {
            pkcs8Der = wrapPkcs1AsPkcs8(der);
            privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8Der));
            format = "PKCS#1（BEGIN RSA PRIVATE KEY）—— Java 不能直接用";
        } else {
            try {
                pkcs8Der = der;
                privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
                format = "PKCS#8（BEGIN PRIVATE KEY）—— Java 可直接用";
            } catch (Exception tryPkcs1) {
                pkcs8Der = wrapPkcs1AsPkcs8(der);
                privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8Der));
                format = "无 PEM 头尾的一行 base64，实际是 PKCS#1";
            }
        }

        System.out.println("私钥文件: " + keyFile);
        System.out.println("私钥格式: " + format);
        System.out.println("密钥位数: " + ((RSAPrivateCrtKey) privateKey).getModulus().bitLength() + "（支付宝要求 2048）");
        System.out.println();

        RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privateKey;
        byte[] publicDer = keyFactory
                .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()))
                .getEncoded();
        String publicPem = pem("PUBLIC KEY", publicDer);
        String publicOneLine = Base64.getEncoder().encodeToString(publicDer);

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update("miaowang-alipay-pairing-check".getBytes("UTF-8"));
        byte[] sig = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyFactory.generatePublic(new X509EncodedKeySpec(publicDer)));
        verifier.update("miaowang-alipay-pairing-check".getBytes("UTF-8"));
        System.out.println("签名/验签自检: " + (verifier.verify(sig) ? "通过" : "失败"));

        if (pkcs1Header || format.startsWith("无 PEM")) {
            Path converted = keyFile.resolveSibling("app_private_key_pkcs8.pem");
            Files.write(converted, pem("PRIVATE KEY", pkcs8Der).getBytes("UTF-8"));
            System.out.println("已另存 PKCS#8 版本（填 Nacos 用这个更稳）: " + converted);
        }

        if (args.length >= 2) {
            Path ref = Paths.get(args[1]);
            if (!Files.exists(ref)) {
                System.out.println("参考公钥文件不存在: " + ref);
            } else {
                byte[] refDer = Base64.getDecoder()
                        .decode(stripPem(new String(Files.readAllBytes(ref), "UTF-8")));
                boolean same = java.util.Arrays.equals(refDer, publicDer);
                System.out.println("与参考公钥是否同一对: " + (same ? "一致（没混）" : "不一致！这两样不是一对，混用必然签名失败"));
            }
        }

        System.out.println();
        System.out.println("=== 这把私钥对应的应用公钥（PEM）===");
        System.out.print(publicPem);
        System.out.println("=== 同一把公钥（一行 base64，控制台一般要这个）===");
        System.out.println(publicOneLine);
        System.out.println();
        System.out.println("请确认：沙箱控制台「应用公钥」框里的内容 = 上面这段（逐字符一致）。");
        System.out.println("不一致就去控制台把上面这段重新粘一次并保存，然后重新复制控制台给出的「支付宝公钥」。");
    }

    private static String stripPem(String text) {
        return text.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "")
                .replace("\"", "")
                .trim();
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    /** 把 PKCS#1 的 RSAPrivateKey 包成 PKCS#8 的 PrivateKeyInfo（Java 只认后者） */
    private static byte[] wrapPkcs1AsPkcs8(byte[] pkcs1) {
        byte[] algorithmId = new byte[] {
                0x30, 0x0d,
                0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        byte[] version = new byte[] { 0x02, 0x01, 0x00 };
        byte[] octet = concat(tag(0x04, pkcs1), new byte[0]);
        byte[] inner = concat(version, concat(algorithmId, octet));
        return tag(0x30, inner);
    }

    private static byte[] tag(int tag, byte[] content) {
        return concat(new byte[] { (byte) tag }, concat(length(content.length), content));
    }

    private static byte[] length(int len) {
        if (len < 0x80) {
            return new byte[] { (byte) len };
        }
        if (len <= 0xff) {
            return new byte[] { (byte) 0x81, (byte) len };
        }
        return new byte[] { (byte) 0x82, (byte) (len >> 8), (byte) len };
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}